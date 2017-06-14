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
public class XMPPTracker: NSObject {
    let onPresence: ((_ presence: XMPPPresence) -> Void)?
    let onMessage: ((_ message: XMPPMessage) -> Void)?
    
    public init(onPresence: ((_ presence: XMPPPresence) -> Void)?) {
        self.onPresence = onPresence
        self.onMessage = nil
    }

    public init(onMessage: ((_ presence: XMPPMessage) -> Void)?) {
        self.onPresence = nil
        self.onMessage = onMessage
    }
}

@objc
public class ExpLeagueProfile: NSManagedObject {
    public static var active: ExpLeagueProfile {
        return DataController.shared().activeProfile!
    }
    
    public static var state: ExpLeagueCommunicatorState {
        return DataController.shared().activeProfile!.communicator!.state
    }
    
    fileprivate dynamic var communicator: ExpLeagueCommunicator?

    var expectingAOW: Bool {
        return receiveAnswerOfTheWeek?.boolValue ?? false
    }
    
    public var busy: Bool {
        return incoming > 0 || outgoing > 0 || expectingAOW
    }
    
    public func busyChanged() { QObject.notify(#selector(self.busyChanged), self) }
    public func adjustUnread(_ increment: Int) {
        update {
            self.unread += increment
            if (self.unread < 0) {
                self.unread = 0
            }
            
            self.unreadChanged()
        }
    }
    public func unreadChanged() { QObject.notify(#selector(self.unreadChanged), self) }

    public func disconnect() {
        communicator?.state.mode = .background
        communicator = nil
        updateSync {
            self.active = false
        }
    }
    
    public func connect() {
        communicator = ExpLeagueCommunicator(profile: self)
        communicator!.state.mode = .foreground
        QObject.connect(communicator!, signal: #selector(ExpLeagueCommunicator.stateChanged), receiver: self, slot: #selector(self.connectedChanged))
        updateSync {
            self.active = true
        }
    }
    
    public var connected: Bool {
        return communicator?.state.status == .connected
    }
    
    public func connectedChanged() { QObject.notify(#selector(self.connectedChanged), self) }
    
    public func suspend() {
        communicator?.state.mode = .background
    }

    public func resume() {
        communicator?.state.mode = .foreground
    }

    public func track(_ tracker: XMPPTracker) {
        communicator!.track(tracker)
    }
    
    override public func awakeFromFetch() {
        orderSelected = NSNumber(value: -1)
    }
    
    public func expect(_ id: String) {
        updateSync {
            for order in self.orders {
                for message in (order as! ExpLeagueOrder).messagesRaw {
                    if let msg = message as? ExpLeagueMessage , msg.id == id {
                        return
                    }
                }
            }
//            self.pendingStr = (self.pendingStr != nil ? self.pendingStr! + " " : "") + id
            self.communicator!.expect(id)
            self.incomingChanged()
        }
    }
    
    dynamic var _jid: XMPPJID?
    public var jid: XMPPJID! {
        return _jid ?? XMPPJID(string: login + "@" + domain + "/unSearch")
    }
    
    public func application(email: String) {
        let application = DDXMLElement(name: "application", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
        application.stringValue = email
        let msg = XMPPMessage(type: "normal", child: application)!
        msg.addAttribute(withName: "to", stringValue: domain)
        ExpLeagueProfile.active.send(msg)
    }
    
    public func listOrders() -> [ExpLeagueOrder] {
        var result: [ExpLeagueOrder] = []
        var unread: Int32 = 0
        for o in self.orders {
            let order = o as! ExpLeagueOrder
            result.append(order)
            if (order.status != .archived) {
                unread += order.unread
            }
        }
        if (self.unread != unread) {
            adjustUnread(unread - self.unread)
        }
        return result
    }
    
    public var selectedOrder: ExpLeagueOrder? {
        get {
            return orderSelected.intValue >= 0 && orderSelected.intValue < orders.count ? orders[orderSelected.intValue] as? ExpLeagueOrder : nil
        }
        set (value) {
            update {
                self.orderSelected = NSNumber(value: Int16(value != nil ? self.orders.index(of:value!) : -1))
                self.ordersChanged()
            }
        }
    }
    
    public func order(name: String) -> ExpLeagueOrder? {
        let fit = orders.filter() {
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        }
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    func send(_ msg: XMPPMessage) {
        communicator!.send(msg)
    }
    
    public func imageUrl(_ imageId: String) -> URL {
        if (domain == "localhost") {
            return URL(string: "http://localhost:8067/\(imageId)")!
        }
        else {
            return URL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/\(imageId)")!
        }
    }
    
    public var imageStorage: URL {
        if (domain == "localhost") {
            return URL(string: "http://localhost:8067/")!
        }
        else {
            return URL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/")!
        }
    }
    
    public func aow(_ id: String, title: String?) {
        guard id != aowId else {
            return
        }
        updateSync {
            self.aowId = id
            self.aowTitle = title
            self.receiveAnswerOfTheWeek = true
            self.busyChanged()
            if (self.communicator!.state.status == .connected) {
                self.communicator!.requestAOW()
            }
        }
    }
    
    fileprivate dynamic var _experts: [ExpLeagueMember]?
    public var onlineExperts: [ExpLeagueMember] = []
    public var experts: [ExpLeagueMember] {
        if (self._experts == nil) {
            var _experts: [ExpLeagueMember] = []
            for expert in self.expertsSet ?? [] {
                _experts.append(expert as! ExpLeagueMember)
            }
            self._experts = _experts
        }
        return self._experts!
    }
    
    func online(expert: ExpLeagueMember, _ available: Bool = true) {
        if (available) {
            onlineExperts.append(expert)
        }
        else {
            _ = onlineExperts.removeOne(expert)
        }
        expertsChanged()
    }
    
    public func expertsChanged() { QObject.notify(#selector(expertsChanged), self) }
    
    internal func register(expert: ExpLeagueMember) -> ExpLeagueMember {
        if (self._experts != nil) {
            self._experts?.append(expert)
        }
        expertsChanged()
        return expert
    }
    
    public func expert(login id: String, factory: ((NSManagedObjectContext)->ExpLeagueMember)? = nil) -> ExpLeagueMember? {
        if let existing = experts.filter({return $0.login == id}).first {
            return existing
        }
        if (factory != nil) {
            var expert: ExpLeagueMember?
            ExpLeagueCommunicator.xmppQueue.sync {
                expert = factory!(self.managedObjectContext!)
                _ = self.register(expert: expert!)
            }
            return expert
        }
        else {
            return nil
        }
    }
    
    
    fileprivate dynamic var _tags: [ExpLeagueTag]?
    public var tags: [ExpLeagueTag] {
        if (self._tags == nil) {
            var _tags: [ExpLeagueTag] = []
            for tag in self.tagsSet ?? [] {
                _tags.append(tag as! ExpLeagueTag)
            }
            self._tags = _tags
        }
        return self._tags!
    }
    
    internal func register(tag: ExpLeagueTag) -> ExpLeagueTag {
        if (_tags != nil) {
            _tags?.append(tag)
        }
        return tag
    }
    
    public func tag(name id: String, factory: ((NSManagedObjectContext)->ExpLeagueTag)? = nil) -> ExpLeagueTag? {
        if let existing = tags.filter({return $0.name == id}).first {
            return existing
        }
        if (factory != nil) {
            var tag: ExpLeagueTag?
            ExpLeagueCommunicator.xmppQueue.sync {
                tag = factory!(self.managedObjectContext!)
                _ = self.register(tag: tag!)
            }
            return tag
        }
        else {
            return nil
        }
    }
    
    dynamic var avatars: [String: String] = [:]
    public func avatar(_ login: String, url urlStr: String?) -> UIImage {
        if let u = urlStr ?? avatars[login], let url = URL(string: u) {
            avatars[login] = u
            let request = URLRequest(url: url)
            do {
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returning: nil)
            
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
    
    public func ordersChanged() { DispatchQueue.main.async { QObject.notify(#selector(self.ordersChanged), self) } }
    
    public func placeOrder(topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String]) {
        var rand = UUID().uuidString;
        rand = rand.substring(to: rand.characters.index(rand.startIndex, offsetBy: 8))
        update {
            let offer = ExpLeagueOffer(topic: topic, urgency: urgency, local: local, location: locationOrNil, experts: experts, images: images, started: nil)
            let order = ExpLeagueOrder("room-" + self.login + "-" + rand, offer: offer, context: self.managedObjectContext!)
            let msg = XMPPMessage(type: "normal", to: order.jid)!
            msg.addChild(offer.xml)
            self.send(msg)
        
            self.orderSelected = NSNumber(value: self.orders.count as Int)            
            self.selectedOrder = self.add(order: order)
        }
               
    }
    
    func add(order: ExpLeagueOrder) -> ExpLeagueOrder {
        guard orders.filter({($0 as AnyObject).id == order.id}).isEmpty else {
            return orders.filter({($0 as! ExpLeagueOrder).id == order.id}).first! as! ExpLeagueOrder
        }
        let mutableItems = orders.mutableCopy() as! NSMutableOrderedSet
        mutableItems.add(order)
        self.orders = mutableItems.copy() as! NSOrderedSet
        save()
        self.ordersChanged()
        return order
    }

    func add(aow order: ExpLeagueOrder) -> ExpLeagueOrder {
        order.emulate()
        for o in orders {
            let current = o as! ExpLeagueOrder
            if (current.id == order.id) {
                current.emulate()
            }
            else if (current.fake) {
                current.archive()
            }
        }
        receiveAnswerOfTheWeek = false
        aowId = order.id
        busyChanged()
        if (aowTitle != nil) {
            Notifications.notifyBestAnswer(order, title: aowTitle!)
        }
        return add(order: order)
    }
    
    var outgoing: Int {
        return queue != nil ? queue!.count : 0
    }

    var incoming: Int {
        return communicator?.pending.count ?? 0
    }
    
    func outgoingChanged() {
        busyChanged()
        QObject.notify(#selector(self.outgoingChanged), self)
    }
    
    func incomingChanged() {
        busyChanged()
        QObject.notify(#selector(self.incomingChanged), self)
    }

    func delivered(outgoing msg: XMPPMessage) {
        update {
            self.queue = (self.queue ?? NSOrderedSet()).removeAll() {(i: QueueItem) -> Bool in i.receipt == msg.elementID()}
            self.outgoingChanged()
        }
    }
    
    func delivered(incoming msg: XMPPMessage) {
        incomingChanged()
    }

    internal func enqueue(_ msg: XMPPMessage) {
        update {
            self.queue = (self.queue ?? NSOrderedSet()).append(QueueItem(message: msg, context: self.managedObjectContext!))
            self.outgoingChanged()
        }
    }
    
    public func visitQueue(_ visitor: (XMPPMessage)->()) {
        for item in queue ?? NSOrderedSet() {
            visitor(try! XMPPMessage(xmlString: (item as! QueueItem).body!))
        }
    }
    
    public func log(_ msg: String) {
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .short
        dateFormatter.timeStyle = .long
        
        print(dateFormatter.string(from: Date()) + ": " + msg)
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
    
    init(_ name: String, domain: String, login: String, passwd: String, port: Int16, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "Profile", in: context)!, insertInto: context)
        self.name = name
        
        self.domain = domain
        self.port = NSNumber(value: port as Int16)
        
        self.login = login
        self.passwd = passwd
        self.active = false
        save()
    }
    
    deinit {
        QObject.disconnect(self)
    }
}

