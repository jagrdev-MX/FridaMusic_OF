cmake_minimum_required(VERSION 3.22.1)

project(frida_discord_presence LANGUAGES CXX)

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(
    frida_discord_presence
    SHARED
    DiscordPresenceBridge.cpp
)

target_compile_features(frida_discord_presence PRIVATE cxx_std_20)

find_library(android_log_lib log)

target_link_libraries(
    frida_discord_presence
    PRIVATE
    discord_partner_sdk::discord_partner_sdk
    ${android_log_lib}
)
