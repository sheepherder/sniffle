cmake_minimum_required(VERSION 3.22.1)
project("sniffle")

# TheengsDecoder static library
add_library(decoder STATIC
    decoder/src/decoder.cpp
    decoder/src/decoder_c.cpp
)

target_include_directories(decoder PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/src
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/src/arduino_json/src
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/include
)

target_compile_features(decoder PRIVATE cxx_std_11)

# JNI bridge shared library
add_library(sniffle SHARED jni_bridge.cpp)

target_include_directories(sniffle PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/src
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/src/arduino_json/src
    ${CMAKE_CURRENT_SOURCE_DIR}/decoder/include
)

target_link_libraries(sniffle PRIVATE decoder android log)
target_compile_features(sniffle PRIVATE cxx_std_11)
