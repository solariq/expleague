//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import XMPPFramework
import UIKit
import JSQMessagesViewController

class ELOrder {
    let roomId: String
    let started: NSDate
    let topic: String, urgency: String, local: Bool, expert: Bool
    var messages: [XMPPMessage] = [];

    init(_ roomId: String, topic: String, urgency: String, local: Bool, expert: Bool) {
        self.started = NSDate();
        self.roomId = roomId.lowercaseString
        self.topic = topic
        self.urgency = urgency
        self.local = local
        self.expert = expert
    }

    func id() -> String {
        return roomId
    }

    func jid(host: String) -> XMPPJID {
        return XMPPJID.jidWithString(roomId + "@muc." + host, resource: "/client")
    }

    func iq(iq: XMPPIQ) {
        print("\(roomId)> \(iq)")
    }

    func presence(presence: XMPPPresence) {
        print("\(roomId)> \(presence)")
    }

    func message(msg: XMPPMessage) {
        if (msg.elementsForName("body").count > 0 && msg.body().containsString("Welcome to room \(roomId)")) { // initial message
            let setup = XMPPMessage(type: "groupchat", to: jid(stream.hostName))
            setup.addSubject(topic)
            stream.sendElement(setup)
            message(setup)
        }
        else {
            if (msg.attributesAsDictionary()["time"] == nil) {
                let dateFormatter = NSDateFormatter()
                dateFormatter.dateStyle = .ShortStyle
                dateFormatter.timeStyle = .LongStyle

                msg.addAttributeWithName("time", stringValue: dateFormatter.stringFromDate(NSDate()))
            }
            messages.append(msg)
            if (self === ELConnection.instance.orderSelected) {
                ELConnection.instance.selectedChangedCallbacks.forEach({ $0(msg: MyJSQMessage(msg: msg)) })
            }
        }
    }
    
    func send(msg: String) {
        let message = XMPPMessage(type: "groupchat", to: jid(stream.hostName))
        message.addBody(msg)
        stream.sendElement(message)
        self.message(message)
    }
    
    var stream : XMPPStream {
        get {
            return ELConnection.instance.stream;
        }
    }
    
    func size() -> Int {
        return messages.count
    }
    
    func messageAsJSQ(index: Int) -> MyJSQMessage {
        return MyJSQMessage(msg: messages[index])
    }
}

class MyJSQMessage: NSObject, JSQMessageData {
    let delegate: XMPPMessage
    init(msg: XMPPMessage) {
        delegate = msg;
    }

    var incoming: Bool {
        get {
            return from != "me"
        }
    }

    var from: String {
        get {
            return delegate.attributesAsDictionary()["from"] != nil ? delegate.from().resource : "me"
        }
    }

    var avatar: JSQMessagesAvatarImage {
        get {
            return ELConnection.instance.avatar(from)
        }
    }

    func senderId() -> String! {
        return incoming ? "me" : from
    }

    func senderDisplayName() -> String! {
        return incoming ? "Я" : from
    }

    func date() -> NSDate! {
        let dateFormatter = NSDateFormatter()
        dateFormatter.dateStyle = .ShortStyle
        dateFormatter.timeStyle = .LongStyle

        return dateFormatter.dateFromString(delegate.attributeStringValueForName("time"))
    }

    func isMediaMessage() -> Bool {
        return false
    }

    func messageHash() -> UInt {
        return UInt(delegate.hash)
    }

    func text() -> String! {
        var textChildren = delegate.elementsForName("subject")
        if (textChildren.count == 0) {
            textChildren = delegate.elementsForName("body")
        }
        return textChildren.count > 0 ? textChildren[0].stringValue : ""
    }
}

class ELConnection: NSObject {
    static let instance = ELConnection()
    
    let stream = XMPPStream()
    var settings = SettingsSet.active()
    var orders : [String: ELOrder] = [:]
    var avatars : [String: JSQMessagesAvatarImage] = [:]
    var orderSelected: ELOrder = ELOrder("empty", topic: "Заказ не выбран", urgency: "late", local: true, expert: true)
    
    override init() {
        super.init();
        stream.addDelegate(self, delegateQueue: dispatch_get_main_queue())
        reset(SettingsSet.active())
    }

    func reset(settings: SettingsSet) {
        if (settings != self.settings) {
            if (stream.isConnected() || stream.isConnecting()) {
                stream.disconnect()
            }
            self.settings = settings;
            start()
        }
    }

