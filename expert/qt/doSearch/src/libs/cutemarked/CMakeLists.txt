set(CMAKE_AUTOMOC ON)
set(CMAKE_INCLUDE_CURRENT_DIR ON)

find_package(Qt5 REQUIRED COMPONENTS Core Widgets)


include_directories(../peg-markdown-highlight)
include_directories(../hunspell)

add_library(cutemarked markdownhighlighter.cpp dictionary.cpp)

target_link_libraries(cutemarked
  Qt5::Core
  Qt5::Widgets
)
