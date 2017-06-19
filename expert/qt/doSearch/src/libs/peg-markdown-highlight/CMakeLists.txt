#
# PEG Markdown Highlight Adapter Static Libary Project for CuteMarkEd
#
# Github : https://github.com/cloose/CuteMarkEd
#
find_package(Qt5 REQUIRED COMPONENTS Core Widgets)

SET(pmh_sources
        pmhmarkdownparser.cpp
        styleparser.cpp
        pmh_parser.cpp
        pmh_styleparser.cpp)

###################################################################################################
## DEPENDENCIES
###################################################################################################

#
# peg-markdown-highlight
#
ADD_LIBRARY(pmh ${pmh_sources})

target_link_libraries(pmh Qt5::Core Qt5::Widgets)