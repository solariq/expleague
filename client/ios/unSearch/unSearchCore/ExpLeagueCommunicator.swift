//
//  Communicator.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 26/05/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import XMPPFramework

public enum ExpLeagueCommunicatorMode: Int {
    case foreground = 1
    case background = 2
}

public enum ExpLeagueCommunicatorStatus: Int {
    case idle = 4
    case connected = 8
    case acquiring = 16
}

public class MyXMPPStream: XMPPStream {
    public override func generateUUID() -> String! {
        return "\(Utils.randString(10))-\(UInt64(floor(Date().timeIntervalSince1970)))"
    }
}

public class ExpLeagueCommunicatorState {
    let owner: ExpLeagueCommunicator?
    public var mode: ExpLeagueCommunicatorMode {
        didSet {
            owner?.stateChanged()
        }
    }
    public var status: ExpLeagueCommunicatorStatus {
        didSet {
            owner?.stateChanged()
        }
    }
    
    init(_ owner: ExpLeagueCommunicator?, mode: ExpLeagueCommunicatorMode, status: ExpLeagueCommunicatorStatus) {
        self.owner = owner
        self.mode = mode
        self.status = status
    }
    
    internal func disconnect() {
        if (status == .connected) {
            status = !owner!.profile.busy && mode == .background ? .idle : .acquiring
        }
    }
    
    internal func connect() {
        if (status == .idle) {
            status = .acquiring
        }
    }
}

internal class ExpLeagueCommunicator: NSObject {
    static let DEBUG = false
    static let xmppQueue = DispatchQueue(label: "ExpLeague XMPP stream", attributes: [])

