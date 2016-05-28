//
//  Utils.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 11/04/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import CoreData

// Global array of targets, as extensions cannot have non-computed properties
private var target = [Target]()

extension UIGestureRecognizer {
    convenience init(trailingClosure closure: (() -> ())) {
        // let UIGestureRecognizer do its thing
        self.init()
        
        target.append(Target(closure))
        self.addTarget(target.last!, action: #selector(Target.invoke))
    }
}

private class Target {
    // store closure
    private var trailingClosure: (() -> ())
    
    init(_ closure:(() -> ())) {
        trailingClosure = closure
    }
    
    // function that gesture calls, which then
    // calls closure
    /* Note: Note sure why @IBAction is needed here */
    @objc
    @IBAction func invoke() {
        trailingClosure()
    }
}

extension NSTimer {
    /**
     Creates and schedules a one-time `NSTimer` instance.
     
     - Parameters:
     - delay: The delay before execution.
     - handler: A closure to execute after `delay`.
     
     - Returns: The newly-created `NSTimer` instance.
     */
    class func schedule(delay delay: NSTimeInterval, handler: NSTimer! -> Void) -> NSTimer {
        let fireDate = delay + CFAbsoluteTimeGetCurrent()
        let timer = CFRunLoopTimerCreateWithHandler(kCFAllocatorDefault, fireDate, 0, 0, 0, handler)
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, kCFRunLoopCommonModes)
        return timer
    }
    
    /**
     Creates and schedules a repeating `NSTimer` instance.
     
     - Parameters:
     - repeatInterval: The interval (in seconds) between each execution of
     `handler`. Note that individual calls may be delayed; subsequent calls
     to `handler` will be based on the time the timer was created.
     - handler: A closure to execute at each `repeatInterval`.
     
     - Returns: The newly-created `NSTimer` instance.
     */
    class func schedule(repeatInterval interval: NSTimeInterval, handler: NSTimer! -> Void) -> NSTimer {
        let fireDate = interval + CFAbsoluteTimeGetCurrent()
        let timer = CFRunLoopTimerCreateWithHandler(kCFAllocatorDefault, fireDate, interval, 0, 0, handler)
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, kCFRunLoopCommonModes)
        return timer
    }
}

extension NSManagedObject {
    func notify() {}
    
    func update(todo: () -> ()) {
        dispatch_async(AppDelegate.instance.xmppQueue) {
            todo()
            self.save()
        }
    }
    internal final func save() {
        do {
            try self.managedObjectContext!.save()
        }
        catch {
            fatalError("Failure to save context: \(error)")
        }
        dispatch_async(dispatch_get_main_queue()) {
            self.notify()
        }
    }
}

class KeyboardStateTracker: NSObject {
    func start() {
        NSNotificationCenter.defaultCenter().addObserver(self, selector: #selector(KeyboardStateTracker.keyboardShown(_:)), name: UIKeyboardWillShowNotification, object: nil)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: #selector(KeyboardStateTracker.keyboardHidden(_:)), name: UIKeyboardWillHideNotification, object: nil)
    }
    
    func stop() {
        NSNotificationCenter.defaultCenter().removeObserver(self)
    }
    
    @objc func keyboardHidden(notification: NSNotification) {
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.closure(CGFloat(0))
        }, completion: nil)
    }
    
    @objc func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).CGRectValue().size
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.closure(kbSize.height)
        }, completion: nil)
    }
    
    let closure: (CGFloat) -> ()
    init(_ closure: (CGFloat)->()) {
        self.closure = closure;
    }
}

