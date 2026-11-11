cmake_minimum_required(VERSION 3.22.1)

project(playintegrityfix)

find_package(cxx REQUIRED CONFIG)

link_libraries(cxx::cxx)

enable_language(C CXX ASM)

include(FetchContent)

set(MODULE_SRC main.cpp local_cxa_atexit_finalize_impl/atexit.cpp)

if(CMAKE_ANDROID_ARCH_ABI MATCHES "^x86")
    # shadowhook (inline hook) only supports arm and arm64; use ByteHook (PLT hook) for x86 ABIs
    FetchContent_Declare(
      bytehook
      GIT_REPOSITORY https://github.com/bytedance/bhook.git
      GIT_TAG        v1.1.2
    )
    FetchContent_GetProperties(bytehook)
    if(NOT bytehook_POPULATED)
      FetchContent_Populate(bytehook)
    endif()

    file(GLOB BYTEHOOK_SRC ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp/*.c)
    # skip the unused Java bridge
    list(REMOVE_ITEM BYTEHOOK_SRC ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp/bh_jni.c)

    include_directories(
        ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp
        ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp/include
        ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp/third_party/bsd
        ${bytehook_SOURCE_DIR}/bytehook/src/main/cpp/third_party/lss
    )

    list(APPEND MODULE_SRC ${BYTEHOOK_SRC})
else()
    FetchContent_Declare(
      shadowhook
      GIT_REPOSITORY https://github.com/bytedance/android-inline-hook.git
      GIT_TAG        v2.0.1
      PATCH_COMMAND  perl -pi -e "s/sh_linker_init/sh_linker_init_dummy/g" shadowhook/src/main/cpp/sh_linker.c
    )
    FetchContent_GetProperties(shadowhook)
    if(NOT shadowhook_POPULATED)
      FetchContent_Populate(shadowhook)
    endif()

    file(GLOB SHADOWHOOK_SRC
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/*.c
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/common/*.c
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/third_party/xdl/*.c
    )

    if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
        file(GLOB SHADOWHOOK_ARCH_SRC ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm/*.c ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm/*.S)
        include_directories(${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm)
    elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        file(GLOB SHADOWHOOK_ARCH_SRC ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm64/*.c ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm64/*.S)
        include_directories(${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/arch/arm64)
    endif()

    include_directories(
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/common
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/include
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/third_party/bsd
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/third_party/lss
        ${shadowhook_SOURCE_DIR}/shadowhook/src/main/cpp/third_party/xdl
    )

    list(APPEND MODULE_SRC ${SHADOWHOOK_SRC} ${SHADOWHOOK_ARCH_SRC})
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED ${MODULE_SRC})

target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE log)
