cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# --- Optimisation flags matching FUTO keyboard build ---
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -O3 -DHAVE_PTHREAD")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DHAVE_PTHREAD -fexceptions")

# ARM optimizations: dotprod accelerates quantized model inference on
# Snapdragon 7-series Gen 1+ and all 8-series SoCs (ARMv8.2-a).
# NEON is auto-enabled for arm64-v8a.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -march=armv8.2-a+dotprod+fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod+fp16")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -mfpu=neon")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
endif()

# Whisper + GGML source files (from whisper.cpp / FUTO keyboard).
set(WHISPER_SOURCES
    whisper/ggml.c
    whisper/ggml-alloc.c
    whisper/ggml-quants.c
    whisper/ggml-backend.c
    whisper/whisper.cpp
)

add_library(whisper_jni SHARED
    whisper_jni.cpp
    ${WHISPER_SOURCES}
)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper
)

target_link_libraries(whisper_jni
    android
    log
)
