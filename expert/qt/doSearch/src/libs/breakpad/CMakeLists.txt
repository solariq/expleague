include_directories(src)

if (APPLE)
  set(breakpad_sources
          ./src/client/mac/handler/exception_handler.cc
          ./src/client/mac/crash_generation/crash_generation_client.cc
          ./src/client/mac/crash_generation/crash_generation_server.cc
          ./src/client/mac/handler/minidump_generator.cc
          ./src/client/mac/handler/dynamic_images.cc
          ./src/client/mac/handler/breakpad_nlist_64.cc
          ./src/client/minidump_file_writer.cc
          ./src/common/mac/macho_id.cc
          ./src/common/mac/macho_walker.cc
          ./src/common/mac/macho_utilities.cc
          ./src/common/mac/string_utilities.cc
          ./src/common/mac/file_id.cc
          ./src/common/mac/MachIPC.mm
          ./src/common/mac/bootstrap_compat.cc
          ./src/common/md5.cc
          ./src/common/string_conversion.cc
          ./src/common/linux/linux_libc_support.cc
          ./src/common/convert_UTF.c)
  #  LIBS += /System/Library/Frameworks/CoreFoundation.framework/Versions/A/CoreFoundation
  #  LIBS += /System/Library/Frameworks/CoreServices.framework/Versions/A/CoreServices
elseif(LINUX)
  set(breakpad_sources
    ./src/client/linux/crash_generation/crash_generation_client.cc
    ./src/client/linux/handler/exception_handler.cc
    ./src/client/linux/handler/minidump_descriptor.cc
    ./src/client/linux/minidump_writer/minidump_writer.cc
    ./src/client/linux/minidump_writer/linux_dumper.cc
    ./src/client/linux/minidump_writer/linux_ptrace_dumper.cc
    ./src/client/linux/log/log.cc
    ./src/client/minidump_file_writer.cc
    ./src/common/linux/linux_libc_support.cc
    ./src/common/linux/file_id.cc
    ./src/common/linux/memory_mapped_file.cc
    ./src/common/linux/safe_readlink.cc
    ./src/common/linux/guid_creator.cc
    ./src/common/linux/elfutils.cc
    ./src/common/string_conversion.cc
    ./src/common/convert_UTF.c)

  #breakpad app need debug info inside binaries
elseif(WIN32)
  set(breakpad_sources
          ./src/client/windows/handler/exception_handler.cc
          ./src/common/windows/string_utils.cc
          ./src/common/windows/guid_string.cc
          ./src/client/windows/crash_generation/crash_generation_client.cc)
endif()
add_library(breakpad ${breakpad_sources})
