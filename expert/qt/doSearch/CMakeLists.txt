#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

cmake_minimum_required(VERSION 3.4)

project(doSearch)



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
if(MSVC)
    set(CMAKE_CXX_FLAGS_DEBUG "/DEBUG /MDd /Zi")
    set(CMAKE_CXX_FLAGS_RELEASE "/MD /O2 /Ob2 /DNDEBUG /DQT_NO_DEBUG_OUTPUT ")
    set(CMAKE_EXE_LINKER_FLAGS  "/subsystem:windows /entry:mainCRTStartup" )
    set(CMAKE_CXX_FLAGS "/GR /DWIN32 /D_WINDOWS /EHsc /wd4355 /wd4251 /wd4250 /wd4996")
    set(CMAKE_C_FLAGS_DEBUG "/MDd /DEBUG /Zi")
    set(CMAKE_C_FLAGS_RELEASE "/MD /O2 /Ob2 /DNDEBUG")
    set(CMAKE_C_FLAGS "/DWIN32 /D_WINDOWS /EHsc")
endif()

if(CEF)
    if (OS_MACOSX)
        set(CEF_ROOT "${PROJECT_SOURCE_DIR}/src/libs/cef-mac")
    elseif(OS_WINDOWS)
        set(CEF_ROOT "${PROJECT_SOURCE_DIR}/src/libs/cef_win32")
    endif()
    set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CEF_ROOT}/cmake")
    set(CMAKE_CONFIGURATION_TYPES Debug Release)
    set_property(GLOBAL PROPERTY OS_FOLDERS ON)
    find_package(CEF REQUIRED)
    add_subdirectory(${CEF_LIBCEF_DLL_WRAPPER_PATH})
    ADD_LOGICAL_TARGET("libcef_lib" "${CEF_LIB_DEBUG}" "${CEF_LIB_RELEASE}")
    #  include(ExternalProject)
    add_subdirectory(src/libs/cef-exec)
#    if(OS_MACOSX)
#        #    ExternalProject_Add(
#        #      cef-mac
#        #      URL https://cefbuilds.s3.amazonaws.com/2272/win/41.0.2272.76/cef_binary_3.2272.2077_windows32.7z
#        #      PREFIX ${CMAKE_SOURCE_DIR}/src/libs/cef-mac
#        #      INSTALL_COMMAND ""
#        #    )
#        add_subdirectory(src/libs/cef-mac)
#    elseif(OS_WINDOWS)
#        add_subdirectory(src/libs/cef_win32)
#    endif()
endif()

set(QXMPP_NO_EXAMPLES true)
set(QXMPP_NO_TESTS true)
set(QXMPP_LIBRARY_TYPE staticlib)
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)
set(PROJECT_ARCH x86_64)

add_definitions(-DQXMPP_STATIC)

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

#IF(MSVC)
#  INSTALL ( FILES ${PROJECT_BINARY_DIR}/Bin/Debug/file.pdb
#    DIRECTORY lib
#    CONFIGURATIONS Debug
#    )
#ENDIF(MSVC)


add_subdirectory(src/main)

