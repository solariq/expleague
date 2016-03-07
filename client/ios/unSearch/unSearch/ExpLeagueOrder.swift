//
//  ExpLeagueOrder.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import XMPPFramework

class Weak<T: AnyObject> {
    weak var value: T?
    
    init(_ value: T) {
        self.value = value
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
        return status == .Open || status == .Overtime || status == .ExpertSearch
    }
    
    var expert: String? {
        var lastExpert: String? = nil
        for i in 0 ..< count {
            let msg = message(i)
            if (msg.type == .ExpertAssignment ){
                lastExpert = msg.properties["login"] as? String
            }
            else if (msg.type == .ExpertCancel || msg.type == .Answer) {
                lastExpert = nil
            }
        }
        return lastExpert
    }
    
    var before: NSTimeInterval {
        var duration: NSTimeInterval = 60 * 60
        ExpLeagueOrder.urgencyDict.forEach({
            if($1 == (self.flags & 0x1FF)) {
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
                    duration = 60 * 60;
                }
            }
        })
        return started + duration
    }
    
    var timeLeft: NSTimeInterval {
        return before - NSDate().timeIntervalSinceReferenceDate
    }
    
    func message(index: Int) -> ExpLeagueMessage {
        return messagesRaw[index] as! ExpLeagueMessage
    }
    
    func message(message msg: XMPPMessage) {
        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        let mutableItems = messagesRaw.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(message)
        messagesRaw = mutableItems.copy() as! NSOrderedSet
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
        model?.sync()
    }

    dynamic weak var model: ChatMessagesModel?
    
    func iq(iq iq: XMPPIQ) {
    }
    
    func presence(presence presence: XMPPPresence) {
    }
    
    func send(text text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addBody(text)
        message(message:msg)
        stream.sendElement(msg)
    }
    
    func send(xml xml: DDXMLElement) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addChild(xml)
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
        if (!AppDelegate.instance.stream.isConnected()) {
            let alertView = UIAlertController(title: "Experts League", message: "Connecting to server.\n\n", preferredStyle: .Alert)
            let completion = {
                //  Add your progressbar after alert is shown (and measured)
                let progressController = AppDelegate.instance.connectionProgressView
                let rect = CGRectMake(0, 54.0, alertView.view.frame.width, 50)
                progressController.completion = {
                    self.cancel()
                }
                progressController.view.frame = rect
                progressController.view.backgroundColor = alertView.view.backgroundColor
                alertView.view.addSubview(progressController.view)
                progressController.alert = alertView
                AppDelegate.instance.connect()
            }
            alertView.addAction(UIAlertAction(title: "Retry", style: .Default, handler: {(x: UIAlertAction) -> Void in
                AppDelegate.instance.disconnect()
                self.cancel()
            }))
            alertView.addAction(UIAlertAction(title: "Cancel", style: .Cancel, handler: nil))
            AppDelegate.instance.window?.rootViewController?.presentViewController(alertView, animated: true, completion: completion)
            return
        }

        flags = flags | ExpLeagueOrderFlags.Canceled.rawValue
        let msg = XMPPMessage(type: "normal", to: jid)
        msg.addChild(DDXMLElement(name: "cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        stream.sendElement(msg)
        do {
            try self.managedObjectContext!.save()
            model?.sync()
            if let h = AppDelegate.instance.historyView, tableView = h.view as? UITableView{
                tableView.reloadData()
            }
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    func close(stars score: Int) {
        flags = flags | ExpLeagueOrderFlags.Closed.rawValue
        let msg = XMPPMessage(type: "normal", to: jid)
        msg.addChild(DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        let feedback = DDXMLElement(name: "expert-feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback.addAttributeWithName("stars", integerValue: score)
        msg.addChild(feedback)
        stream.sendElement(msg)
        do {
            try self.managedObjectContext!.save()
            model?.sync()
            if let h = AppDelegate.instance.historyView, tableView = h.view as? UITableView{
                tableView.reloadData()
            }
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    func archive() {
        if (isActive) {
            cancel()
        }
        flags |= ExpLeagueOrderFlags.Archived.rawValue
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    var status: ExpLeagueOrderStatus {
        if (flags & ExpLeagueOrderFlags.Archived.rawValue != 0) {
            return .Archived
        }
        else if (flags & ExpLeagueOrderFlags.Canceled.rawValue != 0) {
            return .Canceled
        }
        else if (flags & ExpLeagueOrderFlags.Closed.rawValue != 0) {
            return .Closed
        }
        else if (before - CFAbsoluteTimeGetCurrent() > 0){
            if (expert == nil) {
                return .ExpertSearch
            }
            else {
                return .Open
            }
        }
        else {
            return .Overtime
        }
    }
    
    var shortAnswer: String {
        var result: String? = nil
        for i in 0 ..< count {
            let msg = message(i)
            if (msg.type == .Answer) {
                result = msg.properties["short"] as? String
            }
        }
        return result != nil ? result! : "Нет простого ответа"
    }
    
    var unreadCount: Int {
        var result = 0
        for i in 0 ..< count {
            let msg = message(i)
            if (msg.type == .ExpertMessage || msg.type == .Answer) {
                result += (msg.isRead) ? 0 : 1
            }
        }
        return result
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
    case Archived = 4
    case ExpertSearch = 5
}

enum ExpLeagueOrderFlags: Int16 {
    case LocalTask = 16384
    case SpecificTask = 8196
    case Closed = 4096
    case Canceled = 2048
    case Archived = 1024
}
