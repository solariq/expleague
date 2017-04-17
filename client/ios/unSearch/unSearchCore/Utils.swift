//
//  Utils.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 30.10.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData

class Utils {
    static func randString(_ len: Int, seed: Int? = nil) -> String {
        let seedX = seed != nil ? seed! : Int(arc4random())
        let chars = "0123456789ABCDEF".characters
        var result = ""
        
        srand48(seedX)
        for _ in 0..<len {
            let idx = Int(drand48()*Double(chars.count))
            result.append(chars[chars.index(chars.startIndex, offsetBy: idx)])
        }
        return result
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
        ExpLeagueCommunicator.xmppQueue.async {
            todo()
            self.save()
        }
    }
    
    func updateSync(_ todo: () -> ()) {
        todo()
        ExpLeagueCommunicator.xmppQueue.async {
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
