cmake_minimum_required(VERSION 3.18.1)

project("opencv")

# 指定一个本地头文件路径。
include_directories(${CMAKE_SOURCE_DIR}/)


add_library( # Sets the name of the library.
        opencv

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        opencv.cpp)


find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)


# 设置opencv的动态库
add_library(libopencv_java4
        SHARED
        IMPORTED)
set_target_properties(libopencv_java4
        PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_HOME_DIRECTORY}/jniLibs/${ANDROID_ABI}/libopencv_java4.so
        )


target_link_libraries( # Specifies the target library.
        opencv

        libopencv_java4

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})