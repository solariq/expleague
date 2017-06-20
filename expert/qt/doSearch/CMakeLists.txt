#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

cmake_minimum_required(VERSION 3.4)

#project(doSearch)

set(VERSION 0.9.0)

if("${CMAKE_SYSTEM_NAME}" STREQUAL "Darwin")
  set(OS_MACOSX 1)
  set(OS_POSIX 1)
elseif("${CMAKE_SYSTEM_NAME}" STREQUAL "Linux")
  set(OS_LINUX 1)
  set(OS_POSIX 1)
elseif("${CMAKE_SYSTEM_NAME}" STREQUAL "Windows")
  set(OS_WINDOWS 1)
endif()

set(CEF true)

set(QXMPP_NO_EXAMPLES true)
set(QXMPP_NO_TESTS true)
set(QXMPP_LIBRARY_TYPE staticlib)
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)
set(PROJECT_ARCH x86_64)

add_subdirectory(src/libs/qxmpp)
add_subdirectory(src/libs/discount)
add_subdirectory(src/libs/peg-markdown-highlight)
add_subdirectory(src/libs/hunspell)
add_subdirectory(src/libs/cutemarked)
add_subdirectory(src/libs/breakpad)
if(OS_WINDOWS)
  add_subdirectory(src/libs/leveldb-win)
else()
  add_subdirectory(src/libs/leveldb)
endif()


if(CEF)
#  include(ExternalProject)
  add_subdirectory(src/libs/cef-exec)
  if(OS_MACOSX)
#    ExternalProject_Add(
#      cef-mac
#      URL https://cefbuilds.s3.amazonaws.com/2272/win/41.0.2272.76/cef_binary_3.2272.2077_windows32.7z
#      PREFIX ${CMAKE_SOURCE_DIR}/src/libs/cef-mac
#      INSTALL_COMMAND ""
#    )
    add_subdirectory(src/libs/cef-mac)
  elseif(OS_WINDOWS)
    add_subdirectory(src/libs/cef-win)
  endif()
endif()

add_subdirectory(src/main)