    func isConnected() -> Bool {
        return stream.isConnected()
    }

    func placeOrder(topic topic: String, urgency: String, local: Bool, prof: Bool) -> ELOrder {
        var rand = NSUUID().UUIDString;
        rand = rand.substringToIndex(rand.startIndex.advancedBy(8))
        let order = ELOrder("room-" + settings.user() + "-" + rand, topic: topic, urgency: urgency, local: local, expert: prof);
        let presence = XMPPPresence(type: "available", to: order.jid(settings.host()));
        stream.sendElement(presence)
        orders[order.id()] = order
        changeListeners.forEach({$0()})
        orderSelected = order
        return order;
    }
    
    var incomingAvaWidth : UInt = 100;
    var outgoingAvaWidth : UInt = 100;
    
    
    func avatar(name: String) -> JSQMessagesAvatarImage {
        if let avatar = avatars[name] {
            return avatar
        }
        let diameter = name == "me" ? incomingAvaWidth : outgoingAvaWidth
        
        let rgbValue = name.hash
        let r = CGFloat(Float((rgbValue & 0xFF0000) >> 16)/255.0)
        let g = CGFloat(Float((rgbValue & 0xFF00) >> 8)/255.0)
        let b = CGFloat(Float(rgbValue & 0xFF)/255.0)
        let color = UIColor(red: r, green: g, blue: b, alpha: 0.5)
        
        let nameLength = name.characters.count
        let initials : String? = name.substringToIndex(name.startIndex.advancedBy(min(3, nameLength)))
        let userImage = JSQMessagesAvatarImageFactory.avatarImageWithUserInitials(initials!, backgroundColor: color, textColor: UIColor.blackColor(), font: UIFont.systemFontOfSize(CGFloat(13)), diameter: diameter)
        
        avatars[name] = userImage;
        return userImage
    }

    internal func start() {
        if (stream.isConnected() || stream.isConnecting()) {
            return
        }

        let host = settings.host()
        stream.hostName = host;
        stream.hostPort = settings.port()
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.keepAliveInterval = 30
        stream.enableBackgroundingOnSocket = true
        stream.myJID = XMPPJID.jidWithString(settings.user() + "@" + host);
        do {
            try stream.connectWithTimeout(XMPPStreamTimeoutNone);
        }
        catch {
            log("\(error)");
        }
    }

    internal func stop() {
        if (stream.isConnected()) {
            stream.disconnect()
        }
    }
    
    var changeListeners: [() -> ()] = []
    func onOrderCreate(callback: () -> ()) {
        changeListeners.append(callback)
    }

    var selectedChangedCallbacks: [(msg: MyJSQMessage) -> ()] = []

    func onSelectedChange(callback: (msg: MyJSQMessage) -> ()) {
        selectedChangedCallbacks.append(callback);
    }
}

extension ELConnection: XMPPStreamDelegate {
    @objc
    func xmppStreamDidConnect(sender: XMPPStream!) {
        log("Connected")
        do {
            let passwd = settings.passwd()
            try sender.authenticateWithPassword(passwd);
        }
        catch {
            log(String(error))
        }
    }

    @objc
    func xmppStreamConnectDidTimeout(sender: XMPPStream!) {
        log("Timedout");
    }

