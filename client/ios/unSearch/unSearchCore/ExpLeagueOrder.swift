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

public class ExpLeagueOrder: NSManagedObject {
    public var isActive: Bool {
        return (!judged && status != .archived && status != .canceled) || status == .open || status == .overtime || status == .expertSearch
    }
    
    public var activeExpert: ExpLeagueMember? {
        let result = experts.last
        return _expertActive ? result : nil
    }
    
    fileprivate dynamic var _experts: [ExpLeagueMember]?
    fileprivate dynamic var _expertActive = false
    public var experts: [ExpLeagueMember] {
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
                if (!result.isEmpty) {
                    result.removeLast()
                }
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
    public var messages: [ExpLeagueMessage] {
        let _messages = self._messages
        guard _messages == nil else {
            return _messages!
        }
        let result: [ExpLeagueMessage] = self.messagesRaw.array as! [ExpLeagueMessage]
        self._messages = result
        var unread: Int32 = 0
        for msg in result {
            unread += !msg.read ? 1 : 0
        }
        if (self.unread != unread) {
            updateSync {
                parent.adjustUnread(unread - self.unread)
                self.unread = Int32(unread)
                unreadChanged()
            }
        }
        return result
    }
    
    public func messagesChanged() {
        _experts = nil
        _icon = nil
        QObject.notify(#selector(messagesChanged), self)
    }

    public var before: TimeInterval {
        return started + offer.duration
    }
    
    public var timeLeft: TimeInterval {
        return before - Date().timeIntervalSinceReferenceDate
    }
    
    public var hasFeedback = false
    
    fileprivate dynamic var _icon: UIImage?
    public var typeIcon: UIImage {
        let icon = _icon
        guard icon == nil else {
            return icon!
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
    
    fileprivate var _judged: Bool?
    public var judged: Bool {
        if (fake) {
            return true
        }
        if (_judged != nil) {
            return _judged!
        }
        
        var hasAnswer: Bool = false
        for m in messages {
            if (m.type == .answer) {
                hasAnswer = !m.isEmpty
            }
            else if (m.type == .feedback) {
                hasAnswer = false
            }
        }
        _judged = !hasAnswer
        return !hasAnswer
    }
    
    internal func message(message msg: XMPPMessage, notify: Bool) {
        guard messagesRaw.filter({($0 as! ExpLeagueMessage).id == msg.elementID()}).isEmpty else {
            return
        }
        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        if (_messages != nil) {
            _messages?.append(message)
        }

        if (message.type == .answer) {
            flags = flags | ExpLeagueOrderFlags.deciding.rawValue
            _judged = false
        }
        else if (message.type == .feedback) {
            flags = flags | ExpLeagueOrderFlags.closed.rawValue
            _judged = true
        }
        
        if (notify) {
            var unread = 1
            if (message.type == .answer) {
                Notifications.notifyAnswerReceived(self, answer: message)
            }
            else if (message.type == .expertAssignment) {
                _expertActive = true
                Notifications.notifyExpertFound(self)
            }
            else if (message.type == .expertMessage) {
                Notifications.notifyMessageReceived(self, message: message)
            }
            else {
                unread = 0
            }
            if (unread > 0) {
                self.unread += Int32(unread)
                message.read = false
                self.parent.adjustUnread(1)
                unreadChanged()
            }
        }
        if (message.type == .clientDone) {
            self.flags = self.flags | ExpLeagueOrderFlags.closed.rawValue
        }
        else if (message.type == .clientCancel) {
            if (judged) {
                self.flags = self.flags | ExpLeagueOrderFlags.canceled.rawValue
            }
            else {
                self.flags = self.flags | ExpLeagueOrderFlags.deciding.rawValue
            }
        }
        else if (message.type == .clientMessage) {
            self.flags = self.flags
                        & ~ExpLeagueOrderFlags.deciding.rawValue
                        & ~ExpLeagueOrderFlags.closed.rawValue
                        & ~ExpLeagueOrderFlags.canceled.rawValue
        }
        _status = nil
        messagesChanged()
        parent.ordersChanged()
        save()
    }
    
    public func send(text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)!
        msg.addBody(text)
        msg.addAttribute(withName: "id", stringValue: MyXMPPStream.nextId())
        update {
            self.message(message: msg, notify: false)
        }
        _ = parent.send(msg)
    }
    
    public func send(xml: DDXMLElement, type: String = "normal") {
        let msg = XMPPMessage(type: type, to: jid)!
        msg.addChild(xml)
        msg.addAttribute(withName: "id", stringValue: MyXMPPStream.nextId())
        update {
            self.message(message: msg, notify: false)
        }
        _ = parent.send(msg)
    }
    
    public var jid : XMPPJID {
        return XMPPJID(string: id + "@muc." + ExpLeagueProfile.active.domain)
    }
    
    public var text: String {
        return offer.topic
    }
    
    public func cancel(_ ownerVC: UIViewController? = nil) {
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
    
    public func feedback(stars score: Int, payment: String?) {
        let feedback = DDXMLElement(name: "feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        feedback?.addAttribute(withName: "stars", integerValue: score)
        if let id = payment {
            feedback?.addAttribute(withName: "payment", stringValue: id)
        }
        send(xml: feedback!)
        
        close()
    }

    public func close() {
        send(xml: DDXMLElement(name: "done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME))
    }
    
    public func emulate() {
        self.flags = self.flags | ExpLeagueOrderFlags.closed.rawValue | ExpLeagueOrderFlags.fake.rawValue
        self.flags = self.flags & ~ExpLeagueOrderFlags.archived.rawValue
        _status = nil
        save()
    }
    
    public func markSaved() {
        update {
            self.flags = self.flags | ExpLeagueOrderFlags.saved.rawValue
            self._status = nil
            self.parent.ordersChanged()
        }
    }
    
    public var fake: Bool {
        return (flags & (ExpLeagueOrderFlags.fake.rawValue | ExpLeagueOrderFlags.saved.rawValue) != 0)
    }
    
    public func archive() {
        parent.adjustUnread(-self.unread)
        if (isActive) {
            cancel()
        }
        if (self.flags & ExpLeagueOrderFlags.archived.rawValue == 0) {
            update {
                self.flags |= ExpLeagueOrderFlags.archived.rawValue
                self._status = nil
                self.parent.ordersChanged()
            }
        }
    }
    
    public func continueTask() {
        if (status == .deciding) {
            update {
                self.flags &= ~ExpLeagueOrderFlags.deciding.rawValue
                self._status = nil
            }
        }
    }
    
    fileprivate var _status: ExpLeagueOrderStatus?
    public var status: ExpLeagueOrderStatus {
        guard _status == nil else {
            return _status!
        }
        let lastAnswer = messages.last?.type == .answer && !(messages.last?.isEmpty ?? false)
        if (flags & ExpLeagueOrderFlags.archived.rawValue != 0) {
            _status = .archived
        }
        else if (flags & ExpLeagueOrderFlags.canceled.rawValue != 0) {
            _status = .canceled
        }
        else if ((flags & ExpLeagueOrderFlags.saved.rawValue) == 0 && (flags & ExpLeagueOrderFlags.deciding.rawValue != 0 || fake || lastAnswer)) {
            _status = .deciding
        }
        else if (flags & (ExpLeagueOrderFlags.closed.rawValue | ExpLeagueOrderFlags.saved.rawValue) != 0) {
            _status = .closed
        }
        else if (activeExpert == nil) {
            _status = .expertSearch
        }
        else if (before - NSDate().timeIntervalSince1970 > 0) {
            _status = .open
        }
        else {
            _status = .overtime
        }
        return _status!
    }
    
    fileprivate dynamic var _shortAnswer: String?
    public var shortAnswer: String {
        guard _shortAnswer == nil else {
            return _shortAnswer!
        }
        
        var result: String? = nil
        for answer in messages.filter({msg in msg.type == .answer}) {
            if let shortAnswer = answer.properties["short"] as? String {
                result = shortAnswer
            }
        }
        result = result ?? "Нет простого ответа"
        _shortAnswer = result!
        return result!
    }
    
    public func onMessageRead(message: ExpLeagueMessage) {
        update {
            if (self.unread > 0) {
                self.unread -= 1
                self.parent.adjustUnread(-1)
                self.unreadChanged()
            }
        }
    }
    public func unreadChanged() { QObject.notify(#selector(self.unreadChanged), self) }

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
    public var offer: ExpLeagueOffer {
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

public class ExpLeagueOffer: NSObject {
    let xml: DDXMLElement
    
    public var duration: TimeInterval {
        switch(xml.attributeStringValue(forName: "urgency")) {
        case "day":
            return 24 * 60 * 60
        case "asap":
            return 60 * 60
        default:
            return 7 * 24 * 60 * 60
        }
    }
    
    public var topic: String {
        return xml.forName("topic")!.stringValue!
    }
    
    public var images: [URL] {
        var result: [URL] = []
        for imageElement in xml.elements(forName: "image") {
            result.append(URL(string: imageElement.stringValue!)!)
        }
        return result
    }

    public var experts: [ExpLeagueMember] {
        var result: [ExpLeagueMember] = []
        if let filter = xml.forName("experts-filter") {
            for acceptElement in filter.elements(forName: "accept") {
                if let expert = ExpLeagueProfile.active.expert(login: XMPPJID(string: acceptElement.stringValue).user) {
                    result.append(expert)
                }
            }
        }
        return result
    }

    public var started: Date {
        return Date(timeIntervalSince1970: xml.attributeDoubleValue(forName: "started"))
    }
    
    public var local: Bool {
        return xml.attributeBoolValue(forName: "local")
    }
    
    public var location: CLLocationCoordinate2D? {
        if let location = xml.forName("location", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            return CLLocationCoordinate2DMake(location.attributeDoubleValue(forName: "latitude") , location.attributeDoubleValue(forName: "longitude"))
        }
        return nil
    }
    
    public var room: String {
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
            imageElement.stringValue = "\(ExpLeagueProfile.active.imageStorage)\(img)"
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

public enum ExpLeagueOrderStatus: Int {
    case open = 0
    case closed = 1
    case overtime = 2
    case canceled = 3
    case archived = 4
    case expertSearch = 5
    case deciding = 7
}

public enum ExpLeagueOrderFlags: Int16 {
    case localTask = 16384
    case specificTask = 8196
    case closed = 4096
    case canceled = 2048
    case archived = 1024
    case deciding = 512
    case fake = 4
    case saved = 8
}
