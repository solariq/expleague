//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import XMPPFramework
import UIKit

class ELOrder {
    let roomId: String;
    let topic: String, urgency: String, local: Bool, expert: Bool

    init(_ roomId: String, topic: String, urgency: String, local: Bool, expert: Bool) {
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
        print("\(roomId)> \(msg)")
    }
}

class ELConnection: NSObject {
    static let instance = ELConnection()
    
    let stream = XMPPStream()
    var settings = SettingsSet(0, "")
    var orders : [String: ELOrder] = [:]

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
        return order;
    }

    private func start() {
        let host = settings.host()
        stream.hostName = host;
        stream.hostPort = settings.port()
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.myJID = XMPPJID.jidWithString(settings.user() + "@" + host);
        do {
            try stream.connectWithTimeout(XMPPStreamTimeoutNone);
        }
        catch {
            print(error);
        }
    }
}

extension ELConnection: XMPPStreamDelegate {
    @objc
    func xmppStreamDidConnect(sender: XMPPStream!) {
        print("Connected")
        do {
            let passwd = settings.passwd()
            try sender.authenticateWithPassword(passwd);
        }
        catch {
            print(String(error))
        }
    }

    @objc
    func xmppStreamConnectDidTimeout(sender: XMPPStream!) {
        print("Timedout");
    }

    @objc
    func xmppStreamDidDisconnect(sender: XMPPStream!, withError error: NSError!) {
        print("Disconnected" + (error != nil ? " with error:\n\(error)" : ""));
    }

    @objc
    func xmppStreamDidStartNegotiation(sender: XMPPStream!) {
        print("Starting negotiations")
    }

    @objc
    func xmppStream(sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        print("Socket opened");
    }

    @objc
    func xmppStream(sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        print("Configuring");
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
//        settings.setValue(true, forKey: String(kCFStreamSSLValidatesCertificateChain))
//        settings.setValue(<#T##value: AnyObject?##AnyObject?#>, forKey: <#T##String#>)
    }

    @objc
    func xmppStream(sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elementsForName("text");
        if (texts.count > 0) {
            if let txt = texts[0] as? DDXMLElement {
                let text = txt.stringValue()
                if ("No such user" == String(text)) {
                    do {
                        print("No such user, trying to register a new one.")
                        try sender.registerWithPassword(settings.passwd())
                    }
                    catch {
                        print("\(error)")
                    }
                    return
                }
            }
        }
        print("Not authenticate \(error)")
    }

    @objc
    func xmppStreamDidRegister(sender: XMPPStream!) {
        print("The new user has been registered! Restarting the xmpp stream.")
        do {
            sender.disconnect()
            try sender.connectWithTimeout(XMPPStreamTimeoutNone)
        }
        catch {
            print(String(error))
        }
    }


    @objc
    func xmppStreamDidAuthenticate(sender: XMPPStream!) {
        print("Success!");
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
        if let order = orders[presence.from().user] {
            order.presence(presence)
        }
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
