TEMPLATE = lib

TARGET = breakpad

QT += core

CONFIG += staticlib

INCLUDEPATH += ./src

macx {
  HEADERS += ./src/client/mac/handler/exception_handler.h
  HEADERS += ./src/client/mac/crash_generation/crash_generation_client.h
  HEADERS += ./src/client/mac/crash_generation/crash_generation_server.h
  HEADERS += ./src/client/mac/crash_generation/client_info.h
  HEADERS += ./src/client/mac/handler/minidump_generator.h
  HEADERS += ./src/client/mac/handler/dynamic_images.h
  HEADERS += ./src/client/mac/handler/breakpad_nlist_64.h
  HEADERS += ./src/client/mac/handler/mach_vm_compat.h
  HEADERS += ./src/client/minidump_file_writer.h
  HEADERS += ./src/client/minidump_file_writer-inl.h
  HEADERS += ./src/common/mac/macho_utilities.h
  HEADERS += ./src/common/mac/byteswap.h
  HEADERS += ./src/common/mac/MachIPC.h
  HEADERS += ./src/common/mac/scoped_task_suspend-inl.h
  HEADERS += ./src/common/mac/file_id.h
  HEADERS += ./src/common/mac/macho_id.h
  HEADERS += ./src/common/mac/macho_walker.h
  HEADERS += ./src/common/mac/macho_utilities.h
  HEADERS += ./src/common/mac/bootstrap_compat.h
  HEADERS += ./src/common/mac/string_utilities.h
  HEADERS += ./src/common/linux/linux_libc_support.h
  HEADERS += ./src/common/string_conversion.h
  HEADERS += ./src/common/md5.h
  HEADERS += ./src/common/memory.h
  HEADERS += ./src/common/using_std_string.h
  HEADERS += ./src/common/convert_UTF.h
  HEADERS += ./src/processor/scoped_ptr.h
  HEADERS += ./src/google_breakpad/common/minidump_exception_mac.h
  HEADERS += ./src/google_breakpad/common/breakpad_types.h
  HEADERS += ./src/google_breakpad/common/minidump_format.h
  HEADERS += ./src/google_breakpad/common/minidump_size.h
  HEADERS += ./src/third_party/lss/linux_syscall_support.h

  SOURCES += ./src/client/mac/handler/exception_handler.cc
  SOURCES += ./src/client/mac/crash_generation/crash_generation_client.cc
  SOURCES += ./src/client/mac/crash_generation/crash_generation_server.cc
  SOURCES += ./src/client/mac/handler/minidump_generator.cc
  SOURCES += ./src/client/mac/handler/dynamic_images.cc
  SOURCES += ./src/client/mac/handler/breakpad_nlist_64.cc
  SOURCES += ./src/client/minidump_file_writer.cc
  SOURCES += ./src/common/mac/macho_id.cc
  SOURCES += ./src/common/mac/macho_walker.cc
  SOURCES += ./src/common/mac/macho_utilities.cc
  SOURCES += ./src/common/mac/string_utilities.cc
  SOURCES += ./src/common/mac/file_id.cc
  SOURCES += ./src/common/mac/MachIPC.mm
  SOURCES += ./src/common/mac/bootstrap_compat.cc
  SOURCES += ./src/common/md5.cc
  SOURCES += ./src/common/string_conversion.cc
  SOURCES += ./src/common/linux/linux_libc_support.cc
  SOURCES += ./src/common/convert_UTF.c
#  LIBS += /System/Library/Frameworks/CoreFoundation.framework/Versions/A/CoreFoundation
#  LIBS += /System/Library/Frameworks/CoreServices.framework/Versions/A/CoreServices
}
unix:!macx {
  HEADERS += ./src/client/linux/handler/exception_handler.h
  HEADERS += ./src/client/linux/crash_generation/crash_generation_client.h
  HEADERS += ./src/client/linux/handler/minidump_descriptor.h
  HEADERS += ./src/client/linux/minidump_writer/minidump_writer.h
  HEADERS += ./src/client/linux/minidump_writer/line_reader.h
  HEADERS += ./src/client/linux/minidump_writer/linux_dumper.h
  HEADERS += ./src/client/linux/minidump_writer/linux_ptrace_dumper.h
  HEADERS += ./src/client/linux/minidump_writer/directory_reader.h
  HEADERS += ./src/client/linux/log/log.h
  HEADERS += ./src/client/minidump_file_writer-inl.h
  HEADERS += ./src/client/minidump_file_writer.h
  HEADERS += ./src/common/linux/linux_libc_support.h
  HEADERS += ./src/common/linux/eintr_wrapper.h
  HEADERS += ./src/common/linux/ignore_ret.h
  HEADERS += ./src/common/linux/file_id.h
  HEADERS += ./src/common/linux/memory_mapped_file.h
  HEADERS += ./src/common/linux/safe_readlink.h
  HEADERS += ./src/common/linux/guid_creator.h
  HEADERS += ./src/common/linux/elfutils.h
  HEADERS += ./src/common/linux/elfutils-inl.h
  HEADERS += ./src/common/using_std_string.h
  HEADERS += ./src/common/memory.h
  HEADERS += ./src/common/basictypes.h
  HEADERS += ./src/common/memory_range.h
  HEADERS += ./src/common/string_conversion.h
  HEADERS += ./src/common/convert_UTF.h
  HEADERS += ./src/google_breakpad/common/minidump_format.h
  HEADERS += ./src/google_breakpad/common/minidump_size.h
  HEADERS += ./src/google_breakpad/common/breakpad_types.h
  HEADERS += ./src/processor/scoped_ptr.h
  HEADERS += ./src/third_party/lss/linux_syscall_support.h
  SOURCES += ./src/client/linux/crash_generation/crash_generation_client.cc
  SOURCES += ./src/client/linux/handler/exception_handler.cc
  SOURCES += ./src/client/linux/handler/minidump_descriptor.cc
  SOURCES += ./src/client/linux/minidump_writer/minidump_writer.cc
  SOURCES += ./src/client/linux/minidump_writer/linux_dumper.cc
  SOURCES += ./src/client/linux/minidump_writer/linux_ptrace_dumper.cc
  SOURCES += ./src/client/linux/log/log.cc
  SOURCES += ./src/client/minidump_file_writer.cc
  SOURCES += ./src/common/linux/linux_libc_support.cc
  SOURCES += ./src/common/linux/file_id.cc
  SOURCES += ./src/common/linux/memory_mapped_file.cc
  SOURCES += ./src/common/linux/safe_readlink.cc
  SOURCES += ./src/common/linux/guid_creator.cc
  SOURCES += ./src/common/linux/elfutils.cc
  SOURCES += ./src/common/string_conversion.cc
  SOURCES += ./src/common/convert_UTF.c
  #breakpad app need debug info inside binaries
}
win32 {
  HEADERS += ./src/common/windows/string_utils-inl.h
  HEADERS += ./src/common/windows/guid_string.h
  HEADERS += ./src/client/windows/handler/exception_handler.h
  HEADERS += ./src/client/windows/common/ipc_protocol.h
  HEADERS += ./src/google_breakpad/common/minidump_format.h
  HEADERS += ./src/google_breakpad/common/breakpad_types.h
  HEADERS += ./src/client/windows/crash_generation/crash_generation_client.h
  HEADERS += ./src/processor/scoped_ptr.h
  SOURCES += ./src/client/windows/handler/exception_handler.cc
  SOURCES += ./src/common/windows/string_utils.cc
  SOURCES += ./src/common/windows/guid_string.cc
  SOURCES += ./src/client/windows/crash_generation/crash_generation_client.cc
}
