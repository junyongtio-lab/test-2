cmake_minimum_required(VERSION 3.22.1)
project(palmac LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set_property(GLOBAL PROPERTY JOB_POOLS palm_compile=4 palm_link=1)
set(CMAKE_JOB_POOL_COMPILE palm_compile)
set(CMAKE_JOB_POOL_LINK palm_link)
set(IR_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/IRremoteESP8266/src")
file(GLOB IR_PROTOCOL_SOURCES CONFIGURE_DEPENDS "${IR_SOURCE}/ir_*.cpp")
add_library(palmac SHARED ac_engine.cpp ac_jni.cpp
    "${IR_SOURCE}/IRac.cpp" "${IR_SOURCE}/IRsend.cpp"
    "${IR_SOURCE}/IRrecv.cpp" "${IR_SOURCE}/IRtimer.cpp"
    "${IR_SOURCE}/IRtext.cpp" "${IR_SOURCE}/IRutils.cpp"
    ${IR_PROTOCOL_SOURCES})
target_include_directories(palmac PRIVATE "${IR_SOURCE}" "${CMAKE_CURRENT_SOURCE_DIR}/capture")
target_compile_definitions(palmac PRIVATE UNIT_TEST _IR_LOCALE_=en-AU)
target_compile_options(palmac PRIVATE -fexceptions -frtti -ffunction-sections -fdata-sections)
target_link_options(palmac PRIVATE -Wl,--gc-sections)