    // MARK: - *** Public members ***
    var state: ExpLeagueCommunicatorState!
    func stateChanged() {
        QObject.notify(#selector(self.stateChanged), self)
    }
    
    func track(_ tracker: XMPPTracker) {
        listeners.add(Weak(tracker))
    }
    
    func expect(_ id: String) {
        if (ExpLeagueCommunicator.DEBUG) {
            print("Expecting: \(id)")
        }
        self.pending.insert(id)
        if (self.state.status == .idle) {
            self.state.status = .acquiring
        }
        else {
            tick()
        }
    }
    
    func send(_ msg: XMPPMessage) {
        let receiptRequest = DDXMLElement(name: "request", xmlns: "urn:xmpp:receipts")
        msg.addChild(receiptRequest!)
        msg.addAttribute(withName: "id", stringValue: stream!.generateUUID())
        
        thread.async {
            self.profile.enqueue(msg)
            self.queue.append(msg)
            if (self.stream?.isAuthenticated())! {
                if (ExpLeagueCommunicator.DEBUG) {
                    print("< \(msg.xmlString)")
                }
                self.stream?.send(msg)
            }
        }
    }

    func requestAOW() {
        let aowIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        aowIq?.addAttribute(withName: "type", stringValue: "get")
        let query = DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/best-answer")!
        query.addAttribute(withName: "lastKnown", stringValue: profile.aowId ?? "")
        query.addAttribute(withName: "received", boolValue: !(profile.receiveAnswerOfTheWeek?.boolValue ?? false))
        aowIq?.addChild(query)
        stream?.send(aowIq)
    }

    
    // MARK: - *** Private stuff ***
    fileprivate var listeners: NSMutableArray = []
    fileprivate let profile: ExpLeagueProfile
    fileprivate let stream = MyXMPPStream()
    fileprivate var thread: DispatchQueue {
        return ExpLeagueCommunicator.xmppQueue
    }
    
    fileprivate var queue: [XMPPMessage] = []
    internal var pending = Set<String>()
    
    fileprivate var timer: Timer?
    fileprivate var connectionAttempts = 0
    @objc
    fileprivate func tick() {
        timer?.invalidate()
        timer = nil
        if (state.status == .connected) {
            connectionAttempts = 0
        }
        switch state.status {
        case .acquiring where connectionAttempts > 30 && state.mode == .background,
             .connected where !profile.busy && state.mode == .background:
//            if (ExpLeagueCommunicator.DEBUG) {
                print("Disconnecting")
//            }
            stream?.disconnect()
            
        case .idle where state.mode == .foreground, .acquiring:
            connectionAttempts += 1
            guard !(stream?.isAuthenticated())! else {
                break
            }
            
            guard (!(stream?.isConnecting())! && !(stream?.isAuthenticating())!) else {
                if (connectionAttempts + 1) % 10 == 0 {
                    stream?.disconnect()
                }

                break
            }
//            if (ExpLeagueCommunicator.DEBUG) {
                print("Connecting")
//            }
            do {
                try stream?.connect(withTimeout: XMPPStreamTimeoutNone)
            }
            catch {
                profile.log("Unable to start the stream: \(error)")
            }
        default:
            break
        }
        
        if (state.status == .acquiring) {
            DispatchQueue.main.async {
                self.timer = Timer.scheduledTimer(timeInterval: 10, target: self, selector: #selector(self.tick), userInfo: nil, repeats: false)
            }
        }
//        if (ExpLeagueCommunicator.DEBUG) {
            print("Communication tick. incoming: \(pending.count), outgoing: \(queue.count), state: (\(state.mode), \(state.status)), profile busy: \(profile.busy)")
//        }
    }
    
    fileprivate var requested: [String] = []
    fileprivate var dumpRequestId: String = ""

    // MARK: - *** Life cycle ***
    internal init(profile: ExpLeagueProfile) {
        self.profile = profile
        stream?.hostName = profile.domain
        stream?.hostPort = profile.port.uint16Value
        stream?.myJID = XMPPJID(string: profile.login + "@" + profile.domain + "/unSearch")
        stream?.startTLSPolicy = XMPPStreamStartTLSPolicy.required
        stream?.keepAliveInterval = 30
        stream?.enableBackgroundingOnSocket = true
        super.init()
        stream?.addDelegate(self, delegateQueue: thread)
        state = ExpLeagueCommunicatorState(self, mode: .background, status: .idle)
        profile.visitQueue({msg in self.queue.append(msg)})
//        self.pending.appendContentsOf(profile.pending)
        if (profile.busy) {
            state.status = .acquiring
        }
        QObject.connect(self, signal: #selector(self.stateChanged), receiver: self, slot: #selector(self.tick))
    }
    
    fileprivate func notify(_ proc: @escaping (XMPPTracker)->()) {
        for listenerRef in listeners.copy() as! NSArray {
            if let listener = (listenerRef as! Weak<XMPPTracker>).value {
                DispatchQueue.main.async {
                    proc(listener)
                }
            }
            else {
                listeners.remove(listenerRef)
            }
        }
    }
    
    deinit {
        timer?.invalidate()
    }
}

extension ExpLeagueCommunicator: XMPPStreamDelegate {
    @objc
    func xmppStreamDidConnect(_ sender: XMPPStream!) {
        profile.log("Connected")
        do {
            try sender.authenticate(withPassword: profile.passwd);
        }
        catch {
            profile.log("Failed to authenticate \(error)")
        }
    }
    @objc
    func xmppStreamConnectDidTimeout(_ sender: XMPPStream!) {
        profile.log("Connection timeout");
        state.disconnect()
    }
    
    @objc
    func xmppStreamDidDisconnect(_ sender: XMPPStream!, withError error: Error!) {
        profile.log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""))
        state.disconnect()
    }
    
    @objc
    func xmppStream(_ sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
    }
    
    @objc
    func xmppStream(_ sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elements(forName: "text");
        if (texts.count > 0) {
            let txt = texts[0]
            let text = txt.stringValue ?? ""
            if ("No such user" == text) {
                do {
                    profile.log("No such user, trying to register a new one.")
                    var props: [DDXMLElement] = []
                    let system = Bundle.main.infoDictionary!
                    props.append(DDXMLElement(name: "username", stringValue: profile.jid.user))
                    props.append(DDXMLElement(name: "password", stringValue: profile.passwd))
                    props.append(DDXMLElement(name: "email", stringValue: "\(ProcessInfo.processInfo.operatingSystemVersionString)/\(system["CFBundleIdentifier"]!)/\(system["CFBundleVersion"]!))"))
                    
                    try sender.register(withPassword: profile.passwd)
                }
                catch {
                    profile.log("Failed to authenticate \(error)")
                }
                return
            }
        }
        state.disconnect()
        profile.log("Unable to log in: \(error)")
    }
    
    @objc
    func xmppStreamDidRegister(_ sender: XMPPStream!) {
        profile.log("The new user has been registered! Restarting the xmpp stream.")
        sender.disconnect()
        tick()
    }
    
    @objc
    func xmppStreamDidAuthenticate(_ sender: XMPPStream!) {
        connectionAttempts = 0
        state.status = .connected
        profile._jid = sender.myJID
        
        // updating client version and token
        if (DataController.shared().token != nil && DataController.shared().version != nil) {
            let msg = XMPPMessage(type: "normal")
            let token = DDXMLElement(name: "token", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        
            token?.stringValue = DataController.shared().token
            token?.addAttribute(withName: "client", stringValue: DataController.shared().version!)
            msg?.addChild(token!)
            sender.send(msg)
        }

        // sending not confirmed messages
        for item in queue {
            if (item.attribute(forName: "id") != nil) {
                sender.send(item)
            }
        }
        
        // getting latest experts
        let rosterIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        rosterIq?.addAttribute(withName: "type", stringValue: "get")
        rosterIq?.addChild(DDXMLElement(name: "query", xmlns: "jabber:iq:roster"))
        sender.send(rosterIq)
        
        // updating tags and patterns
        let tagsIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        tagsIq?.addAttribute(withName: "type", stringValue: "get")
        tagsIq?.addChild(DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/tags"))
        sender.send(tagsIq)
        
        let patternsIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        patternsIq?.addAttribute(withName: "type", stringValue: "get")
        patternsIq?.addChild(DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/patterns"))
        sender.send(patternsIq)
        
        // restore rooms if needed
        let restore = DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/restore")
        stream?.send(XMPPIQ(type: "get", child: restore))

        // answer of the week
        requestAOW()
    }
    
    @objc
    public func xmppStream(_ sender: XMPPStream!, didReceive trust: SecTrust!, completionHandler: ((Bool) -> Swift.Void)!) {
        completionHandler(true)
    }
    
    
    func xmppStream(_ sender: XMPPStream!, didReceive iq: XMPPIQ!) -> Bool {
        if (ExpLeagueCommunicator.DEBUG) {
            print(iq)
        }
        if let query = iq.forName("query", xmlns: "jabber:iq:roster") {
            for item in query.elements(forName: "item") {
                let groupStr: String = item.elements(forName: "group").count > 0 ? (item.forName("group")?.stringValue!)! : ""
                let group: ExpLeagueMemberGroup
                switch groupStr {
                case "Favorites":
                    group = .favorites
                default:
                    group = .top
                }
                if let profile = item.forName("expert", xmlns: "http://expleague.com/scheme") {
                    let expert: ExpLeagueMember
                    if let existing = self.profile.expert(login: profile.attributeStringValue(forName: "login")) {
                        existing.updateXml(profile)
                        expert = existing
                    }
                    else {
                        expert = ExpLeagueMember(xml: profile, group: group, context: self.profile.managedObjectContext!)
                        _ = self.profile.register(expert: expert)
                    }
                    expert.group = group
                    expert.available = profile.attributeBoolValue(forName: "available")
                }
            }
        }
        else if let query = iq.forName("query", xmlns: "http://expleague.com/scheme/tags") {
            for tag in query.elements(forName: "tag") {
                let name = tag.stringValue
                let icon = tag.attributeStringValue(forName: "icon", withDefaultValue: "named://search_icon")!
                let tag: ExpLeagueTag
                if let existing = profile.tag(name: name!) {
                    existing.updateIcon(icon)
                }
                else {
                    tag = ExpLeagueTag(name: name!, icon: icon, type: .tag, context: profile.managedObjectContext!)
                    _ = profile.register(tag: tag)
                }
            }
        }
        else if let query = iq.forName("query", xmlns: "http://expleague.com/scheme/patterns") {
            for tag in query.elements(forName: "pattern") {
                let name = tag.attributeStringValue(forName: "name")
                let icons = tag.elements(forName: "icon") 
                let tag: ExpLeagueTag
                if let existing = profile.tag(name: name!) {
                    if (icons.count > 0) {
                        existing.updateIcon(icons[0].stringValue!)
                    }
                }
                else {
                    let icon = icons.count > 0 ? icons[0].stringValue : "named://search_icon"
                    tag = ExpLeagueTag(name: name!, icon: icon!, type: .pattern, context: profile.managedObjectContext!)
                    _ = profile.register(tag: tag)
                }
            }
        }
        else if let query = iq.forName("query", xmlns: "http://expleague.com/scheme/best-answer"), !iq.isErrorIQ() {
            guard let offerXml = query.forName("offer", xmlns: "http://expleague.com/scheme") else { // empty answer
                return false
            }
            let offer = ExpLeagueOffer(xml: offerXml)
            var order = ExpLeagueOrder(offer.room, offer: offer, context: profile.managedObjectContext!)
            order = profile.add(aow: order)
            let content = query.forName("content", xmlns: "http://expleague.com/scheme/best-answer")
            for item in (content?.elements(forName: "message"))! {
                let message = XMPPMessage(from: item)!
                order.message(message: message, notify: false)
            }
        }
        else if let query = iq.forName("query", xmlns: "http://expleague.com/scheme/dump-room") {
            let offer = ExpLeagueOffer(xml: query.forName("offer", xmlns: "http://expleague.com/scheme")!)
            var order = ExpLeagueOrder(offer.room, offer: offer, context: profile.managedObjectContext!)
            order = profile.add(order: order)
            let content = query.forName("content", xmlns: "http://expleague.com/scheme/dump-room")
            for item in (content?.elements(forName: "message"))! {
                let message = XMPPMessage(from: item)!
                order.message(message: message, notify: false)
            }
            let restore = DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/restore")
            stream?.send(XMPPIQ(type: "get", child: restore))
        }
        else if let query = iq.forName("query", xmlns: "http://expleague.com/scheme/restore"), !iq.isErrorIQ() {
            for room in query.elements(forName: "room") {
                let roomId = (room ).stringValue
                guard self.profile.order(name: roomId!) == nil && !requested.contains(roomId!) else {
                    continue
                }
                requested.append(roomId!)
                let dumpRequest = DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/dump-room")
                dumpRequest?.addAttribute(withName: "room", stringValue: roomId!)
                let iq = XMPPIQ(type: "get", child: dumpRequest)!
                iq.addAttribute(withName: "id", stringValue: "restore-" + String(requested.count))
                stream?.send(iq)
                dumpRequestId = iq.elementID()
                break // one at a time
            }
        }
        else if iq.elementID() == dumpRequestId {
            let restore = DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/restore")
            stream?.send(XMPPIQ(type: "get", child: restore))
        }
        return false
    }
    
    func xmppStream(_ sender: XMPPStream!, didReceive msg: XMPPMessage!) {
        if (ExpLeagueCommunicator.DEBUG) {
            print(msg)
        }
        var delivered: [XMPPMessage] = []
        while let receipt = msg.forName("received", xmlns: "urn:xmpp:receipts") {
            let id = receipt.attributeStringValue(forName: "id")
            if let msg = self.queue.filter({$0.elementID() == id}).first {
                delivered.append(msg)
            }
            msg.removeChild(at: receipt.index)
        }
        var receipt: DDXMLElement?
        if let receiptRequest = msg.forName("request", xmlns: "urn:xmpp:receipts") {
            msg.removeChild(at: receiptRequest.index)
            receipt = DDXMLElement(name: "received", xmlns: "urn:xmpp:receipts")
            receipt!.addAttribute(withName: "id", stringValue: msg.elementID())
        }
        if let from = msg.from(), let order = profile.order(name: from.user) {
            order.message(message: msg, notify: true)
        }
        notify { listener in
            listener.onMessage?(msg)
        }
        if (receipt != nil) {
            sender.send(XMPPMessage(type: "normal", child: receipt))
        }
        if ((pending.remove(msg.elementID())) != nil) {
            profile.delivered(incoming: msg)
        }
        delivered.forEach{ msg in
            _ = queue.removeOne(msg)
            profile.delivered(outgoing: msg)
        }
    }
    
    func xmppStream(_ sender: XMPPStream!, didReceive presence: XMPPPresence!) {
        if (ExpLeagueCommunicator.DEBUG) {
            print(presence)
        }
        if let from = presence.from(), let user = from.user, let expert = profile.experts.filter({$0.id.user == user}).first {
            expert.available = presence.type() == "available"
        }
        
        notify { listener in
            listener.onPresence?(presence)
        }
    }
}
