//
//  ExpLeagueOrder.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import JSQMessagesViewController
import XMPPFramework

class Weak<T: AnyObject> {
    weak var value: T?
    
    init(_ value: T) {
        self.value = value
    }
}

@objc
class XMPPPresenceTracker: NSObject {
    let onPresence: ((presence: XMPPPresence) -> Void)?
    init(onPresence: ((presence: XMPPPresence) -> Void)?) {
        self.onPresence = onPresence
    }
}

class ExpLeagueOrder: NSManagedObject {
    var stream: XMPPStream {
        return AppDelegate.instance.stream
    }

    var count: Int {
        return messagesRaw.count
    }
    
    var isActive: Bool {
        return status == .Open || status == .Overtime
    }
    
    var before: NSTimeInterval {
        var duration: NSTimeInterval = 0
        ExpLeagueOrder.urgencyDict.forEach({
            if($1 & self.flags != 0) {
                switch($0){
                case "asap":
                    duration = 60 * 60
                    break
                case "day":
                    duration = 24 * 60 * 60
                    break
                case "week":
                    duration = 7 * 24 * 60 * 60
                    break
                default:
                    duration = 0;
                }
            }
        })
        return started + duration
    }
    
    func message(index: Int) -> ExpLeagueMessage {
        return messagesRaw[index] as! ExpLeagueMessage
    }
    
    func message(var message msg: XMPPMessage) {
        if (msg.elementsForName("body").count > 0 && msg.body().containsString("Welcome to room \(id)")) { // initial message
            msg = XMPPMessage(type: "groupchat", to: jid)
            msg.addSubject(topic)
            stream.sendElement(msg)
        }

        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        let mutableItems = messagesRaw.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(message)
        messagesRaw = mutableItems.copy() as! NSOrderedSet
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
        AppDelegate.instance.messagesView?.onMessage(message)
    }

    private dynamic var listeners: NSMutableArray = []
    func track(tracker: XMPPPresenceTracker) {
        listeners.addObject(Weak(tracker))
    }
    
    func iq(iq iq: XMPPIQ) {
    }
    
    func presence(presence presence: XMPPPresence) {
        for listenerRef in listeners.copy() as! NSArray {
            if let listener = (listenerRef as! Weak<XMPPPresenceTracker>).value {
                listener.onPresence?(presence: presence)
            }
            else {
                listeners.removeObject(listenerRef)
            }
        }
    }
    
    func send(text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addBody(text)
        message(message:msg)
        stream.sendElement(msg)
    }
    
    var jid : XMPPJID {
        return XMPPJID.jidWithString(id + "@muc." + AppDelegate.instance.activeProfile!.domain)
    }
    
    var text: String {
        if (topic.hasPrefix("{")) {
            let json = try! NSJSONSerialization.JSONObjectWithData(topic.dataUsingEncoding(NSUTF8StringEncoding)!, options: []) as! [String: AnyObject]
            return json["topic"] as! String
        }
        else {
            return topic
        }
    }
    
    func cancel() {
        flags = flags | ExpLeagueOrderFlags.Canceled.rawValue
        let msg = XMPPMessage()
        msg.addChild(DDXMLElement(name: "cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        stream.sendElement(msg)
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    func close(stars score: Int) {
        flags = flags | ExpLeagueOrderFlags.Closed.rawValue
        let msg = XMPPMessage()
        msg.addChild(DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        let feedback = DDXMLElement(name: "expert-feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback.addAttributeWithName("stars", integerValue: score)
        msg.addChild(feedback)
        stream.sendElement(msg)
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    var status: ExpLeagueOrderStatus {
        if (flags & ExpLeagueOrderFlags.Canceled.rawValue != 0) {
            return .Canceled
        }
        else if (flags & ExpLeagueOrderFlags.Closed.rawValue != 0) {
            return .Closed
        }
        else if (before - CFAbsoluteTimeGetCurrent() > 0){
            return .Open
        }
        else {
            return .Overtime
        }
    }
    
    static let urgencyDict : [String: Int16] = [
        "asap" : 256,
        "day" : 128,
        "week" : 0
    ]
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    init(_ roomId: String, topic: String, urgency: String, local: Bool, specific: Bool, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Order", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.started = CFAbsoluteTimeGetCurrent()
        self.id = roomId.lowercaseString
        self.topic = topic
        self.flags |= local ? ExpLeagueOrderFlags.LocalTask.rawValue : 0
        self.flags |= specific ? ExpLeagueOrderFlags.SpecificTask.rawValue : 0
        self.flags += ExpLeagueOrder.urgencyDict[urgency]!
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
}

enum ExpLeagueOrderStatus: Int {
    case Open = 0
    case Closed = 1
    case Overtime = 2
    case Canceled = 3
}

enum ExpLeagueOrderFlags: Int16 {
    case LocalTask = 16384
    case SpecificTask = 8196
    case Closed = 4096
    case Canceled = 2048
}
