#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <cstdio>
#include <algorithm>
#include <unordered_map>
#include <android/log.h>
#include <unistd.h>

#include "whisper/whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct whisper_context *g_context = nullptr;

// Detect high-performance CPU cores on big.LITTLE architectures.
// Using all cores (including efficiency cores) can be 5x slower.
static int get_perf_core_count() {
    int cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count <= 0) return 4;

    int max_freq = 0;
    std::vector<int> freqs(cpu_count, 0);

    for (int i = 0; i < cpu_count; i++) {
        char path[128];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        FILE *f = fopen(path, "r");
        if (f) {
            fscanf(f, "%d", &freqs[i]);
            fclose(f);
            if (freqs[i] > max_freq) max_freq = freqs[i];
        }
    }

    if (max_freq == 0) return std::min(cpu_count, 4);

    int perf_count = 0;
    int threshold = (int)(max_freq * 0.8);
    for (int i = 0; i < cpu_count; i++) {
        if (freqs[i] >= threshold) perf_count++;
    }

    return std::max(perf_count, 2);
}

// Create a jstring safely, handling the case where whisper.cpp outputs
// standard UTF-8 that can crash JNI's NewStringUTF (expects Modified UTF-8).
static jstring safe_new_string(JNIEnv *env, const std::string &str) {
    jstring result = env->NewStringUTF(str.c_str());
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        // Fallback: strip non-ASCII bytes to avoid the crash.
        std::string safe;
        safe.reserve(str.size());
        for (char c : str) {
            if ((unsigned char)c < 0x80) safe += c;
        }
        LOGE("UTF-8 encoding issue, stripped to ASCII: %s", safe.c_str());
        result = env->NewStringUTF(safe.c_str());
    }
    return result;
}

