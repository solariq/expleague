#-------------------------------------------------
#
# Project created by QtCreator 2016-03-29T13:58:49
#
#-------------------------------------------------

# # Win
# cd C:\Users\solar\Documents\GitHub\expleague\expert\qt\build-doSearch-Desktop_Qt_5_6_0_MSVC2015_64bit-Release\src\main\release
# mkdir doSearch
# c:\Qt\5.6\msvc2015_64\bin\windeployqt.exe --release --pdb --qmldir ..\..\..\..\doSearch\src\main\resources\qml\ --dir .\doSearch -geoservices .\doSearch.exe
# cp /Volumes/C/Windows//System32/ssleay32.dll ./doSearch/
# cp /Volumes/C/Windows//System32/libeay32.dll ./doSearch/
# copy .\doSearch.exe .\doSearch\
# zip -r ./doSearch-0.1.zip ./doSearch

# # Mac
# mkdir -p ./doSearch.app/Contents/PlugIns/geoservices
# cp ~/Qt/5.6/clang_64/plugins/geoservices/libqtgeoservices_osm.dylib ./doSearch.app/Contents/PlugIns/geoservices/
# ~/Qt/5.6/clang_64/bin/macdeployqt ./doSearch.app -verbose=1 -qmldir=/Users/solar/tree/tbts/expert/qt/doSearch/src/main/resources/qml/
# install_name_tool -rpath /Users/solar/Qt/5.6/clang_64/lib/ @executable_path/../Frameworks ./doSearch.app/Contents/MacOS/doSearch
# mkdir temp
# mv ./doSearch.app/ ./temp/
# hdiutil create -volname doSearch -srcfolder ./temp/ -ov -format UDZO doSearch.dmg
set(DYLD_IMAGE_SUFFIX _debug)
find_package(Qt5 REQUIRED COMPONENTS
  Widgets
  Core
  Network
  Location
  Concurrent
  Positioning
  Gui
  Quick
  QuickControls2
  Xml
  XmlPatterns
  Multimedia
  OpenGL
  Test
)

set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)

include_directories(c++/)
include_directories(objc/)

qt5_add_resources(RESOURCES resources/misc.qrc resources/images/images.qrc resources/qml/qml.qrc)
set(RESOURCES ${RESOURCES} resources/markdown-tile.css)

file(GLOB_RECURSE doSearch_sources "./c++/*.cpp" "./objc/*.mm")

add_definitions(-DQXMPP_STATIC)

if(OS_MACOSX)
  add_definitions("-g")
  set(doSearch_APP "${CMAKE_CURRENT_BINARY_DIR}/doSearch.app")
  set(doSearch_RESOURCES ${CMAKE_CURRENT_SOURCE_DIR}/resources)

  set(ICON_NAME "doSearch.icns")
  set(ICON_PATH "resources/${ICON_NAME}")

  # OpenGL
  include_directories(/System/Library/Frameworks)
  find_library(COCOA_LIBRARY Cocoa)
  find_library(GLUT_LIBRARY GLUT)
  find_library(OpenGL_LIBRARY OpenGL)
  mark_as_advanced(COCOA_LIBRARY
    GLUT_LIBRARY
    OpenGL_LIBRARY)
  set(EXTRA_LIBS ${COCOA_LIBRARY} ${GLUT_LIBRARY} ${OpenGL_LIBRARY})
