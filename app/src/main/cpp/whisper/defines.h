#ifndef DEFINES_H
#define DEFINES_H

// Minimal compatibility header for whisper.cpp logging macros.
// Replaces FUTO keyboard's full defines.h — only provides what whisper.cpp needs.

#include <android/log.h>

#define AKLOGI(...) __android_log_print(ANDROID_LOG_INFO,  "whisper.cpp", __VA_ARGS__)
#define AKLOGE(...) __android_log_print(ANDROID_LOG_ERROR, "whisper.cpp", __VA_ARGS__)

#endif // DEFINES_H
