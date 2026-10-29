cmake_minimum_required(VERSION 3.18.1)
project(nxcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(nxcore SHARED treescan.cpp vtscreen.cpp term_jni.cpp filesniff.cpp filesniff_jni.cpp textgrep.cpp textgrep_jni.cpp gitblob.cpp gitblob_jni.cpp treeprint.cpp treeprint_jni.cpp)
target_compile_options(nxcore PRIVATE -O2 -Wall)
target_link_libraries(nxcore log)
target_link_options(nxcore PRIVATE -Wl,-z,max-page-size=16384)