#  target_sources(doSearch PUBLIC ${EXTRA_LIBS})
#
  add_executable(doSearch MACOSX_BUNDLE ${doSearch_sources} ${ICON_PATH} ${RESOURCES})

  set_target_properties(doSearch PROPERTIES MACOSX_BUNDLE_ICON_FILE ${ICON_NAME})

  # System libs
  find_library(APP_KIT AppKit)
  target_link_libraries(doSearch ${APP_KIT})

  add_custom_command(
    TARGET doSearch
    POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy "${doSearch_RESOURCES}/Info.plist" "${doSearch_APP}/Contents/Info.plist"
    VERBATIM
  )

  add_custom_command(
    TARGET doSearch
    POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy "${doSearch_RESOURCES}/doSearch.icns" "${doSearch_APP}/Contents/Resources/doSearch.icns"
    VERBATIM
  )

  set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})
  install(CODE "
    include(BundleUtilities)
    fixup_bundle(doSearch.app \"\" \"\")
  ")
  include(BundleUtilities)
  fixup_bundle(doSearch.app \"\" \"\")

elseif(OS_WINDOWS)
  set(ICON_NAME "doSearch.ico")
  set(ICON_PATH "resources/${ICON_NAME}")

  add_executable(doSearch ${doSearch_sources} ${ICON_PATH} ${RESOURCES})

  target_compile_options(doSearch PRIVATE "/Zi")
  set_target_properties(doSearch PROPERTIES LINK_FLAGS "/DEBUG")

  target_link_libraries(doSearch shlwapi)
endif()

target_include_directories(doSearch PRIVATE
  ../libs/qxmpp/src/client ../libs/qxmpp/src/base
  ../libs/discount
  ../libs/peg-markdown-highlight
  ../libs/hunspell
  ../libs/cutemarked
  ../libs/breakpad
  ../libs/breakpad/src
  ../libs/leveldb/include
)

#set_target_properties(doSearch PROPERTIES PREFIX com.expleague.)

target_link_libraries(doSearch
  Qt5::Widgets
  Qt5::Core
  Qt5::Network
  Qt5::Location
  Qt5::Concurrent
  Qt5::Positioning
  Qt5::Gui
  Qt5::Quick
  Qt5::QuickControls2
  Qt5::Xml
  Qt5::XmlPatterns
  Qt5::Multimedia
  Qt5::OpenGL
)

add_dependencies(doSearch
  qxmpp
  discount
  pmh
  hunspell
  cutemarked
  breakpad
  leveldb
)

target_link_libraries(doSearch
  qxmpp
  discount
  pmh
  hunspell
  cutemarked
  breakpad
  leveldb
)

target_link_libraries(doSearch ${EXTRA_LIBS})

if (CEF)
  add_definitions(-DCEF)

  set(CEF_ROOT "${PROJECT_SOURCE_DIR}/src/libs/cef-mac")
  set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CEF_ROOT}/cmake")
  find_package(CEF REQUIRED)
  add_dependencies(doSearch libcef_dll_wrapper cef-exec)

  target_include_directories(doSearch PUBLIC ../libs/cef-mac)
  ADD_LOGICAL_TARGET("libcef_lib" "${CEF_LIB_DEBUG}" "${CEF_LIB_RELEASE}")
  target_link_libraries(doSearch
    libcef_lib
    libcef_dll_wrapper
  )

  if (OS_MACOSX)
    # Output paths for the app bundles.
    set(CEF_APP "${CMAKE_CURRENT_BINARY_DIR}/doSearch.app")
    set(CEF_HELPER_APP "${CMAKE_CURRENT_BINARY_DIR}/../libs/cef-exec/cef-exec.app")

    # Variable referenced from Info.plist files.
#    set_target_properties(${CEF_TARGET} PROPERTIES
#      RESOURCE "${CEFSIMPLE_RESOURCES_SRCS}"
#      MACOSX_BUNDLE_INFO_PLIST ${CMAKE_CURRENT_SOURCE_DIR}/mac/Info.plist
#    )

    # Copy files into the main app bundle.
    add_custom_command(
      TARGET doSearch
      POST_BUILD
      # Copy the helper app bundle into the Frameworks directory.
      COMMAND ${CMAKE_COMMAND} -E copy_directory
      "${CEF_HELPER_APP}"
      "${CEF_APP}/Contents/Frameworks/doSearch Helper.app"
      COMMAND ${CMAKE_COMMAND} -E rename
      "${CEF_APP}/Contents/Frameworks/doSearch Helper.app/Contents/MacOS/cef-exec"
      "${CEF_APP}/Contents/Frameworks/doSearch Helper.app/Contents/MacOS/doSearch Helper"
      # Copy the CEF framework into the Frameworks directory.
      COMMAND ${CMAKE_COMMAND} -E copy_directory
      "${CEF_BINARY_DIR}/Chromium Embedded Framework.framework"
      "${CEF_APP}/Contents/Frameworks/Chromium Embedded Framework.framework"
      VERBATIM
    )

    # Fix the framework rpath in the main executable.
    set_target_properties(doSearch PROPERTIES INSTALL_RPATH "@executable_path/..;${_qt5_root_dir}/../")
    set_target_properties(doSearch PROPERTIES BUILD_WITH_INSTALL_RPATH TRUE)


    #    if(NOT ${CMAKE_GENERATOR} STREQUAL "Xcode")
#      # Manually process and copy over resource files.
#      # The Xcode generator handles this via the set_target_properties RESOURCE directive.

    #    endif()
  endif()
else()
  target_link_libraries(doSearch
    Qt5::WebEngine
    Qt5::WebEngineCore
  )
endif()

include(BundleUtilities)
#fixup_bundle(doSearch "" "")

add_executable(tests ./test/main_test.cpp ./test/util/pholder_test.cpp ./c++/util/pholder.cpp ./c++/util/leveldb.cpp)
target_link_libraries(tests
  leveldb
  Qt5::Core
  Qt5::Test
)
