cmake_minimum_required(VERSION 3.22.1)
project(laermbatz_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(laermbatz_audio SHARED audio_engine.cpp)
target_compile_features(laermbatz_audio PRIVATE cxx_std_17)
target_compile_options(laermbatz_audio PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(laermbatz_audio PRIVATE oboe::oboe android log)
