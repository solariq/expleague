#import <Cocoa/Cocoa.h>
#include "mackeycodes.h"
#include "c++/cef/windowskeyboardcodes.h"

int macKeyCode(int key){
  switch(key){
    case VK_PRIOR: return NSPageUpFunctionKey;
    case VK_NEXT: return NSPageDownFunctionKey;
    case VK_END:  return NSEndFunctionKey;
    case VK_HOME:  return NSHomeFunctionKey;
    case VK_LEFT:  return NSLeftArrowFunctionKey;
    case VK_UP: return  NSUpArrowFunctionKey;
    case VK_RIGHT: return  NSRightArrowFunctionKey;
    case VK_DOWN: return   NSDownArrowFunctionKey;
    case VK_PRINT: return  NSPrintFunctionKey;
    case VK_EXECUTE: return  NSExecuteFunctionKey;
    case VK_SNAPSHOT: return  NSPrintScreenFunctionKey;
    case VK_INSERT: return  NSInsertFunctionKey;
    case VK_F1: return NSF1FunctionKey;
    case VK_F2: return NSF2FunctionKey;
    case VK_F3: return NSF3FunctionKey;
    case VK_F4: return NSF4FunctionKey;
    case VK_F5: return NSF5FunctionKey;
    case VK_F6: return NSF6FunctionKey;
    case VK_F7: return NSF7FunctionKey;
    case VK_F8: return NSF8FunctionKey;
    case VK_F9: return NSF9FunctionKey;
    case VK_F10:return NSF10FunctionKey;
    case VK_F11:return NSF11FunctionKey;
    case VK_F12:return NSF12FunctionKey;
    case VK_F13:return NSF13FunctionKey;
    case VK_F14:return NSF14FunctionKey;
    case VK_F15:return NSF15FunctionKey;
    case VK_F16:return NSF16FunctionKey;
    case VK_F17:return NSF17FunctionKey;
    case VK_F18:return NSF18FunctionKey;
    case VK_F19:return NSF19FunctionKey;
    case VK_F20:return NSF20FunctionKey;
    case VK_F21:return NSF21FunctionKey;
    case VK_F22:return NSF22FunctionKey;
    case VK_F23:return NSF23FunctionKey;
    case VK_F24:return NSF24FunctionKey;
  }
}