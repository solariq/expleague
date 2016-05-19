#import <Foundation/Foundation.h>

@interface ExpLeagueNotificationDelegate : NSObject<NSUserNotificationCenterDelegate>
- (BOOL)userNotificationCenter:(NSUserNotificationCenter *)center shouldPresentNotification:(NSUserNotification *)notification;
@end
