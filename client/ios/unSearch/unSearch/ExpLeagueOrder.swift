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
    var count: Int {
        return messagesRaw.count
    }
    
    var isActive: Bool {
        return status != .Closed && status != .Archived && status != .Canceled
    }
    
    var expert: ExpLeagueMember? {
        var lastExpert: ExpLeagueMember? = nil
        for i in 0 ..< count {
            let msg = message(i)
            if (msg.type == .ExpertAssignment){
                lastExpert = msg.expert
            }
            else if (msg.type == .ExpertCancel || msg.type == .Answer) {
                lastExpert = nil
            }
        }
        return lastExpert
    }
    
    var before: NSTimeInterval {
        return started + offer.duration
    }
    
    var timeLeft: NSTimeInterval {
        return before - NSDate().timeIntervalSinceReferenceDate
    }
    
    private dynamic var _icon: UIImage?
    var typeIcon: UIImage {
        if (_icon == nil) {
            var tags: [String] = []
            for msg in messagesRaw {
                if let message = msg as? ExpLeagueMessage, let change = message.change {
                    if (change.target == .Tag) {
                        switch change.type {
                        case .Add:
                            tags.append(change.name)
                            break
                        case .Remove:
                            if let index = tags.indexOf(change.name) {
                                tags.removeAtIndex(index)
                            }
                            break
                        default: break
                        }
                    }
                }
            }
            _icon = tags.isEmpty ? UIImage(named: "search_icon")! : parent.tag(name: tags.last!)?.icon ?? UIImage(named: "search_icon")!
        }
        return _icon!
    }
    
    func message(index: Int) -> ExpLeagueMessage {
        return messagesRaw[index] as! ExpLeagueMessage
    }
    
    func message(message msg: XMPPMessage) {
        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        let mutableItems = messagesRaw.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(message)
        messagesRaw = mutableItems.copy() as! NSOrderedSet
        if (message.type == .Answer) {
            flags = flags | ExpLeagueOrderFlags.Deciding.rawValue
        }
        _unreadCount = nil
        _icon = nil
        save()
    }


    func iq(iq iq: XMPPIQ) {
    }
    
    func presence(presence presence: XMPPPresence) {
    }
    
    func send(text text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addBody(text)
        message(message:msg)
        parent.send(msg)
    }
    
    func send(xml xml: DDXMLElement) {
        send(xml: xml, type: "normal")
    }
    
    func send(xml xml: DDXMLElement, type: String) {
        let msg = XMPPMessage(type: type, to: jid)
        msg.addChild(xml)
        message(message:msg)
        parent.send(msg)
    }
    
    var jid : XMPPJID {
        return XMPPJID.jidWithString(id + "@muc." + AppDelegate.instance.activeProfile!.domain)
    }
    
    var text: String {
        return offer.topic
    }
    
    func cancel(needAlert: Bool? = nil) {
        if (needAlert == nil || needAlert!) {
            let alertView = UIAlertController(title: "Лига Экспертов", message: "Вы уверены, что хотите отменить задание?", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Да", style: .Default, handler: {(x: UIAlertAction) -> Void in
                self.cancel(false)
            }))
            
            alertView.addAction(UIAlertAction(title: "Нет", style: .Cancel, handler: nil))
            AppDelegate.instance.window?.rootViewController?.presentViewController(alertView, animated: true, completion: nil)
            return
        }

        guard AppDelegate.instance.ensureConnected({self.cancel(false)}) else {
            return
        }

        flags = flags | ExpLeagueOrderFlags.Canceled.rawValue
        let msg = XMPPMessage(type: "normal", to: jid)
        msg.addChild(DDXMLElement(name: "cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        parent.send(msg)
        AppDelegate.instance.historyView?.populate()
        save()
    }
    
    func feedback(stars score: Int) {
        let feedback = DDXMLElement(name: "feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback.addAttributeWithName("stars", integerValue: score)
        send(xml: feedback)
        
        close()
    }

    func close() {
        flags = flags | ExpLeagueOrderFlags.Closed.rawValue
        send(xml: DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        AppDelegate.instance.historyView?.populate()
        save()
    }
    
    func emulate() {
        flags = flags | ExpLeagueOrderFlags.Closed.rawValue | ExpLeagueOrderFlags.Fake.rawValue
        save()
    }
    
    var fake: Bool {
        return (flags & ExpLeagueOrderFlags.Fake.rawValue) != 0
    }
    
    func archive() {
        if (isActive) {
            cancel()
        }
        flags |= ExpLeagueOrderFlags.Archived.rawValue
        save()
    }
    
    func continueTask() {
        if (status == .Deciding) {
            flags ^= ExpLeagueOrderFlags.Deciding.rawValue
        }
        save()
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
        else if (flags & ExpLeagueOrderFlags.Deciding.rawValue != 0) {
            return .Deciding
        }
        else if (expert == nil) {
            return .ExpertSearch
        }
        else if (before - CFAbsoluteTimeGetCurrent() > 0) {
            return .Open
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
        return result != nil && !result!.isEmpty ? result! : "Нет простого ответа"
    }
    
    dynamic var _unreadCount: NSNumber?
    var unreadCount: Int {
        guard _unreadCount == nil else {
            return _unreadCount!.longValue
        }
        var result = 0
        for i in 0 ..< count {
            let msg = message(i)
            if (msg.type == .ExpertMessage || msg.type == .Answer) {
                result += msg.read ? 0 : 1
            }
        }
        _unreadCount = result
        return result
    }
    
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    init(_ roomId: String, offer: ExpLeagueOffer, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Order", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.started = CFAbsoluteTimeGetCurrent()
        self.id = roomId.lowercaseString
        self.topic = offer.xml.XMLString()
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }

    private dynamic var _offer: ExpLeagueOffer?
    var offer: ExpLeagueOffer {
        if _offer == nil {
            do {
                _offer = ExpLeagueOffer(xml: try DDXMLElement(XMLString: topic))
            }
            catch { // old format
                do {
                    _offer = try ExpLeagueOffer(json: try NSJSONSerialization.JSONObjectWithData(topic.dataUsingEncoding(NSUTF8StringEncoding)!, options: []) as! [String: AnyObject])
                }
                catch {
                    _offer = ExpLeagueOffer(plain: topic)
                }
            }
        }
        return _offer!
    }
    
    dynamic weak var model: ChatModel?
    dynamic weak var badge: OrderBadge?
    
    override func save() {
        model?.sync()
        badge?.update(order: self)
        super.save()
    }
}

class ExpLeagueOffer: NSObject {
    let xml: DDXMLElement
    
    var duration: NSTimeInterval {
        switch(xml.attributeStringValueForName("urgency")) {
        case "day":
            return 24 * 60 * 60
        case "asap":
            return 60 * 60
        default:
            return 7 * 24 * 60 * 60
        }
    }
    
    var topic: String {
        return xml.elementForName("topic").stringValue()
    }
    
    var images: [NSURL] {
        var result: [NSURL] = []
        for imageElement in xml.elementsForName("image") {
            result.append(NSURL(string: imageElement.stringValue)!)
        }
        return result
    }
    
    var started: NSDate {
        return NSDate(timeIntervalSince1970: xml.attributeDoubleValueForName("started"))
    }
    
    var local: Bool {
        return xml.attributeBoolValueForName("local")
    }
    
    var location: CLLocationCoordinate2D? {
        if let location = xml.elementForName("location", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            return CLLocationCoordinate2DMake(location.attributeDoubleValueForName("latitude") , location.attributeDoubleValueForName("longitude"))
        }
        return nil
    }
    
    var room: String {
        let roomAttr = XMPPJID.jidWithString(self.xml.attributeStringValueForName("room"))
        return roomAttr.user
    }
    
    init(xml: DDXMLElement) {
        self.xml = xml
    }
    
    init(topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String], started: NSTimeInterval?) {
        let offer = DDXMLElement(name: "offer", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        offer.addAttributeWithName("local", boolValue: local)
        offer.addAttributeWithName("urgency", stringValue: urgency)
        offer.addAttributeWithName("started", doubleValue: started ?? NSDate().timeIntervalSince1970)
        let topicElement = DDXMLElement(name: "topic", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        topicElement.setStringValue(topic)
        offer.addChild(topicElement)
        var expertsElement: DDXMLElement? = nil
        for expert in experts {
            expertsElement = expertsElement != nil ? expertsElement: DDXMLElement(name: "experts-filter", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            let accept = DDXMLElement(name: "accept", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            accept.setStringValue("\(expert)")
            expertsElement?.addChild(accept)
        }
        if expertsElement != nil {
            offer.addChild(expertsElement!)
        }
        
        for img in images {
            if (img.isEmpty) {
                continue
            }
            let imageElement = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            imageElement.setStringValue("\(AppDelegate.instance.activeProfile!.imageStorage)\(img)")
            offer.addChild(imageElement)
        }
        
        if let location = locationOrNil {
            let locationElement = DDXMLElement(name: "location", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            locationElement.addAttributeWithName("longitude", doubleValue: location.longitude)
            locationElement.addAttributeWithName("latitude", doubleValue: location.latitude)
            offer.addChild(locationElement)
        }
        xml = offer
    }
    
    convenience init(json: [String: AnyObject]) throws {
        let location = json["location"] as? [String: AnyObject]
        self.init(
            topic: json["topic"] as! String,
            urgency: json["urgency"] as! String,
            local: json["local"] as! Bool,
            location: location != nil ? CLLocationCoordinate2D(latitude: location!["latitude"] as! Double, longitude: location!["longitude"] as! Double) : nil,
            experts: [],
            images: (json["attachments"] as! String).componentsSeparatedByString(", "),
            started: json["started"] as? NSTimeInterval
        )
    }
    
    convenience init(plain topic: String) {
        self.init(topic: topic, urgency: "day", local: false, location: nil, experts: [], images: [], started: nil)
    }
}

enum ExpLeagueOrderStatus: Int {
    case Open = 0
    case Closed = 1
    case Overtime = 2
    case Canceled = 3
    case Archived = 4
    case ExpertSearch = 5
    case Deciding = 7
}

enum ExpLeagueOrderFlags: Int16 {
    case LocalTask = 16384
    case SpecificTask = 8196
    case Closed = 4096
    case Canceled = 2048
    case Archived = 1024
    case Deciding = 512
    case Fake = 4
}
