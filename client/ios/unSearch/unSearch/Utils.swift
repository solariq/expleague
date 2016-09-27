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
    convenience init(trailingClosure closure: @escaping (() -> ())) {
        // let UIGestureRecognizer do its thing
        self.init()
        
        target.append(Target(closure))
        self.addTarget(target.last!, action: #selector(Target.invoke))
    }
}

private class Target {
    // store closure
    fileprivate var trailingClosure: (() -> ())
    
    init(_ closure:@escaping (() -> ())) {
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

extension Timer {
    /**
     Creates and schedules a one-time `NSTimer` instance.
     
     - Parameters:
     - delay: The delay before execution.
     - handler: A closure to execute after `delay`.
     
     - Returns: The newly-created `NSTimer` instance.
     */
    class func schedule(delay: TimeInterval, handler: @escaping (CFRunLoopTimer?) -> Void) -> Timer {
        let fireDate = delay + CFAbsoluteTimeGetCurrent()
        let timer = CFRunLoopTimerCreateWithHandler(kCFAllocatorDefault, fireDate, 0, 0, 0, handler)
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, CFRunLoopMode.commonModes)
        return timer!
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
    class func schedule(repeatInterval interval: TimeInterval, handler: @escaping (CFRunLoopTimer?) -> Void) -> Timer {
        let fireDate = interval + CFAbsoluteTimeGetCurrent()
        let timer = CFRunLoopTimerCreateWithHandler(kCFAllocatorDefault, fireDate, interval, 0, 0, handler)
        CFRunLoopAddTimer(CFRunLoopGetCurrent(), timer, CFRunLoopMode.commonModes)
        return timer!
    }
}

extension NSSet {
    func append(_ item: Element) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        mutable.add(item)
        return mutable.copy() as! NSSet
    }
    
    func removeOne(_ item: Element) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        mutable.remove(item)
        return mutable.copy() as! NSSet
    }

    func filter<T>(predicate p: (T) -> Bool) -> NSSet {
        let mutable = mutableCopy() as! NSMutableSet
        for item in mutable {
            if (p(item as! T)) {
                mutable.remove(item)
            }
        }
        return mutable.copy() as! NSSet
    }
}

extension NSOrderedSet {
    func append(_ item: Element) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        mutable.add(item)
        return mutable.copy() as! NSOrderedSet
    }
    
    func removeOne(_ item: Element) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        mutable.remove(item)
        return mutable.copy() as! NSOrderedSet
    }
    
    func removeAll<T>(predicate p: (T) -> Bool) -> NSOrderedSet {
        let mutable = mutableCopy() as! NSMutableOrderedSet
        var found = false
        repeat {
            found = false
            for item in mutable {
                if (p(item as! T)) {
                    mutable.remove(item)
                    found = true
                    break
                }
            }
        }
        while(found)
        return mutable.copy() as! NSOrderedSet
    }
}

extension NSManagedObject {
    func notify() {}
    func invalidate() {}
    
    func update(_ todo: @escaping () -> ()) {
        AppDelegate.instance.xmppQueue.async {
            todo()
            self.save()
        }
    }
    
    func updateSync(_ todo: () -> ()) {
        AppDelegate.instance.xmppQueue.sync {
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
        DispatchQueue.main.async {
            self.notify()
        }
    }
}

extension Array where Element : Equatable {
    mutating func removeOne(_ item: Iterator.Element) -> Bool {
        if let idx = index(where: {$0 == item}) {
            remove(at: idx)
            return true
        }
        return false
    }
}

extension Array {
    mutating func removeFirstOrNone() -> Iterator.Element? {
        return isEmpty ? nil : removeFirst()
    }
}


extension String {
    func matches(regexp re: String) -> Bool {
        return range(of: re, options: [.regularExpression], range: nil, locale: nil) == startIndex..<endIndex
    }
}

class KeyboardStateTracker: NSObject {
    func start() {
        NotificationCenter.default.addObserver(self, selector: #selector(KeyboardStateTracker.keyboardShown(_:)), name: NSNotification.Name.UIKeyboardWillShow, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(KeyboardStateTracker.keyboardHidden(_:)), name: NSNotification.Name.UIKeyboardWillHide, object: nil)
    }
    
    func stop() {
        NotificationCenter.default.removeObserver(self)
    }
    
    @objc func keyboardHidden(_ notification: Notification) {
        let duration = (notification as NSNotification).userInfo![UIKeyboardAnimationDurationUserInfoKey] as! TimeInterval;
        let curve = (notification as NSNotification).userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.beginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.curveEaseIn.rawValue << curve))]
        UIView.animate(withDuration: duration, delay: 0, options: options, animations: { () -> Void in
            self.closure(CGFloat(0))
        }, completion: nil)
    }
    
    @objc func keyboardShown(_ notification: Notification) {
        let kbSize = ((notification as NSNotification).userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).cgRectValue.size
        let duration = (notification as NSNotification).userInfo![UIKeyboardAnimationDurationUserInfoKey] as! TimeInterval;
        let curve = (notification as NSNotification).userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.beginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.curveEaseIn.rawValue << curve))]
        UIView.animate(withDuration: duration, delay: 0, options: options, animations: { () -> Void in
            self.closure(kbSize.height)
        }, completion: nil)
    }
    
    let closure: (CGFloat) -> ()
    init(_ closure: @escaping (CGFloat)->()) {
        self.closure = closure;
    }
}

class Lang {
    static func rusNumEnding(_ count: Int, variants: [String]) -> String {
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
        return CGPoint(x: self.midX, y: self.midY)
    }
}

extension PHAsset {
    class func fetchSquareThumbnail(_ size: CGFloat, localId: String, callback: @escaping (UIImage?, [AnyHashable: Any]?) -> ()) {
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [localId], options: nil)
        if fetchResult.count > 0 {
            let asset = fetchResult.object(at: 0)
            let retinaScale = UIScreen.main.scale
            let retinaSquare = CGSize(width: size * retinaScale, height: size * retinaScale)
            
            let cropToSquare = PHImageRequestOptions()
            cropToSquare.resizeMode = .exact;
            
            let cropSideLength = CGFloat(min(asset.pixelWidth, asset.pixelHeight))
            let square = CGRect(x: 0, y: 0, width: cropSideLength, height: cropSideLength)
            let cropRect = square.applying(CGAffineTransform(scaleX: 1.0 / CGFloat(asset.pixelWidth), y: 1.0 / CGFloat(asset.pixelHeight)));
            
            cropToSquare.normalizedCropRect = cropRect;

            PHImageManager.default().requestImage(
                for: asset,
                targetSize: retinaSquare,
                contentMode: PHImageContentMode.aspectFit,
                options: cropToSquare,
                resultHandler: callback
            )
        }
    }
}

extension UINavigationController {
    open override var shouldAutorotate : Bool {
        guard visibleViewController as? UIAlertController == nil else {
            return super.shouldAutorotate
        }
        return visibleViewController?.shouldAutorotate ?? true
    }
    
    open override var supportedInterfaceOrientations : UIInterfaceOrientationMask {
        guard visibleViewController as? UIAlertController == nil else {
            return super.supportedInterfaceOrientations
        }
        return visibleViewController?.supportedInterfaceOrientations ?? [.all]
    }
    
    open override var preferredInterfaceOrientationForPresentation : UIInterfaceOrientation {
        guard visibleViewController as? UIAlertController == nil else {
            return super.preferredInterfaceOrientationForPresentation
        }
        return visibleViewController?.preferredInterfaceOrientationForPresentation ?? .portrait
    }
}

