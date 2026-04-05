cmake_minimum_required(VERSION 3.22.1)
project("sniffle")

# TheengsDecoder + ArduinoJson werden in Task #2 als Submodule hinzugefügt.
# Für jetzt: leere native lib damit der Build durchgeht.

add_library(sniffle SHARED jni_bridge.cpp)
target_link_libraries(sniffle android log)
