#include "ExpLeagueApplication.h"

BOOL g_handling_send_event = false;

@implementation ExpLeagueApplication

- (BOOL)isHandlingSendEvent {
  return g_handling_send_event;
}

- (void)setHandlingSendEvent:(BOOL)handlingSendEvent {
  g_handling_send_event = handlingSendEvent;
}

@end

void initApp() {
    [ExpLeagueApplication sharedApplication];
}
