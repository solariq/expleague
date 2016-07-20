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
import Photos

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

extension NSSet {
    func append(item: Element) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        mutable.addObject(item)
        return mutable.copy() as! NSSet
    }
    
    func removeOne(item: Element) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        mutable.removeObject(item)
        return mutable.copy() as! NSSet
    }

    func filter<T>(predicate p: (T) -> Bool) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        for item in mutable {
            if (p(item as! T)) {
                mutable.removeObject(item)
            }
        }
        return mutable.copy() as! NSSet
    }
}

extension NSOrderedSet {
    func append(item: Element) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        mutable.addObject(item)
        return mutable.copy() as! NSOrderedSet
    }
    
    func remove(item: Element) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        mutable.removeObject(item)
        return mutable.copy() as! NSOrderedSet
    }
    
    func removeAll<T>(predicate p: (T) -> Bool) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        for item in mutable {
            if (p(item as! T)) {
                mutable.removeObject(item)
            }
        }
        return mutable.copy() as! NSOrderedSet
    }
}

extension NSManagedObject {
    func notify() {}
    func invalidate() {}
    
    func update(todo: () -> ()) {
        dispatch_async(AppDelegate.instance.xmppQueue) {
            todo()
            self.save()
        }
    }
    
    func updateSync(todo: () -> ()) {
        dispatch_sync(AppDelegate.instance.xmppQueue) {
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
        invalidate()
        dispatch_async(dispatch_get_main_queue()) {
            self.notify()
        }
    }
}

extension Array where Element : Equatable {
    mutating func removeOne(item: Generator.Element) -> Bool {
        if let idx = indexOf({$0 == item}) {
            removeAtIndex(idx)
            return true
        }
        return false
    }
}

extension Array {
    mutating func removeFirstOrNone() -> Generator.Element? {
        return isEmpty ? nil : removeFirst()
    }
}


extension String {
    func matches(regexp re: String) -> Bool {
        return rangeOfString(re, options: [.RegularExpressionSearch], range: nil, locale: nil) == startIndex..<endIndex
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

class Lang {
    static func rusNumEnding(count: Int, variants: [String]) -> String {
        let num = count % 100
        if (num >= 11 && num <= 19) {
            return variants[2];
        }
        else {
            switch num % 10 {
            case 1:
                return variants[0]
            case 2, 3, 4:
                return variants[1]
            default:
                return variants[2];
            }
        }
    }
}

extension CGRect {
    func center() -> CGPoint {
        return CGPointMake(self.midX, self.midY)
    }
}

extension PHAsset {
    class func fetchSquareThumbnail(size: CGFloat, localId: String, callback: (UIImage?, [NSObject: AnyObject]?) -> ()) {
        let fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers([localId], options: nil)
        if let asset = fetchResult.objectAtIndex(0) as? PHAsset {
            let retinaScale = UIScreen.mainScreen().scale
            let retinaSquare = CGSizeMake(size * retinaScale, size * retinaScale)
            
            let cropToSquare = PHImageRequestOptions()
            cropToSquare.resizeMode = .Exact;
            
            let cropSideLength = CGFloat(min(asset.pixelWidth, asset.pixelHeight))
            let square = CGRectMake(0, 0, cropSideLength, cropSideLength)
            let cropRect = CGRectApplyAffineTransform(square, CGAffineTransformMakeScale(1.0 / CGFloat(asset.pixelWidth), 1.0 / CGFloat(asset.pixelHeight)));
            
            cropToSquare.normalizedCropRect = cropRect;

            PHImageManager.defaultManager().requestImageForAsset(
                asset,
                targetSize: retinaSquare,
                contentMode: PHImageContentMode.AspectFit,
                options: cropToSquare,
                resultHandler: callback
            )
        }
    }
}

extension UINavigationController {
    public override func shouldAutorotate() -> Bool {
        guard visibleViewController as? UIAlertController == nil else {
            return super.shouldAutorotate()
        }
        return visibleViewController?.shouldAutorotate() ?? true
    }
    
    public override func supportedInterfaceOrientations() -> UIInterfaceOrientationMask {
        guard visibleViewController as? UIAlertController == nil else {
            return super.supportedInterfaceOrientations()
        }
        return visibleViewController?.supportedInterfaceOrientations() ?? [.All]
    }
    
    public override func preferredInterfaceOrientationForPresentation() -> UIInterfaceOrientation {
        guard visibleViewController as? UIAlertController == nil else {
            return super.preferredInterfaceOrientationForPresentation()
        }
        return visibleViewController?.preferredInterfaceOrientationForPresentation() ?? .Portrait
    }
}

