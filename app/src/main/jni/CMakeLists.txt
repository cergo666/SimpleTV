cmake_minimum_required(VERSION 3.22.1)
project(stvcodec LANGUAGES C)

add_library(stvcodec SHARED string_vault.c)
target_compile_options(stvcodec PRIVATE -Wall -Wextra -Werror)
set_target_properties(stvcodec PROPERTIES C_VISIBILITY_PRESET hidden)
