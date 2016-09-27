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
        return status != .closed && status != .archived && status != .canceled
    }
    
    var activeExpert: ExpLeagueMember? {
        let result = experts.last
        return _expertActive ? result : nil
    }
    
    fileprivate dynamic var _experts: [ExpLeagueMember]?
    fileprivate dynamic var _expertActive = false
    var experts: [ExpLeagueMember] {
        guard _experts == nil else {
            return _experts!
        }
        var result: [ExpLeagueMember] = []
        var active = false
        for msg in messages {
            if (msg.type == .expertAssignment){
                result.append(msg.expert!)
                active = true
            }
            else if (msg.type == .expertCancel) {
                result.removeLast()
                active = false
            }
            else if (msg.type == .answer) {
                active = false
            }
        }
        _expertActive = active
        _experts = result
        return result
    }
    
    fileprivate dynamic var _messages: [ExpLeagueMessage]?
    var messages: [ExpLeagueMessage] {
        let _messages = self._messages
        guard _messages == nil else {
            return _messages!
        }
        let result: [ExpLeagueMessage] = self.messagesRaw.array as! [ExpLeagueMessage]
        self._messages = result
        return result
    }
    
    func messagesChanged() {
        _unreadCount = nil
        _messages = nil
        _experts = nil
        QObject.notify(#selector(messagesChanged), self)
    }

    var before: TimeInterval {
        return started + offer.duration
    }
    
    var timeLeft: TimeInterval {
        return before - Date().timeIntervalSinceReferenceDate
    }
    
    fileprivate dynamic var _icon: UIImage?
    var typeIcon: UIImage {
        guard _icon == nil else {
            return _icon!
        }
        var tags: [String] = []
        for message in messages {
            if let change = message.change , change.target == .Tag {
                switch change.type {
                case .Add:
                    tags.append(change.name)
                    break
                case .Remove:
                    if let index = tags.index(of: change.name) {
                        tags.remove(at: index)
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
    
    internal func message(message msg: XMPPMessage, notify: Bool) {
        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        messagesRaw = messagesRaw.append(message)
        if (message.type == .answer) {
            flags = flags | ExpLeagueOrderFlags.deciding.rawValue
        }
        messagesChanged()
        save()
        if (notify) {
            if (message.type == .answer) {
                Notifications.notifyAnswerReceived(self, answer: message)
            }
            else if (message.type == .expertAssignment) {
                Notifications.notifyExpertFound(self)
            }
            else if (message.type == .expertMessage) {
                Notifications.notifyMessageReceived(self, message: message)
            }
        }
        else {
            DispatchQueue.main.async {
                message.read = true
            }
        }
        if (message.type == .clientDone) {
            update {
                self.flags = self.flags | ExpLeagueOrderFlags.closed.rawValue
                DispatchQueue.main.async {
                    AppDelegate.instance.historyView?.populate()
                }
            }
        }
        else if (message.type == .clientCancel) {
            update {
                self.flags = self.flags | ExpLeagueOrderFlags.canceled.rawValue
                DispatchQueue.main.async {
                    AppDelegate.instance.historyView?.populate()
                }
            }
        }
    }
    
    func send(text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)!
        msg.addBody(text)
        update {
            self.message(message: msg, notify: false)
        }
        _ = parent.send(msg)
    }
    
    func send(xml: DDXMLElement, type: String = "normal") {
        let msg = XMPPMessage(type: type, to: jid)!
        msg.addChild(xml)
        update {
            self.message(message: msg, notify: false)
        }
        _ = parent.send(msg)
    }
    
    var jid : XMPPJID {
        return XMPPJID(string: id + "@muc." + AppDelegate.instance.activeProfile!.domain)
    }
    
    var text: String {
        return offer.topic
    }
    
    func cancel(_ ownerVC: UIViewController? = nil) {
        if let vc = ownerVC {
            let alertView = UIAlertController(title: "unSearch", message: "Вы уверены, что хотите отменить задание?", preferredStyle: .alert)
            alertView.addAction(UIAlertAction(title: "Да", style: .default, handler: {(x: UIAlertAction) -> Void in
                self.cancel(nil)
            }))
            
            alertView.addAction(UIAlertAction(title: "Нет", style: .cancel, handler: nil))
            vc.present(alertView, animated: true, completion: nil)
            return
        }

        send(xml: DDXMLElement(name: "cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
    }
    
    func feedback(stars score: Int, payment: String?) {
        let feedback = DDXMLElement(name: "feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback?.addAttribute(withName: "stars", integerValue: score)
        if let id = payment {
            feedback?.addAttribute(withName: "payment", stringValue: id)
        }
        send(xml: feedback!)
        
        close()
    }

    func close() {
        send(xml: DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
    }
    
    func emulate() {
        self.flags = self.flags | ExpLeagueOrderFlags.closed.rawValue | ExpLeagueOrderFlags.fake.rawValue
        save()
    }
    
    func markSaved() {
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.saved.rawValue
        }
    }
    var fake: Bool {
        return (flags & ExpLeagueOrderFlags.fake.rawValue) != 0 && (flags & ExpLeagueOrderFlags.saved.rawValue == 0)
    }
    
    func archive() {
        if (isActive) {
            cancel()
        }
        update {
            self.flags |= ExpLeagueOrderFlags.archived.rawValue
        }
    }
    
    func continueTask() {
        if (status == .deciding) {
            update {
                self.flags ^= ExpLeagueOrderFlags.deciding.rawValue
            }
        }
    }
    
    var status: ExpLeagueOrderStatus {
        if (flags & ExpLeagueOrderFlags.archived.rawValue != 0) {
            return .archived
        }
        else if (flags & ExpLeagueOrderFlags.canceled.rawValue != 0) {
            return .canceled
        }
        else if (flags & ExpLeagueOrderFlags.closed.rawValue != 0) {
            return .closed
        }
        else if (flags & ExpLeagueOrderFlags.deciding.rawValue != 0) {
            return .deciding
        }
        else if (activeExpert == nil) {
            return .expertSearch
        }
        else if (before - CFAbsoluteTimeGetCurrent() > 0) {
            return .open
        }
        else {
            return .overtime
        }
    }
    
    fileprivate dynamic var _shortAnswer: String?
    var shortAnswer: String {
        guard _shortAnswer == nil else {
            return _shortAnswer!
        }
        
        var result: String? = nil
        if let answer = messages.filter({msg in msg.type == .answer}).last {
            result = answer.properties["short"] as? String
        }
        result = result ?? "Нет простого ответа"
        _shortAnswer = result!
        return result!
    }
    
    internal dynamic var _unreadCount: NSNumber?
    var unreadCount: Int {
        guard _unreadCount == nil else {
            return _unreadCount!.intValue
        }
        let result = messages.filter({$0.type == .expertMessage || $0.type == .answer}).filter({!$0.read}).count
        _unreadCount = result as NSNumber?
        return result
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
    
    init(_ roomId: String, offer: ExpLeagueOffer, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "Order", in: context)!, insertInto: context)
        self.started = offer.started.timeIntervalSinceReferenceDate
        self.id = roomId.lowercased()
        self.topic = offer.xml.xmlString
        save()
    }

    fileprivate dynamic var _offer: ExpLeagueOffer?
    var offer: ExpLeagueOffer {
        if _offer == nil {
            do {
                _offer = ExpLeagueOffer(xml: try DDXMLElement(xmlString: topic))
            }
            catch { // old format
                do {
                    _offer = try ExpLeagueOffer(json: try JSONSerialization.jsonObject(with: topic.data(using: String.Encoding.utf8)!, options: []) as! [String: AnyObject])
                }
                catch {
                    _offer = ExpLeagueOffer(plain: topic)
                }
            }
        }
        return _offer!
    }
    
    override func invalidate() {
        _shortAnswer = nil
        _experts = nil
        _icon = nil
    }
    
    override func notify() {
        QObject.notify(#selector(self.notify), self)
    }
}

class ExpLeagueOffer: NSObject {
    let xml: DDXMLElement
    
    var duration: TimeInterval {
        switch(xml.attributeStringValue(forName: "urgency")) {
        case "day":
            return 24 * 60 * 60
        case "asap":
            return 60 * 60
        default:
            return 7 * 24 * 60 * 60
        }
    }
    
    var topic: String {
        return xml.forName("topic")!.stringValue!
    }
    
    var images: [URL] {
        var result: [URL] = []
        for imageElement in xml.elements(forName: "image") {
            result.append(URL(string: imageElement.stringValue!)!)
        }
        return result
    }
    
    var started: Date {
        return Date(timeIntervalSince1970: xml.attributeDoubleValue(forName: "started"))
    }
    
    var local: Bool {
        return xml.attributeBoolValue(forName: "local")
    }
    
    var location: CLLocationCoordinate2D? {
        if let location = xml.forName("location", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            return CLLocationCoordinate2DMake(location.attributeDoubleValue(forName: "latitude") , location.attributeDoubleValue(forName: "longitude"))
        }
        return nil
    }
    
    var room: String {
        let roomAttr = XMPPJID(string: self.xml.attributeStringValue(forName: "room"))
        return roomAttr!.user
    }
    
    init(xml: DDXMLElement) {
        self.xml = xml
    }
    
    init(topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String], started: TimeInterval?) {
        let offer = DDXMLElement(name: "offer", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        offer?.addAttribute(withName: "local", boolValue: local)
        offer?.addAttribute(withName: "urgency", stringValue: urgency)
        offer?.addAttribute(withName: "started", doubleValue: started ?? Date().timeIntervalSince1970)
        let topicElement = DDXMLElement(name: "topic", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
        topicElement.stringValue = topic
        offer?.addChild(topicElement)
        var expertsElement: DDXMLElement? = nil
        for expert in experts {
            expertsElement = expertsElement != nil ? expertsElement: DDXMLElement(name: "experts-filter", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            let accept = DDXMLElement(name: "accept", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
            accept.stringValue = "\(expert)"
            expertsElement?.addChild(accept)
        }
        if expertsElement != nil {
            offer?.addChild(expertsElement!)
        }
        
        for img in images {
            if (img.isEmpty) {
                continue
            }
            let imageElement = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
            imageElement.stringValue = "\(AppDelegate.instance.activeProfile!.imageStorage)\(img)"
            offer?.addChild(imageElement)
        }
        
        if let location = locationOrNil {
            let locationElement = DDXMLElement(name: "location", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            locationElement?.addAttribute(withName: "longitude", doubleValue: location.longitude)
            locationElement?.addAttribute(withName: "latitude", doubleValue: location.latitude)
            offer?.addChild(locationElement!)
        }
        xml = offer!
    }
    
    convenience init(json: [String: AnyObject]) throws {
        let location = json["location"] as? [String: AnyObject]
        self.init(
            topic: json["topic"] as! String,
            urgency: json["urgency"] as! String,
            local: json["local"] as! Bool,
            location: location != nil ? CLLocationCoordinate2D(latitude: location!["latitude"] as! Double, longitude: location!["longitude"] as! Double) : nil,
            experts: [],
            images: (json["attachments"] as! String).components(separatedBy: ", "),
            started: json["started"] as? TimeInterval
        )
    }
    
    convenience init(plain topic: String) {
        self.init(topic: topic, urgency: "day", local: false, location: nil, experts: [], images: [], started: nil)
    }
}

enum ExpLeagueOrderStatus: Int {
    case open = 0
    case closed = 1
    case overtime = 2
    case canceled = 3
    case archived = 4
    case expertSearch = 5
    case deciding = 7
}

enum ExpLeagueOrderFlags: Int16 {
    case localTask = 16384
    case specificTask = 8196
    case closed = 4096
    case canceled = 2048
    case archived = 1024
    case deciding = 512
    case fake = 4
    case saved = 8
}