    @objc
    func xmppStreamDidDisconnect(sender: XMPPStream!, withError error: NSError!) {
        log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""));
    }

    @objc
    func xmppStreamDidStartNegotiation(sender: XMPPStream!) {
        log("Starting negotiations")
    }

    @objc
    func xmppStream(sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        log("Socket opened");
    }

    @objc
    func xmppStream(sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        log("Configuring");
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
    }

    @objc
    func xmppStream(sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elementsForName("text");
        if (texts.count > 0) {
            if let txt = texts[0] as? DDXMLElement {
                let text = txt.stringValue()
                if ("No such user" == String(text)) {
                    do {
                        log("No such user, trying to register a new one.")
                        try sender.registerWithPassword(settings.passwd())
                    }
                    catch {
                        log("\(error)")
                    }
                    return
                }
            }
        }
        log("Not authenticate \(error)")
    }

    @objc
    func xmppStreamDidRegister(sender: XMPPStream!) {
        log("The new user has been registered! Restarting the xmpp stream.")
        do {
            sender.disconnect()
            try sender.connectWithTimeout(XMPPStreamTimeoutNone)
        }
        catch {
            log(String(error))
        }
    }


    @objc
    func xmppStreamDidAuthenticate(sender: XMPPStream!) {
        log("Success!");
    }

    @objc
    func xmppStream(sender: XMPPStream!, didReceiveTrust trust: SecTrustRef, completionHandler: (Bool) -> ()) {
        completionHandler(true)
    }

    func xmppStream(sender: XMPPStream!, didReceiveIQ iq: XMPPIQ!) -> Bool {
        if let order = orders[iq.from().user] {
            order.iq(iq)
        }
        return false
    }

    func xmppStream(sender: XMPPStream!, didReceiveMessage msg: XMPPMessage!) {
        if let order = orders[msg.from().user] {
            order.message(msg)
        }
    }

    func xmppStream(sender: XMPPStream!, didReceivePresence presence: XMPPPresence!) {
        if let user = presence.from().user, let order = orders[user] {
            order.presence(presence)
        }
    }
    
    func log(msg: String) {
        let dateFormatter = NSDateFormatter()
        dateFormatter.dateStyle = .ShortStyle
        dateFormatter.timeStyle = .LongStyle
        
        print(dateFormatter.stringFromDate(NSDate()) + ": " + msg)
    }
}

final class SettingsSet : NSObject {
    static let MY_KEY = "unSearch.settings"
    static let defaultHosts = ["expleague.com", "test.expleague.com", "localhost"]
    var profile: Int = 0

    var h: String = "localhost"
    var u: String?
    var p: String?

    init(_ profile: Int, _ host: String) {
        self.profile = Int(profile)
        self.h = host
    }

    func host() -> String {
        let parts = h.componentsSeparatedByString(":") as [String];
        if parts.count > 1 {
            return parts[0]
        }
        return h;
    }

    func port() -> UInt16 {
        let parts = h.componentsSeparatedByString(":") as [String];
        if parts.count > 1 {
            return UInt16(parts[1])!
        }
        return 5222;
    }

    func user() -> String {
        if u == nil {
            //                CKContainer.defaultContainer().fetchUserRecordIDWithCompletionHandler({
            //                    (profile: CKRecordID?, error: NSError?) -> Void in
            //                })
            let randString = NSUUID().UUIDString
            self.u = randString.substringToIndex(randString.startIndex.advancedBy(8))

            save()
        }
        return u!;
    }

    func passwd() -> String {
        if p == nil {
            p = NSUUID().UUIDString
            save();
        }
        return p!
    }

    func save() {
        let data = NSKeyedArchiver.archivedDataWithRootObject(self)
        NSUserDefaults.standardUserDefaults().setObject(data, forKey: "\(SettingsSet.MY_KEY)/\(profile)")
    }

    class func load(profile: Int) -> SettingsSet {
        NSUserDefaults.standardUserDefaults().setInteger(profile, forKey: "\(SettingsSet.MY_KEY)/selected")
        if let known = NSUserDefaults.standardUserDefaults().valueForKey("\(MY_KEY)/\(profile)") {
            return NSKeyedUnarchiver.unarchiveObjectWithData(known as! NSData) as! SettingsSet;
        }
        return SettingsSet(profile, defaultHosts[profile]);
    }

    class func active() -> SettingsSet {
        if let active = NSUserDefaults.standardUserDefaults().valueForKey("\(SettingsSet.MY_KEY)/selected") as? Int {
            return load(active)
        }
        return load(0)
    }
}

extension SettingsSet: NSCoding {
    @objc convenience init(coder: NSCoder) {
        self.init(coder.decodeIntegerForKey("profile"), coder.decodeObjectForKey("host") as! String)
        u = coder.decodeObjectForKey("user") as! String?
        p = coder.decodeObjectForKey("passwd") as! String?
    }

    @objc func encodeWithCoder(coder: NSCoder) {
        coder.encodeInteger(profile, forKey: "profile")
        coder.encodeObject(h, forKey: "host")
        coder.encodeObject(u, forKey: "user")
        coder.encodeObject(p, forKey: "passwd")
    }
}

//extension SettingsSet: Equatable {}

// MARK: Equatable

func ==(lhs: SettingsSet, rhs: SettingsSet) -> Bool {
    return lhs.profile == rhs.profile && lhs.h == rhs.h && lhs.u == rhs.u && lhs.p == rhs.p
}
