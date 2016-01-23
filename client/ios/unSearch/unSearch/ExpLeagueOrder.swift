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

class ExpLeagueOrder: NSManagedObject {
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
    
    var stream: XMPPStream {
        return AppDelegate.instance.stream
    }

    var count: Int {
        return messagesRaw.count
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
    
    func iq(iq iq: XMPPIQ) {
    }
    
    func presence(presence presence: XMPPPresence) {
        
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
    
    static let urgencyDict : [String: Int16] = [
        "asap" : 256,
        "day" : 128,
        "week" : 0
    ]
}

enum ExpLeagueOrderFlags: Int16 {
    case LocalTask = 16384
    case SpecificTask = 8196
}
