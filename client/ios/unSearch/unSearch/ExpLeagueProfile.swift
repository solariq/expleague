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
    let onPresence: ((_ presence: XMPPPresence) -> Void)?
    let onMessage: ((_ message: XMPPMessage) -> Void)?
    init(onPresence: ((_ presence: XMPPPresence) -> Void)?) {
        self.onPresence = onPresence
        self.onMessage = nil
    }

    init(onMessage: ((_ presence: XMPPMessage) -> Void)?) {
        self.onPresence = nil
        self.onMessage = onMessage
    }
}

@objc
class ExpLeagueProfile: NSManagedObject {
    static var active: ExpLeagueProfile {
        return AppDelegate.instance.activeProfile!
    }
    
    static var state: ExpLeagueCommunicatorState {
        return AppDelegate.instance.activeProfile!.communicator!.state
    }
    
    var thread: DispatchQueue {
        return AppDelegate.instance.xmppQueue
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
    
    fileprivate dynamic var communicator: ExpLeagueCommunicator?
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

    var expectingAOW: Bool {
        return receiveAnswerOfTheWeek?.boolValue ?? false
    }
    
    var busy: Bool {
        return incoming > 0 || outgoing > 0 || expectingAOW
    }
    
    func busyChanged() {
        QObject.notify(#selector(self.busyChanged), self)
    }
    
    func disconnect() {
        communicator?.state.mode = .background
        communicator = nil
        updateSync {
            self.active = false
        }
    }
    
    func connect() {
        communicator = ExpLeagueCommunicator(profile: self)
        communicator!.state.mode = .foreground
        updateSync {
            self.active = true
        }
    }
    
    func suspend() {
        communicator?.state.mode = .background
    }

    func resume() {
        communicator?.state.mode = .foreground
    }

    func track(_ tracker: XMPPTracker) {
        communicator!.track(tracker)
    }
    
    override func awakeFromFetch() {
        orderSelected = NSNumber(value: orders.count - 1 as Int)
    }
    
    func expect(_ id: String) {
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
    var jid: XMPPJID! {
        return _jid ?? XMPPJID(string: login + "@" + domain + "/unSearch")
    }
    
    func order(name: String) -> ExpLeagueOrder? {
        let fit = orders.filter() {
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        }
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    func send(_ msg: XMPPMessage) {
        communicator!.send(msg)
    }
    
    func imageUrl(_ imageId: String) -> URL {
        if (domain == "localhost") {
            return URL(string: "http://localhost:8067/\(imageId)")!
        }
        else {
            return URL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/\(imageId)")!
        }
    }
    
    var imageStorage: URL {
        if (domain == "localhost") {
            return URL(string: "http://localhost:8067/")!
        }
        else {
            return URL(string: "https://img.\(domain)/OSYpRdXPNGZgRvsY/")!
        }
    }
    
    func aow(_ title: String) {
        updateSync {
            self.aowTitle = title
            self.receiveAnswerOfTheWeek = true
            self.busyChanged()
            if (self.communicator!.state.status == .connected) {
                self.communicator!.requestAOW()
            }
        }
    }
    
    fileprivate dynamic var _experts: [ExpLeagueMember]?
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
    
    internal func enqueue(_ msg: XMPPMessage) {
        update {
            self.queue = (self.queue ?? NSOrderedSet()).append(QueueItem(message: msg, context: self.managedObjectContext!))
            self.outgoingChanged()
        }
    }
    
    internal func register(expert: ExpLeagueMember) -> ExpLeagueMember {
        let experts = expertsSet?.mutableCopy() ?? NSMutableSet()
        (experts as AnyObject).add(expert)
        expertsSet = ((experts as AnyObject).copy(with: nil) as! NSSet)
        if (self._experts != nil) {
            self._experts?.append(expert)
        }
        save()
        return expert
    }
    
    func expert(login id: String, factory: ((NSManagedObjectContext)->ExpLeagueMember)? = nil) -> ExpLeagueMember? {
        if let existing = experts.filter({return $0.login == id}).first {
            return existing
        }
        if (factory != nil) {
            var expert: ExpLeagueMember?
            AppDelegate.instance.xmppQueue.sync {
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
    
    internal func register(tag: ExpLeagueTag) -> ExpLeagueTag {
        let tags = tagsSet?.mutableCopy() ?? NSMutableSet()
        (tags as AnyObject).add(tag)
        tagsSet = ((tags as AnyObject).copy(with: nil) as! NSSet)
        if (_tags != nil) {
            _tags?.append(tag)
        }
        return tag
    }
    
    func tag(name id: String, factory: ((NSManagedObjectContext)->ExpLeagueTag)? = nil) -> ExpLeagueTag? {
        if let existing = tags.filter({return $0.name == id}).first {
            return existing
        }
        if (factory != nil) {
            var tag: ExpLeagueTag?
            AppDelegate.instance.xmppQueue.sync {
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
    func avatar(_ login: String, url urlStr: String?) -> UIImage {
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
        
    func placeOrder(topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String]) {
        var rand = UUID().uuidString;
        rand = rand.substring(to: rand.characters.index(rand.startIndex, offsetBy: 8))
        update {
            let offer = ExpLeagueOffer(topic: topic, urgency: urgency, local: local, location: locationOrNil, experts: experts, images: images, started: nil)
            let order = ExpLeagueOrder("room-" + self.login + "-" + rand, offer: offer, context: self.managedObjectContext!)
            let msg = XMPPMessage(type: "normal", to: order.jid)!
            msg.addChild(offer.xml)
            self.send(msg)
        
            self.orderSelected = NSNumber(value: self.orders.count as Int)
            self.add(order: order)
            DispatchQueue.main.async {
                AppDelegate.instance.historyView?.selected = order
            }
        }
    }
    
    func add(order: ExpLeagueOrder) {
        guard orders.filter({($0 as AnyObject).id == order.id}).isEmpty else {
            return
        }
        let mutableItems = orders.mutableCopy() as! NSMutableOrderedSet
        mutableItems.add(order)
        self.orders = mutableItems.copy() as! NSOrderedSet
        save()
        DispatchQueue.main.async {
            AppDelegate.instance.historyView?.populate()
        }
    }

    func add(aow order: ExpLeagueOrder) {
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
        busyChanged()
        add(order: order)
        Notifications.notifyBestAnswer(order, title: aowTitle ?? "")
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

    func visitQueue(_ visitor: (XMPPMessage)->()) {
        for item in queue ?? NSOrderedSet() {
            visitor(try! XMPPMessage(xmlString: (item as! QueueItem).body!))
        }
    }
    
    func log(_ msg: String) {
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .short
        dateFormatter.timeStyle = .long
        
        print(dateFormatter.string(from: Date()) + ": " + msg)
    }
}