// Read a 16-bit PCM WAV file and return normalised float samples.
// Supports both standard 44-byte headers and extended headers with extra
// sub-chunks (e.g. LIST/INFO) before the "data" chunk.
// Validates that the sample rate is 16 kHz as required by Whisper.
static bool read_wav(const char *path, std::vector<float> &out) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        LOGE("Cannot open WAV file: %s", path);
        return false;
    }

    // Read the RIFF header (12 bytes).
    char riff_header[12];
    if (fread(riff_header, 1, 12, f) != 12 ||
        memcmp(riff_header, "RIFF", 4) != 0 ||
        memcmp(riff_header + 8, "WAVE", 4) != 0) {
        LOGE("Not a valid WAV file: %s", path);
        fclose(f);
        return false;
    }

    // Walk sub-chunks until we find "fmt " and "data".
    uint16_t num_channels = 0;
    uint32_t sample_rate  = 0;
    uint16_t bits_per_sample = 0;
    bool     found_fmt  = false;
    bool     found_data = false;
    uint32_t data_size  = 0;

    while (!found_data) {
        char     chunk_id[4];
        uint32_t chunk_size;
        if (fread(chunk_id, 1, 4, f) != 4 || fread(&chunk_size, 4, 1, f) != 1) {
            break;  // EOF
        }

        if (memcmp(chunk_id, "fmt ", 4) == 0) {
            if (chunk_size < 16) { fclose(f); return false; }
            uint16_t audio_format;
            fread(&audio_format, 2, 1, f);
            fread(&num_channels, 2, 1, f);
            fread(&sample_rate,  4, 1, f);
            uint32_t byte_rate; fread(&byte_rate, 4, 1, f);
            uint16_t block_align; fread(&block_align, 2, 1, f);
            fread(&bits_per_sample, 2, 1, f);
            // Skip any remaining fmt bytes (e.g. cbSize for extended format).
            if (chunk_size > 16) fseek(f, chunk_size - 16, SEEK_CUR);
            found_fmt = true;
        } else if (memcmp(chunk_id, "data", 4) == 0) {
            data_size = chunk_size;
            found_data = true;
        } else {
            // Skip unknown sub-chunk.
            fseek(f, chunk_size, SEEK_CUR);
        }
    }

    if (!found_fmt || !found_data || bits_per_sample != 16 || num_channels == 0) {
        LOGE("Unsupported WAV format (need 16-bit PCM): %s", path);
        fclose(f);
        return false;
    }

    if (sample_rate != 16000) {
        LOGE("Wrong sample rate %u Hz (need 16000 Hz): %s", sample_rate, path);
        fclose(f);
        return false;
    }

    size_t num_samples = data_size / (bits_per_sample / 8) / num_channels;
    std::vector<int16_t> pcm(num_samples * num_channels);
    size_t read = fread(pcm.data(), sizeof(int16_t), pcm.size(), f);
    fclose(f);

    // Convert to mono float [-1, 1].
    out.resize(read / num_channels);
    for (size_t i = 0; i < out.size(); i++) {
        if (num_channels == 1) {
            out[i] = (float)pcm[i] / 32768.0f;
        } else {
            // Average channels.
            float sum = 0;
            for (int c = 0; c < num_channels; c++) {
                sum += (float)pcm[i * num_channels + c];
            }
            out[i] = sum / (num_channels * 32768.0f);
        }
    }

    LOGI("WAV loaded: %zu samples, %u Hz, %u ch", out.size(), sample_rate, num_channels);

    // Compute audio statistics for debugging silence / low-energy issues
    float rms = 0.0f, peak = 0.0f;
    int zero_crossings = 0;
    int silent_samples = 0; // below -60 dB ≈ 0.001
    for (size_t i = 0; i < out.size(); i++) {
        float abs_val = fabsf(out[i]);
        rms += out[i] * out[i];
        if (abs_val > peak) peak = abs_val;
        if (abs_val < 0.001f) silent_samples++;
        if (i > 0 && ((out[i] >= 0) != (out[i-1] >= 0))) zero_crossings++;
    }
    rms = sqrtf(rms / (float)out.size());
    float silence_pct = 100.0f * (float)silent_samples / (float)out.size();
    LOGI("Audio stats: RMS=%.4f, peak=%.4f, silence%%=%.1f%%, zero_crossings=%d",
         rms, peak, silence_pct, zero_crossings);

    // Log energy per 1-second chunk to spot silent tails
    size_t chunk_size = 16000; // 1 second at 16kHz
    for (size_t offset = 0; offset < out.size(); offset += chunk_size) {
        size_t end = std::min(offset + chunk_size, out.size());
        float chunk_rms = 0.0f;
        float chunk_peak = 0.0f;
        for (size_t j = offset; j < end; j++) {
            chunk_rms += out[j] * out[j];
            float a = fabsf(out[j]);
            if (a > chunk_peak) chunk_peak = a;
        }
        chunk_rms = sqrtf(chunk_rms / (float)(end - offset));
        LOGD("  Audio chunk [%.1f-%.1fs]: RMS=%.4f, peak=%.4f",
             (float)offset / 16000.0f, (float)end / 16000.0f, chunk_rms, chunk_peak);
    }

    return true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_ethereumphone_andyclaw_whisper_WhisperBridgeNative_initModel(
        JNIEnv *env, jclass clazz, jstring model_path) {
    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
    }

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading whisper model: %s", path);

    g_context = whisper_init_from_file_with_params(path, { .use_gpu = false });
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_context) {
        LOGE("Failed to load whisper model");
        return JNI_FALSE;
    }

    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_org_ethereumphone_andyclaw_whisper_WhisperBridgeNative_transcribeWav(
        JNIEnv *env, jclass clazz, jstring audio_path, jstring language) {
    if (!g_context) {
        return env->NewStringUTF("ERROR: model not initialized");
    }

    const char *path = env->GetStringUTFChars(audio_path, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    // Read WAV and convert to float samples.
    std::vector<float> samples;
    if (!read_wav(path, samples)) {
        env->ReleaseStringUTFChars(audio_path, path);
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("ERROR: failed to read WAV file");
    }
    env->ReleaseStringUTFChars(audio_path, path);

    LOGI("Transcribing %zu samples (~%.1f sec)", samples.size(),
         (float)samples.size() / 16000.0f);

    int n_threads = get_perf_core_count();

    // Match Futo keyboard's whisper params exactly — beam search prevents repetition loops.
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress   = false;
    wparams.print_realtime   = false;
    wparams.print_special    = false;
    wparams.print_timestamps = false;
    wparams.max_tokens       = 256;
    wparams.n_threads        = n_threads;

    wparams.audio_ctx = std::max(160,
            std::min(1500, (int)ceil((double)samples.size() / (double)(320.0)) + 32));

    wparams.temperature_inc = 0.0f;

    // Beam search with beam_size=5 (same as Futo's DecodingMode.BeamSearch5)
    wparams.strategy = WHISPER_SAMPLING_BEAM_SEARCH;
    wparams.beam_search.beam_size = 5;
    wparams.greedy.best_of        = 5;

    wparams.suppress_blank             = false;
    wparams.suppress_non_speech_tokens = true;
    wparams.no_timestamps              = true;
    wparams.language                   = lang;
    wparams.initial_prompt             = "";

    LOGI("=== Whisper params ===");
    LOGI("  strategy=BEAM_SEARCH, beam_size=5");
    LOGI("  audio_ctx=%d, n_threads=%d, max_tokens=%d", wparams.audio_ctx, wparams.n_threads, wparams.max_tokens);
    LOGI("  temperature_inc=%.2f, suppress_blank=%d, suppress_non_speech=%d",
         wparams.temperature_inc, wparams.suppress_blank, wparams.suppress_non_speech_tokens);
    LOGI("  no_timestamps=%d, language=%s", wparams.no_timestamps, wparams.language);
    LOGI("  samples=%zu (%.2f sec), audio_ctx covers %.2f sec",
         samples.size(), (float)samples.size() / 16000.0f,
         (float)wparams.audio_ctx * 320.0f / 16000.0f);

    // Create a fresh state for each transcription so no KV cache / decoder
    // state lingers from previous calls.
    struct whisper_state *w_state = whisper_init_state(g_context);
    if (!w_state) {
        LOGE("Failed to create whisper state");
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("ERROR: failed to create whisper state");
    }

    LOGI("Calling whisper_full_with_state (fresh state)...");
    int res = whisper_full_with_state(g_context, w_state, wparams,
                                      samples.data(), (int)samples.size());

    env->ReleaseStringUTFChars(language, lang);

    if (res != 0) {
        LOGE("whisper_full_with_state failed with code %d", res);
        whisper_free_state(w_state);
        return env->NewStringUTF("ERROR: whisper_full failed");
    }

    // Collect output segments with detailed per-segment & per-token logging.
    std::string output;
    int n_segments = whisper_full_n_segments_from_state(w_state);
    LOGI("whisper_full returned %d segment(s)", n_segments);

    // Track token-level repetition
    std::string prev_segment_text;
    int consecutive_identical_segments = 0;

    for (int i = 0; i < n_segments; i++) {
        const char *seg_text = whisper_full_get_segment_text_from_state(w_state, i);
        int64_t t0 = whisper_full_get_segment_t0_from_state(w_state, i);
        int64_t t1 = whisper_full_get_segment_t1_from_state(w_state, i);
        int n_tokens = whisper_full_n_tokens_from_state(w_state, i);

        LOGI("  Segment %d/%d: t0=%lld, t1=%lld, tokens=%d, text=\"%s\"",
             i, n_segments, (long long)t0, (long long)t1, n_tokens,
             seg_text ? seg_text : "(null)");

        // Log individual tokens for this segment
        for (int t = 0; t < n_tokens && t < 64; t++) {
            whisper_token token_id = whisper_full_get_token_id_from_state(w_state, i, t);
            float token_p = whisper_full_get_token_p_from_state(w_state, i, t);
            const char *token_text = whisper_full_get_token_text_from_state(g_context, w_state, i, t);
            LOGD("    Token[%d]: id=%d, p=%.4f, text=\"%s\"",
                 t, token_id, token_p, token_text ? token_text : "(null)");
        }
        if (n_tokens > 64) {
            LOGW("    ... (%d more tokens not logged)", n_tokens - 64);
        }

        // Track consecutive identical segments
        std::string seg_str = seg_text ? seg_text : "";
        if (seg_str == prev_segment_text && !seg_str.empty()) {
            consecutive_identical_segments++;
        } else {
            if (consecutive_identical_segments > 0) {
                LOGW("  (previous segment repeated %d consecutive time(s))",
                     consecutive_identical_segments);
            }
            consecutive_identical_segments = 0;
        }
        prev_segment_text = seg_str;

        output += seg_text ? seg_text : "";
    }

    if (consecutive_identical_segments > 0) {
        LOGW("  (final segment repeated %d consecutive time(s))",
             consecutive_identical_segments);
    }

    LOGI("Transcription done (%d segments, %zu chars): %s",
         n_segments, output.size(), output.c_str());

    whisper_free_state(w_state);
    return safe_new_string(env, output);
}

JNIEXPORT void JNICALL
Java_org_ethereumphone_andyclaw_whisper_WhisperBridgeNative_release(
        JNIEnv *env, jclass clazz) {
    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
        LOGI("Whisper model released");
    }
}

} // extern "C"
