//
//  ExpLeagueProfile.swift
//  unSearch
//
//  Created by Igor Kuralenok on 15.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import XMPPFramework


@objc
class XMPPTracker: NSObject {
    let onPresence: ((presence: XMPPPresence) -> Void)?
    let onMessage: ((message: XMPPMessage) -> Void)?
    init(onPresence: ((presence: XMPPPresence) -> Void)?) {
        self.onPresence = onPresence
        self.onMessage = nil
    }

    init(onMessage: ((presence: XMPPMessage) -> Void)?) {
        self.onPresence = nil
        self.onMessage = onMessage
    }
}

@objc(ExpLeagueProfile)
class ExpLeagueProfile: NSManagedObject {
    static var active: ExpLeagueProfile {
        return AppDelegate.instance.activeProfile!
    }
    
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    init(_ name: String, domain: String, login: String, passwd: String, port: Int16, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Profile", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.name = name
        
        self.domain = domain
        self.port = NSNumber(short: port)
        
        self.login = login
        self.passwd = passwd
        self.active = false
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    private dynamic var listeners: NSMutableArray = []
    func track(tracker: XMPPTracker) {
        listeners.addObject(Weak(tracker))
    }
    
    override func awakeFromFetch() {
        orderSelected = NSNumber(long: orders.count - 1)
    }
    
    dynamic var _jid: XMPPJID?
    var jid: XMPPJID! {
        return _jid ?? XMPPJID.jidWithString(login + "@" + domain + "/unSearch");
    }
    
    var progressBar: ConnectionProgressController? {
        return AppDelegate.instance.connectionProgressView
    }
    
    func order(name name: String) -> ExpLeagueOrder? {
        let fit = orders.filter() {
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        }
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    func send(msg: XMPPMessage) {
        let stream = AppDelegate.instance.stream
        let receiptRequest = DDXMLElement(name: "request", xmlns: "urn:xmpp:receipts")
        let msgId = stream.generateUUID()
        msg.addAttributeWithName("id", stringValue: msgId)
        msg.addChild(receiptRequest)
        let queue = self.queue?.mutableCopy() as? NSMutableSet ?? NSMutableSet()
        queue.addObject(QueueItem(message: msg, context: self.managedObjectContext!))
        self.queue = queue.copy() as? NSSet
        self.log("Sending: \(msg.XMLString())");
        stream.sendElement(msg)
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    var selected: ExpLeagueOrder? {
        set (order) {
            self.orderSelected = order != nil ? NSNumber(long: orders.indexOfObject(order!)) : NSNumber(long: -1)
        }
        get {
            guard let selected = self.orderSelected?.integerValue else {
                return nil
            }
            return (selected >= 0 && selected < orders.count) ? orders[selected] as? ExpLeagueOrder : nil
        }
    }
    
    func imageUrl(imageId: String) -> NSURL {
        if (domain == "localhost") {
            return NSURL(string: "http://localhost:8067/\(imageId)")!
        }
        else {
            return NSURL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/\(imageId)")!
        }
    }
    
    var imageStorage: NSURL {
        if (domain == "localhost") {
            return NSURL(string: "http://localhost:8067/")!
        }
        else {
            return NSURL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/")!
        }
    }
    
    private dynamic var _experts: [ExpLeagueMember]?
    var experts: [ExpLeagueMember] {
        if (self._experts == nil) {
            var _experts: [ExpLeagueMember] = []
            for expert in self.expertsSet ?? [] {
                _experts.append(expert as! ExpLeagueMember)
            }
            self._experts = _experts
        }
        return self._experts!
    }
    func register(expert expert: ExpLeagueMember) -> ExpLeagueMember {
        let experts = expertsSet?.mutableCopy() ?? NSMutableSet()
        experts.addObject(expert)
        expertsSet = (experts.copy() as! NSSet)
        if (_experts != nil) {
            _experts?.append(expert)
        }
        save()
        return expert
    }
    func expert(login id: String, factory: ((NSManagedObjectContext)->ExpLeagueMember)? = nil) -> ExpLeagueMember? {
        if let existing = experts.filter({return $0.login == id}).first {
            return existing
        }
        if (factory != nil) {
            return register(expert: factory!(self.managedObjectContext!))
        }
        else {
            return nil
        }
    }
    
    private dynamic var _tags: [ExpLeagueTag]?
    var tags: [ExpLeagueTag] {
        if (self._tags == nil) {
            var _tags: [ExpLeagueTag] = []
            for tag in self.tagsSet ?? [] {
                _tags.append(tag as! ExpLeagueTag)
            }
            self._tags = _tags
        }
        return self._tags!
    }
    func register(tag tag: ExpLeagueTag) -> ExpLeagueTag {
        let tags = tagsSet?.mutableCopy() ?? NSMutableSet()
        tags.addObject(tag)
        tagsSet = (tags.copy() as! NSSet)
        if (_tags != nil) {
            _tags?.append(tag)
        }
        save()
        return tag
    }
    func tag(name id: String, factory: ((NSManagedObjectContext)->ExpLeagueTag)? = nil) -> ExpLeagueTag? {
        if let existing = tags.filter({return $0.name == id}).first {
            return existing
        }
        if (factory != nil) {
            return register(tag: factory!(self.managedObjectContext!))
        }
        else {
            return nil
        }
    }

    
    dynamic var avatars: [String: String] = [:]
    func avatar(login: String, url urlStr: String?) -> UIImage {
        if let u = urlStr ?? avatars[login], let url = NSURL(string: u) {
            avatars[login] = u
            let request = NSURLRequest(URL: url)
            do {
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returningResponse: nil)
            
                if let image = UIImage(data: imageData) {
                    return image
                }
            }
            catch {
                ExpLeagueProfile.active.log("Unable to load avatar \(url): \(error)");
            }
        }

        return UIImage(named: "owl_exp")!
    }
        
    func placeOrder(topic topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String]) -> ExpLeagueOrder {
        var rand = NSUUID().UUIDString;
        rand = rand.substringToIndex(rand.startIndex.advancedBy(8))
        let offer = ExpLeagueOffer(topic: topic, urgency: urgency, local: local, location: locationOrNil, experts: experts, images: images, started: nil)
        let order = ExpLeagueOrder("room-" + login + "-" + rand, offer: offer, context: self.managedObjectContext!)
        let msg = XMPPMessage(type: "normal", to: order.jid)
        msg.addChild(offer.xml)
        send(msg)
        
        orderSelected = NSNumber(long: orders.count)
        let mutableItems = orders.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(order)
        orders = mutableItems.copy() as! NSOrderedSet
        save()
        return order
    }
}

extension ExpLeagueProfile: XMPPStreamDelegate {
    @objc
    func xmppStreamDidConnect(sender: XMPPStream!) {
        progressBar?.progress = .Connected
        
        log("Connected")
        do {
            try sender.authenticateWithPassword(passwd);
        }
        catch {
            log(String(error))
        }
    }
    
