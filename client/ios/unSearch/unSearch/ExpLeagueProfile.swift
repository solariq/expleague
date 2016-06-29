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

@objc
class ExpLeagueProfile: NSManagedObject {
    static var active: ExpLeagueProfile {
        return AppDelegate.instance.activeProfile!
    }
    
    static var state: ExpLeagueCommunicatorState {
        return AppDelegate.instance.activeProfile!.communicator!.state
    }
    
    var thread: dispatch_queue_t {
        return AppDelegate.instance.xmppQueue
    }
    
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    private dynamic var communicator: ExpLeagueCommunicator?
    init(_ name: String, domain: String, login: String, passwd: String, port: Int16, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Profile", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.name = name
        
        self.domain = domain
        self.port = NSNumber(short: port)
        
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
        communicator?.state.mode = .Background
        communicator = nil
        updateSync {
            self.active = false
        }
    }
    
    func connect() {
        communicator = ExpLeagueCommunicator(profile: self)
        communicator!.state.mode = .Foreground
        updateSync {
            self.active = true
        }
    }
    
    func suspend() {
        communicator?.state.mode = .Background
    }

    func resume() {
        communicator?.state.mode = .Foreground
    }

    func track(tracker: XMPPTracker) {
        communicator!.track(tracker)
    }
    
    override func awakeFromFetch() {
        orderSelected = NSNumber(long: orders.count - 1)
    }
    
    func expect(id: String) {
        updateSync {
            for order in self.orders {
                for message in (order as! ExpLeagueOrder).messagesRaw {
                    if let msg = message as? ExpLeagueMessage where msg.id == id {
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
        return _jid ?? XMPPJID.jidWithString(login + "@" + domain + "/unSearch")
    }
    
    func order(name name: String) -> ExpLeagueOrder? {
        let fit = orders.filter() {
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        }
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    func send(msg: XMPPMessage) {
        communicator!.send(msg)
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
    
    func aow(title: String) {
        updateSync {
            self.aowTitle = title
            self.receiveAnswerOfTheWeek = true
            self.busyChanged()
            if (self.communicator!.state.status == .Connected) {
                self.communicator!.requestAOW()
            }
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
    
    internal func enqueue(msg: XMPPMessage) {
        update {
            self.queue = (self.queue ?? NSOrderedSet()).append(QueueItem(message: msg, context: self.managedObjectContext!))
            self.outgoingChanged()
        }
    }
    
    internal func register(expert expert: ExpLeagueMember) -> ExpLeagueMember {
        let experts = expertsSet?.mutableCopy() ?? NSMutableSet()
        experts.addObject(expert)
        expertsSet = (experts.copy() as! NSSet)
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
            dispatch_sync(AppDelegate.instance.xmppQueue) {
                expert = factory!(self.managedObjectContext!)
                self.register(expert: expert!)
            }
            return expert
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
    
    internal func register(tag tag: ExpLeagueTag) -> ExpLeagueTag {
        let tags = tagsSet?.mutableCopy() ?? NSMutableSet()
        tags.addObject(tag)
        tagsSet = (tags.copy() as! NSSet)
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
            dispatch_sync(AppDelegate.instance.xmppQueue) {
                tag = factory!(self.managedObjectContext!)
                self.register(tag: tag!)
            }
            return tag
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
        
    func placeOrder(topic topic: String, urgency: String, local: Bool, location locationOrNil: CLLocationCoordinate2D?, experts: [XMPPJID], images: [String]) {
        var rand = NSUUID().UUIDString;
        rand = rand.substringToIndex(rand.startIndex.advancedBy(8))
        update {
            let offer = ExpLeagueOffer(topic: topic, urgency: urgency, local: local, location: locationOrNil, experts: experts, images: images, started: nil)
            let order = ExpLeagueOrder("room-" + self.login + "-" + rand, offer: offer, context: self.managedObjectContext!)
            let msg = XMPPMessage(type: "normal", to: order.jid)
            msg.addChild(offer.xml)
            self.send(msg)
        
            self.orderSelected = NSNumber(long: self.orders.count)
            self.add(order: order)
            dispatch_async(dispatch_get_main_queue()) {
                AppDelegate.instance.historyView?.selected = order
            }
        }
    }
    
    func add(order order: ExpLeagueOrder) {
        guard orders.filter({$0.id == order.id}).isEmpty else {
            return
        }
        let mutableItems = orders.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(order)
        self.orders = mutableItems.copy() as! NSOrderedSet
        save()
        dispatch_async(dispatch_get_main_queue()) {
            AppDelegate.instance.historyView?.populate()
        }
    }

    func add(aow order: ExpLeagueOrder) {
        order.emulate()
        for o in orders {
            let order = o as! ExpLeagueOrder
            if (order.fake) {
                order.archive()
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

    func visitQueue(visitor: (XMPPMessage)->()) {
        for item in queue ?? NSOrderedSet() {
            visitor(try! XMPPMessage(XMLString: (item as! QueueItem).body))
        }
    }
    
    func log(msg: String) {
        let dateFormatter = NSDateFormatter()
        dateFormatter.dateStyle = .ShortStyle
        dateFormatter.timeStyle = .LongStyle
        
        print(dateFormatter.stringFromDate(NSDate()) + ": " + msg)
    }
}

