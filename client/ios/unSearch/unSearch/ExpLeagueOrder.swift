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
    var isActive: Bool {
        return status != .Closed && status != .Archived && status != .Canceled
    }
    
    var activeExpert: ExpLeagueMember? {
        let result = experts.last
        return _expertActive ? result : nil
    }
    
    private dynamic var _experts: [ExpLeagueMember]?
    private dynamic var _expertActive = false
    var experts: [ExpLeagueMember] {
        guard _experts == nil else {
            return _experts!
        }
        var result: [ExpLeagueMember] = []
        var active = false
        for msg in messages {
            if (msg.type == .ExpertAssignment){
                result.append(msg.expert!)
                active = true
            }
            else if (msg.type == .ExpertCancel) {
                result.removeLast()
                active = false
            }
            else if (msg.type == .Answer) {
                active = false
            }
        }
        _expertActive = active
        _experts = result
        return result
    }
    
    private dynamic var _messages: [ExpLeagueMessage]?
    var messages: [ExpLeagueMessage] {
        guard _messages == nil else {
            return _messages!
        }
        let result: [ExpLeagueMessage] = self.messagesRaw.array as! [ExpLeagueMessage]
        self._messages = result
        return result
    }

    var before: NSTimeInterval {
        return started + offer.duration
    }
    
    var timeLeft: NSTimeInterval {
        return before - NSDate().timeIntervalSinceReferenceDate
    }
    
    private dynamic var _icon: UIImage?
    var typeIcon: UIImage {
        guard _icon == nil else {
            return _icon!
        }
        var tags: [String] = []
        for message in messages {
            if let change = message.change where change.target == .Tag {
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
        let result = tags.isEmpty ? UIImage(named: "search_icon")! : parent.tag(name: tags.last!)?.icon ?? UIImage(named: "search_icon")!
        _icon = result
        return result
    }
    
    internal func message(message msg: XMPPMessage) {
        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        messagesRaw = messagesRaw.append(message)
        if (message.type == .Answer) {
            flags = flags | ExpLeagueOrderFlags.Deciding.rawValue
        }
        save()
        if (message.type == .Answer) {
            Notifications.notifyAnswerReceived(self, answer: message)
        }
        else if (message.type == .ExpertAssignment) {
            Notifications.notifyExpertFound(self)
        }
        else if (message.type == .ExpertMessage) {
            Notifications.notifyMessageReceived(self, message: message)
        }
    }
    
    func send(text text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addBody(text)
        update {
            self.message(message: msg)
        }
        parent.send(msg)
    }
    
    func send(xml xml: DDXMLElement) {
        send(xml: xml, type: "normal")
    }
    
    func send(xml xml: DDXMLElement, type: String) {
        let msg = XMPPMessage(type: type, to: jid)
        msg.addChild(xml)
        update {
            self.message(message: msg)
        }
        parent.send(msg)
    }
    
    var jid : XMPPJID {
        return XMPPJID.jidWithString(id + "@muc." + AppDelegate.instance.activeProfile!.domain)
    }
    
    var text: String {
        return offer.topic
    }
    
    func cancel(ownerVC: UIViewController? = nil) {
        if let vc = ownerVC {
            let alertView = UIAlertController(title: "unSearch", message: "Вы уверены, что хотите отменить задание?", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Да", style: .Default, handler: {(x: UIAlertAction) -> Void in
                self.cancel(nil)
            }))
            
            alertView.addAction(UIAlertAction(title: "Нет", style: .Cancel, handler: nil))
            vc.presentViewController(alertView, animated: true, completion: nil)
            return
        }

        let msg = XMPPMessage(type: "normal", to: jid)
        msg.addChild(DDXMLElement(name: "cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        self.parent.send(msg)
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.Canceled.rawValue
            dispatch_async(dispatch_get_main_queue()) {
                AppDelegate.instance.historyView?.populate()
            }
        }
    }
    
    func feedback(stars score: Int) {
        let feedback = DDXMLElement(name: "feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback.addAttributeWithName("stars", integerValue: score)
        send(xml: feedback)
        
        close()
    }

    func close() {
        send(xml: DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.Closed.rawValue
            dispatch_async(dispatch_get_main_queue()) {
                AppDelegate.instance.historyView?.populate()
            }
        }
    }
    
    func emulate() {
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.Closed.rawValue | ExpLeagueOrderFlags.Fake.rawValue
        }
    }
    
    func markSaved() {
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.Saved.rawValue
        }
    }
    var fake: Bool {
        return (flags & ExpLeagueOrderFlags.Fake.rawValue) != 0 && (flags & ExpLeagueOrderFlags.Saved.rawValue == 0)
    }
    
    func archive() {
        if (isActive) {
            cancel()
        }
        update {
            self.flags |= ExpLeagueOrderFlags.Archived.rawValue
        }
    }
    
    func continueTask() {
        if (status == .Deciding) {
            update {
                self.flags ^= ExpLeagueOrderFlags.Deciding.rawValue
            }
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
        else if (flags & ExpLeagueOrderFlags.Deciding.rawValue != 0) {
            return .Deciding
        }
        else if (activeExpert == nil) {
            return .ExpertSearch
        }
        else if (before - CFAbsoluteTimeGetCurrent() > 0) {
            return .Open
        }
        else {
            return .Overtime
        }
    }
    
    private dynamic var _shortAnswer: String?
    var shortAnswer: String {
        guard _shortAnswer == nil else {
            return _shortAnswer!
        }
        
        var result: String? = nil
        if let answer = messages.filter({msg in msg.type == .Answer}).last {
            result = answer.properties["short"] as? String
        }
        result = result ?? "Нет простого ответа"
        _shortAnswer = result!
        return result!
    }
    
    internal dynamic var _unreadCount: NSNumber?
    var unreadCount: Int {
        guard _unreadCount == nil else {
            return _unreadCount!.longValue
        }
        let result = messages.filter({$0.type == .ExpertMessage || $0.type == .Answer}).filter({!$0.read}).count
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
        save()
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
    
    override func invalidate() {
        _shortAnswer = nil
        _experts = nil
        _icon = nil
        _unreadCount = nil
        _messages = nil
    }
    
    override func notify() {
        model?.sync()
        badge?.update(order: self)
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
    case Saved = 8
}
