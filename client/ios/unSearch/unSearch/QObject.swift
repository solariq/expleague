//
//  QObject.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 27/05/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation

class QObject {
    class Tracker: NSObject {
        let todo: () -> Bool
        
        func fire() {
            if (!todo()) {
                NSNotificationCenter.defaultCenter().removeObserver(self)
                QObject.trackers.removeOne(self)
            }
        }
        
        init(todo: () -> Bool) {
            self.todo = todo
            super.init()
        }
    }
    
    static var trackers: [Tracker] = []
    static func notify(signal: Selector, _ sender: AnyObject) {
//        print("Notifying \(signal.description) from \(sender)")
        NSNotificationCenter.defaultCenter().postNotification(NSNotification(name: signal.description, object: sender))
    }
    
    static func connect(sender: AnyObject, signal: Selector, receiver: AnyObject, slot: Selector) {
//        print("Connecting \(receiver):\(slot) to \(sender):\(signal)")
        NSNotificationCenter.defaultCenter().addObserver(receiver, selector: slot, name: signal.description, object: sender)
    }
    
    static func track(sender: AnyObject?, _ signal: Selector, tracker: () -> Bool) {
        let trackerObj = Tracker(todo: tracker)
        trackers.append(trackerObj)
        NSNotificationCenter.defaultCenter().addObserver(trackerObj, selector: #selector(Tracker.fire), name: signal.description, object: sender)
    }
    
    static func disconnect(object: AnyObject) {
//        print("Disconnecting \(object)")
        NSNotificationCenter.defaultCenter().removeObserver(object)
    }
}