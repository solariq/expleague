include_directories(../cef)

if(APPLE)
  add_executable(cef-exec MACOSX_BUNDLE main.cpp)
else()
  add_executable(cef-exec main.cpp)
endif()

set_target_properties(cef-exec PROPERTIES
  CXX_STANDARD 11
  CXX_STANDARD_REQUIRED ON
  CXX_EXTENSIONS ON
)

target_include_directories(cef-exec PRIVATE ${CEF_ROOT})

target_link_libraries(cef-exec
  libcef_lib
  libcef_dll_wrapper
)

if(OS_MACOSX)
set_target_properties(${CEF_HELPER_TARGET} PROPERTIES
  MACOSX_BUNDLE_INFO_PLIST ${CMAKE_CURRENT_SOURCE_DIR}/mac/helper-Info.plist
)
# Fix the framework rpath in the helper executable.

FIX_MACOSX_HELPER_FRAMEWORK_RPATH(cef-exec)
endif()



