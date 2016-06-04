//
//  Communicator.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 26/05/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import XMPPFramework

enum ExpLeagueCommunicatorMode: Int {
    case Foreground = 1
    case Background = 2
}

enum ExpLeagueCommunicatorStatus: Int {
    case Idle = 4
    case Connected = 8
    case Acquiring = 16
}

class ExpLeagueCommunicatorState {
    let owner: ExpLeagueCommunicator?
    var mode: ExpLeagueCommunicatorMode {
        didSet {
            owner?.stateChanged()
        }
    }
    var status: ExpLeagueCommunicatorStatus {
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
        if (status == .Connected) {
            status = !owner!.profile.busy && mode == .Background ? .Idle : .Acquiring
        }
    }
    
    internal func connect() {
        if (status == .Idle) {
            status = .Acquiring
        }
    }
}

internal class ExpLeagueCommunicator: NSObject {
    static let DEBUG = false
    // MARK: - *** Public members ***
    var state: ExpLeagueCommunicatorState!
    func stateChanged() {
        QObject.notify(#selector(self.stateChanged), self)
    }
    
    func track(tracker: XMPPTracker) {
        listeners.addObject(Weak(tracker))
    }
    
    func expect(id: String) {
        if (ExpLeagueCommunicator.DEBUG) {
            print("Expecting: \(id)")
        }
        self.pending.append(id)
        if (self.state.status == .Idle) {
            self.state.status = .Acquiring
        }
        else {
            tick()
        }
    }
    
    func send(msg: XMPPMessage) {
        let receiptRequest = DDXMLElement(name: "request", xmlns: "urn:xmpp:receipts")
        let msgId = stream.generateUUID()
        msg.addAttributeWithName("id", stringValue: msgId)
        msg.addChild(receiptRequest)
        
        dispatch_async(thread) {
            self.profile.enqueue(msg)
            self.queue.append(msg)
            if (self.stream.isAuthenticated()) {
                if (ExpLeagueCommunicator.DEBUG) {
                    print("< \(msg.XMLString())")
                }
                self.stream.sendElement(msg)
            }
        }
    }
    
    // MARK: - *** Private stuff ***
    private var listeners: NSMutableArray = []
    private let profile: ExpLeagueProfile
    private let stream = XMPPStream()
    private var thread: dispatch_queue_t {
        return AppDelegate.instance.xmppQueue
    }
    
    private var queue: [XMPPMessage] = []
    internal var pending: [String] = []
    
    private var timer: NSTimer?
    private var connectionAttempts = 0
    @objc
    private func tick() {
        timer?.invalidate()
        timer = nil
        if (state.status == .Connected) {
            connectionAttempts = 0
        }
        switch state.status {
        case .Acquiring where connectionAttempts > 30 && state.mode == .Background,
             .Connected where !profile.busy && state.mode == .Background:
            if (ExpLeagueCommunicator.DEBUG) {
                print("Disconnecting")
            }
            stream.disconnect()
            
        case .Idle where state.mode == .Foreground, .Acquiring:
            connectionAttempts += 1
            guard !stream.isAuthenticated() else {
                break
            }
            
            guard (!stream.isConnecting() && !stream.isAuthenticating()) else {
                if (connectionAttempts + 1) % 10 == 0 {
                    stream.disconnect()
                }

                break
            }
            if (ExpLeagueCommunicator.DEBUG) {
                print("Connecting")
            }
            do {
                try stream.connectWithTimeout(XMPPStreamTimeoutNone)
            }
            catch {
                profile.log("Unable to start the stream: \(error)")
            }
        default:
            break
        }
        
        if (state.status == .Acquiring) {
            dispatch_async(dispatch_get_main_queue()) {
                self.timer = NSTimer.scheduledTimerWithTimeInterval(10, target: self, selector: #selector(self.tick), userInfo: nil, repeats: false)
            }
        }
//        if (ExpLeagueCommunicator.DEBUG) {
            print("Communication tick. incoming: \(pending.count), outgoing: \(queue.count), state: (\(state.mode), \(state.status))")
//        }
    }
    
    // MARK: - *** Life cycle ***
    internal init(profile: ExpLeagueProfile) {
        self.profile = profile
        stream.hostName = profile.domain
        stream.hostPort = profile.port.unsignedShortValue
        stream.myJID = profile.jid
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.keepAliveInterval = 30
//        stream.enableBackgroundingOnSocket = true
        super.init()
        stream.addDelegate(self, delegateQueue: thread)
        state = ExpLeagueCommunicatorState(self, mode: .Background, status: .Idle)
        profile.visitQueue({msg in self.queue.append(msg)})
//        self.pending.appendContentsOf(profile.pending)
        if (profile.busy) {
            state.status = .Acquiring
        }
        QObject.connect(self, signal: #selector(self.stateChanged), receiver: self, slot: #selector(self.tick))
    }

    private func notify(proc: (XMPPTracker)->()) {
        for listenerRef in listeners.copy() as! NSArray {
            if let listener = (listenerRef as! Weak<XMPPTracker>).value {
                dispatch_async(dispatch_get_main_queue()) {
                    proc(listener)
                }
            }
            else {
                listeners.removeObject(listenerRef)
            }
        }
    }
    
    deinit {
        timer?.invalidate()
    }
}

extension ExpLeagueCommunicator: XMPPStreamDelegate {
    @objc
    func xmppStreamDidConnect(sender: XMPPStream!) {
        profile.log("Connected")
        do {
            try sender.authenticateWithPassword(profile.passwd);
        }
        catch {
            profile.log("Failed to authenticate \(error)")
        }
    }
    
    @objc
    func xmppStreamConnectDidTimeout(sender: XMPPStream!) {
        profile.log("Connection timeout");
        state.disconnect()
    }
    
    @objc
    func xmppStreamDidDisconnect(sender: XMPPStream!, withError error: NSError!) {
        profile.log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""))
        state.disconnect()
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
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
                        profile.log("No such user, trying to register a new one.")
                        var props: [DDXMLElement] = []
                        let system = NSBundle.mainBundle().infoDictionary!
                        props.append(DDXMLElement(name: "username", stringValue: profile.jid.user))
                        props.append(DDXMLElement(name: "password", stringValue: profile.passwd))
                        props.append(DDXMLElement(name: "email", stringValue: "\(NSProcessInfo.processInfo().operatingSystemVersionString)/\(system["CFBundleIdentifier"]!)/\(system["CFBundleVersion"])"))
                        
                        try sender.registerWithPassword(profile.passwd)
                    }
                    catch {
                        profile.log("Failed to authenticate \(error)")
                    }
                    return
                }
            }
        }
        state.disconnect()
        profile.log("Unable to log in: \(error)")
    }
    
    @objc
    func xmppStreamDidRegister(sender: XMPPStream!) {
        profile.log("The new user has been registered! Restarting the xmpp stream.")
        sender.disconnect()
        tick()
    }
    
    @objc
    func xmppStreamDidAuthenticate(sender: XMPPStream!) {
        connectionAttempts = 0
        state.status = .Connected
        profile._jid = sender.myJID
        
        // updating client version and token
        let msg = XMPPMessage(type: "normal")
        let token = DDXMLElement(name: "token", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        if (AppDelegate.instance.token != nil) {
            token.setStringValue(AppDelegate.instance.token)
        }
        token.addAttributeWithName("client", stringValue: "unSearch " + AppDelegate.versionName() + " @iOS")
        msg.addChild(token)
        sender.sendElement(msg)

        // sending not confirmed messages
        for item in queue  {
            sender.sendElement(item)
        }
        
        // getting latest experts
        let rosterIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        rosterIq.addAttributeWithName("type", stringValue: "get")
        rosterIq.addChild(DDXMLElement(name: "query", xmlns: "jabber:iq:roster"))
        sender.sendElement(rosterIq)
        
        // updating tags and patterns
        let tagsIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        tagsIq.addAttributeWithName("type", stringValue: "get")
        tagsIq.addChild(DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/tags"))
        sender.sendElement(tagsIq)
        
        let patternsIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
        patternsIq.addAttributeWithName("type", stringValue: "get")
        patternsIq.addChild(DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/patterns"))
        sender.sendElement(patternsIq)
        
        if (profile.receiveAnswerOfTheWeek?.boolValue ?? true) {
            // answer of the week
            let aowIq = DDXMLElement(name: "iq", xmlns: "jabber:client")
            aowIq.addAttributeWithName("type", stringValue: "get")
            aowIq.addChild(DDXMLElement(name: "query", xmlns: "http://expleague.com/scheme/best-answer"))
            sender.sendElement(aowIq)
        }
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, didReceiveTrust trust: SecTrustRef, completionHandler: (Bool) -> ()) {
        completionHandler(true)
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveIQ iq: XMPPIQ!) -> Bool {
        if (ExpLeagueCommunicator.DEBUG) {
            print(iq)
        }
        if let query = iq.elementForName("query", xmlns: "jabber:iq:roster") {
            for item in query.elementsForName("item") as! [DDXMLElement] {
                let groupStr = item.elementForName("group").stringValue()
                let group: ExpLeagueMemberGroup
                switch groupStr {
                case "Favorites":
                    group = .Favorites
                default:
                    group = .Top
                }
                if let profile = item.elementForName("expert", xmlns: "http://expleague.com/scheme") {
                    let expert: ExpLeagueMember
                    if let existing = self.profile.expert(login: profile.attributeStringValueForName("login")) {
                        existing.updateXml(profile)
                        expert = existing
                    }
                    else {
                        expert = ExpLeagueMember(xml: profile, group: group, context: self.profile.managedObjectContext!)
                        self.profile.register(expert: expert)
                    }
                    expert.group = group
                    expert.available = profile.attributeBoolValueForName("available")
                }
            }
            dispatch_async(dispatch_get_main_queue()) {
                AppDelegate.instance.expertsView?.update()
            }
        }
        else if let query = iq.elementForName("query", xmlns: "http://expleague.com/scheme/tags") {
            for tag in query.elementsForName("tag") as! [DDXMLElement] {
                let name = tag.stringValue()
                let icon = tag.attributeStringValueForName("icon", withDefaultValue: "named://search_icon")
                let tag: ExpLeagueTag
                if let existing = profile.tag(name: name) {
                    existing.updateIcon(icon)
                }
                else {
                    tag = ExpLeagueTag(name: name, icon: icon, type: .Tag, context: profile.managedObjectContext!)
                    profile.register(tag: tag)
                }
            }
        }
        else if let query = iq.elementForName("query", xmlns: "http://expleague.com/scheme/patterns") {
            for tag in query.elementsForName("pattern") as! [DDXMLElement] {
                let name = tag.attributeStringValueForName("name")
                let icons = tag.elementsForName("icon") as! [DDXMLElement]
                let tag: ExpLeagueTag
                if let existing = profile.tag(name: name) {
                    if (icons.count > 0) {
                        existing.updateIcon(icons[0].stringValue())
                    }
                }
                else {
                    let icon = icons.count > 0 ? icons[0].stringValue() : "named://search_icon"
                    tag = ExpLeagueTag(name: name, icon: icon, type: .Pattern, context: profile.managedObjectContext!)
                    profile.register(tag: tag)
                }
            }
        }
        else if let query = iq.elementForName("query", xmlns: "http://expleague.com/scheme/best-answer") where !iq.isErrorIQ(){
            let offer = ExpLeagueOffer(xml: query.elementForName("offer", xmlns: "http://expleague.com/scheme"))
            let order = ExpLeagueOrder(offer.room, offer: offer, context: profile.managedObjectContext!)
            let content = query.elementForName("content", xmlns: "http://expleague.com/scheme/best-answer")
            for item in content.elementsForName("message") {
                let message = XMPPMessage(fromElement: item as! DDXMLElement)
                order.message(message: message)
            }
            profile.add(aow: order)
        }
        return false
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveMessage msg: XMPPMessage!) {
        if (ExpLeagueCommunicator.DEBUG) {
            print(msg)
        }
        while let receipt = msg.elementForName("received", xmlns: "urn:xmpp:receipts") {
            let id = receipt.attributeStringValueForName("id")
            let current = self.queue.filter({$0.elementID() == id}).first
            if let message = current {
                queue.removeOne(message)
                profile.delivered(outgoing: message)
            }
            msg.removeChildAtIndex(receipt.index())
        }
        if let receiptRequest = msg.elementForName("request", xmlns: "urn:xmpp:receipts") {
            msg.removeChildAtIndex(receiptRequest.index())
            let receipt = DDXMLElement(name: "received", xmlns: "urn:xmpp:receipts")
            receipt.addAttributeWithName("id", stringValue: msg.elementID())
            sender.sendElement(XMPPMessage(type: "normal", child: receipt))
        }
        if let from = msg.from(), let order = profile.order(name: from.user) {
            order.message(message: msg)
        }
        if (pending.removeOne(msg.elementID())) {
            profile.delivered(incoming: msg)
        }
        notify { listener in
            listener.onMessage?(message: msg)
        }
    }
    
    func xmppStream(sender: XMPPStream!, didReceivePresence presence: XMPPPresence!) {
        if (ExpLeagueCommunicator.DEBUG) {
            print(presence)
        }
        if let from = presence.from(), let user = from.user, let expert = profile.experts.filter({$0.id.user == user}).first {
            expert.available = presence.type() == "available"
        }
        
        notify { listener in
            listener.onPresence?(presence: presence)
        }
    }
}