    @objc
    func xmppStreamConnectDidTimeout(sender: XMPPStream!) {
        progressBar?.error("Timeout")
        log("Timedout");
    }
    
    @objc
    func xmppStreamDidDisconnect(sender: XMPPStream!, withError error: NSError!) {
        let msg = "Disconnected" + (error != nil ? " with error:\n\(error)" : "")
        progressBar?.error(msg)
        log(msg);
    }
    
    @objc
    func xmppStreamDidStartNegotiation(sender: XMPPStream!) {
        progressBar?.progress = .Negotiations
        log("Starting negotiations")
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        progressBar?.progress = .SocketOpened
        log("Socket opened");
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        progressBar?.progress = .Configuring
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
                        var props: [DDXMLElement] = []
                        let system = NSBundle.mainBundle().infoDictionary!
                        props.append(DDXMLElement(name: "username", stringValue: jid.user))
                        props.append(DDXMLElement(name: "password", stringValue: passwd))
                        props.append(DDXMLElement(name: "email", stringValue: "\(NSProcessInfo.processInfo().operatingSystemVersionString)/\(system["CFBundleIdentifier"]!)/\(system["CFBundleVersion"])/Development"))

                        try sender.registerWithPassword(passwd)
                    }
                    catch {
                        log("\(error)")
                    }
                    return
                }
            }
        }
        progressBar?.error("Can not authenticate: \(error)")
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
        progressBar?.progress = .Connected
        self._jid = sender.myJID
        if (AppDelegate.instance.token != nil) {
            let msg = XMPPMessage(type: "normal")
            let token = DDXMLElement(name: "token", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            token.setStringValue(AppDelegate.instance.token)
            token.addAttributeWithName("type", stringValue: "development")
            msg.addChild(token)
            sender.sendElement(msg)
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
        
        // sending not confirmed messages
        for item in queue ?? NSSet() {
            sender.sendElement(try! XMPPMessage.init(XMLString: (item as! QueueItem).body))
        }
        log("Success!");
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, didReceiveTrust trust: SecTrustRef, completionHandler: (Bool) -> ()) {
        completionHandler(true)
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveIQ iq: XMPPIQ!) -> Bool {
        log(String(iq))
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
                    if let existing = self.expert(login: profile.attributeStringValueForName("login")) {
                        existing.update(profile)
                        expert = existing
                    }
                    else {
                        expert = ExpLeagueMember(xml: profile, group: group, context: self.managedObjectContext!)
                        register(expert: expert)
                    }
                    expert.group = group
                    expert.available = profile.attributeBoolValueForName("available")
                }
            }
            AppDelegate.instance.expertsView?.update()
        }
        else if let query = iq.elementForName("query", xmlns: "http://expleague.com/scheme/tags") {
            for tag in query.elementsForName("tag") as! [DDXMLElement] {
                let name = tag.stringValue()
                let icon = tag.attributeStringValueForName("icon", withDefaultValue: "named://search_icon")
                let tag: ExpLeagueTag
                if let existing = self.tag(name: name) {
                    existing.updateIcon(icon)
                }
                else {
                    tag = ExpLeagueTag(name: name, icon: icon, type: .Tag, context: self.managedObjectContext!)
                    register(tag: tag)
                }
            }
        }
        else if let query = iq.elementForName("query", xmlns: "http://expleague.com/scheme/patterns") {
            for tag in query.elementsForName("pattern") as! [DDXMLElement] {
                let name = tag.attributeStringValueForName("name")
                let icons = tag.elementsForName("icon") as! [DDXMLElement]
                let tag: ExpLeagueTag
                if let existing = self.tag(name: name) {
                    if (icons.count > 0) {
                        existing.updateIcon(icons[0].stringValue())
                    }
                }
                else {
                    let icon = icons.count > 0 ? icons[0].stringValue() : "named://search_icon"
                    tag = ExpLeagueTag(name: name, icon: icon, type: .Pattern, context: self.managedObjectContext!)
                    register(tag: tag)
                }
            }
        }
        else if let from = iq.from(), let order = order(name: from.user) {
            order.iq(iq: iq)
        }
        return false
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveMessage msg: XMPPMessage!) {
        log(String(msg))
        while let receipt = msg.elementForName("received", xmlns: "urn:xmpp:receipts") {
            let queue = self.queue?.mutableCopy() as? NSMutableSet ?? NSMutableSet()
            for item in queue {
                if ((item as! QueueItem).receipt == receipt.attributeStringValueForName("id")) {
                    queue.removeObject(item)
                    self.queue = queue.copy() as? NSSet
                    do {
                        try self.managedObjectContext!.save()
                    } catch {
                        fatalError("Failure to save context: \(error)")
                    }
                    break
                }
            }
            msg.removeChildAtIndex(receipt.index())
        }
        let receiptRequest = msg.elementForName("request", xmlns: "urn:xmpp:receipts")
        if (receiptRequest != nil) {
            msg.removeChildAtIndex(receiptRequest.index())
        }
        if let from = msg.from(), let order = order(name: from.user) {
            order.message(message: msg)
        }
        for listenerRef in listeners.copy() as! NSArray {
            if let listener = (listenerRef as! Weak<XMPPTracker>).value {
                listener.onMessage?(message: msg)
            }
            else {
                listeners.removeObject(listenerRef)
            }
        }
        if receiptRequest != nil {
            let receipt = DDXMLElement(name: "received", xmlns: "urn:xmpp:receipts")
            receipt.addAttributeWithName("id", stringValue: msg.elementID())
            sender.sendElement(XMPPMessage(type: "normal", child: receipt))
        }
    }
    
    func xmppStream(sender: XMPPStream!, didReceivePresence presence: XMPPPresence!) {
        log(String(presence))
        if let from = presence.from(), let user = from.user {
            if let order = order(name: user) {
                order.presence(presence: presence)
            }
            else if let expert = experts.filter({$0.id.user == user}).first {
                expert.available = presence.type() == "available"
            }
        }
        
        for listenerRef in listeners.copy() as! NSArray {
            if let listener = (listenerRef as! Weak<XMPPTracker>).value {
                listener.onPresence?(presence: presence)
            }
            else {
                listeners.removeObject(listenerRef)
            }
        }
    }
    
    func log(msg: String) {
        let dateFormatter = NSDateFormatter()
        dateFormatter.dateStyle = .ShortStyle
        dateFormatter.timeStyle = .LongStyle
        
        print(dateFormatter.stringFromDate(NSDate()) + ": " + msg)
    }
}
