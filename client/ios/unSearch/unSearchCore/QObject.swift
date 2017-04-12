//
//  QObject.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 27/05/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation

public class QObject {
    class Tracker: NSObject {
        let todo: () -> Bool
        
        func fire() {
            if (!todo()) {
                NotificationCenter.default.removeObserver(self)
                _ = QObject.trackers.removeOne(self)
            }
        }
        
        init(todo: @escaping () -> Bool) {
            self.todo = todo
            super.init()
        }
    }
    
    static var trackers: [Tracker] = []
    public static func notify(_ signal: Selector, _ sender: AnyObject) {
//        print("Notifying \(signal.description) from \(sender)")
        NotificationCenter.default.post(Notification(name: Notification.Name(rawValue: signal.description), object: sender))
    }
    
    public static func connect(_ sender: AnyObject, signal: Selector, receiver: AnyObject, slot: Selector) {
//        print("Connecting \(receiver):\(slot) to \(sender):\(signal)")
        NotificationCenter.default.removeObserver(receiver, name: NSNotification.Name(rawValue: signal.description), object: sender)
        NotificationCenter.default.addObserver(receiver, selector: slot, name: NSNotification.Name(rawValue: signal.description), object: sender)
    }
    
    public static func track(_ sender: AnyObject?, _ signal: Selector, tracker: @escaping () -> Bool) {
        let trackerObj = Tracker(todo: tracker)
        trackers.append(trackerObj)
        NotificationCenter.default.addObserver(trackerObj, selector: #selector(Tracker.fire), name: NSNotification.Name(rawValue: signal.description), object: sender)
    }
    
    public static func disconnect(_ object: AnyObject) {
//        print("Disconnecting \(object)")
        NotificationCenter.default.removeObserver(object)
    }
    public static func disconnect(_ object: AnyObject, sender: AnyObject, signal: Selector) {
        //        print("Disconnecting \(object)")
        NotificationCenter.default.removeObserver(object, name: Notification.Name(rawValue: signal.description), object: sender)
    }
}
