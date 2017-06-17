#import <Foundation/Foundation.h>

#include "ExpLeagueNotification.h"

@implementation ExpLeagueNotificationDelegate

- (BOOL)userNotificationCenter:(NSUserNotificationCenter *)center shouldPresentNotification:(NSUserNotification *)notification {
    return YES;
}

@end

int showNotification(const char* titleC, const char* detailsC) {
    NSUserNotification *notification = [[NSUserNotification alloc] init];
    ExpLeagueNotificationDelegate *delegate = [[ExpLeagueNotificationDelegate alloc] init];
    NSString* title = [NSString stringWithCString:titleC encoding:NSUTF8StringEncoding];
    NSString* details = [NSString stringWithCString:detailsC encoding:NSUTF8StringEncoding];
    notification.title = title;
    notification.informativeText = details;
//    notification.soundName = NSUserNotificationDefaultSoundName;
    [NSUserNotificationCenter defaultUserNotificationCenter].delegate = delegate;
    [[NSUserNotificationCenter defaultUserNotificationCenter] deliverNotification:notification];

    return 0;
}
