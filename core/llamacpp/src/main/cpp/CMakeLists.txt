cmake_minimum_required(VERSION 3.22)
project(guadian_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp, pinned to a release and verified by hash. Fetched at configure time so
# the repository does not carry its sources; the MIT license comes with them.
include(FetchContent)
FetchContent_Declare(llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/b11216.tar.gz
    URL_HASH SHA256=2187f9643de609a3f269ee1ae3ef4717de290546b0a4f7fe608dab51f75c8c21)

# One static library inside this module's .so; CPU only; no tools, server, or common utils.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
# Dot-product kernels: ~4x faster 4-bit inference than the generic ARMv8.0 build
# (21 s -> 5 s per verdict on an arm64 emulator). CpuFeatures.kt keeps the library
# unloaded on cores without them.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)
FetchContent_MakeAvailable(llama)

add_library(guadian_llama SHARED llama_jni.cpp)
target_link_libraries(guadian_llama PRIVATE llama android log)
