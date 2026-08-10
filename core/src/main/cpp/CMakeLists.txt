cmake_minimum_required(VERSION 3.22.1)
project("discord_bridge")

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(discord_bridge SHARED discord_bridge.cpp)

target_link_options(discord_bridge PRIVATE "-Wl,-z,max-page-size=16384")

target_include_directories(discord_bridge PRIVATE
    ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(discord_bridge
    android
    log
    discord_partner_sdk::discord_partner_sdk
)