#import <Foundation/Foundation.h>

#include "include/cef_application_mac.h"

@interface ExpLeagueApplication: NSApplication<CrAppControlProtocol>
- (BOOL)isHandlingSendEvent;
- (void)setHandlingSendEvent:(BOOL)handlingSendEvent;
@end
