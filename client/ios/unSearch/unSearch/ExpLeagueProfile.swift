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
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
    
    init(_ name: String, domain: String, login: String, passwd: String, port: Int16, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Profile", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.name = name
        
        self.domain = domain
        self.port = port
        
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
        orderSelected = Int16(orders.count - 1)
    }
    
    var jid: XMPPJID! {
        return XMPPJID.jidWithString(login + "@" + domain + "/unSearch");
    }
    
    var progressBar: ConnectionProgressController? {
        return AppDelegate.instance.connectionProgressView
    }
    
    func order(name name: String) -> ExpLeagueOrder? {
        let fit = orders.filter({
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        })
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    var selected: ExpLeagueOrder? {
        set (order) {
            self.orderSelected = order != nil ? Int16(orders.indexOfObject(order!)) : -1
        }
        get {
            return (self.orderSelected >= 0 && Int(self.orderSelected) < orders.count) ? orders[Int(self.orderSelected)] as? ExpLeagueOrder : nil
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
    
    func hasImage(id: String) -> Bool {
        let root = NSSearchPathForDirectoriesInDomains(.DocumentDirectory, .UserDomainMask, true)[0] as String
        return NSFileManager.defaultManager().fileExistsAtPath("\(root)/\(self.name)/images/\(id)")
    }
    
    func saveImage(id: String, image: UIImage) {
        let root = NSSearchPathForDirectoriesInDomains(.DocumentDirectory, .UserDomainMask, true)[0] as String
        try! NSFileManager.defaultManager().createDirectoryAtPath("\(root)/\(self.name)/images/", withIntermediateDirectories: true, attributes: nil)
        UIImageJPEGRepresentation(image, 100)!.writeToFile("\(root)/\(self.name)/images/\(id)", atomically: true)
    }
    
    func loadImage(id: String) -> UIImage? {
        let root = NSSearchPathForDirectoriesInDomains(.DocumentDirectory, .UserDomainMask, true)[0] as String
        return UIImage(contentsOfFile: "\(root)/\(self.name)/images/\(id)")
    }

    func avatar(login: String, url: String?) -> UIImage {
        let avaName = login + "-avatar"
        if hasImage(avaName) {
            return loadImage(avaName)!
        }
        else if let urlnz = url, let nsurl = NSURL(string: urlnz),
        let data = NSData(contentsOfURL: nsurl),
        let image = UIImage(data: data) {
            saveImage(avaName, image: image)
            return image
        }
        return UIImage(named: "owl_exp")!
    }
    
    func placeOrder(topic topic: String, urgency: String, local: Bool, attachments: [String], location: CLLocationCoordinate2D?, prof: Bool) -> ExpLeagueOrder {
        var rand = NSUUID().UUIDString;
        rand = rand.substringToIndex(rand.startIndex.advancedBy(8))
        var json: [String: NSObject] = [
            "topic": topic,
            "attachments": attachments.joinWithSeparator(", "),
            "urgency": urgency,
            "local": local,
            "specific": prof,
            "started": NSDate().timeIntervalSince1970
        ]
        if (location != nil) {
            json["location"] = [
                "latitude": location!.latitude,
                "longitude": location!.longitude,
            ]
        }
        
        let topicJson = try! NSJSONSerialization.dataWithJSONObject(json, options: [])
        let order = ExpLeagueOrder("room-" + login + "-" + rand, topic: String(NSString(data: topicJson, encoding: NSUTF8StringEncoding)!), urgency: urgency, local: local, specific: prof, context: self.managedObjectContext!);
        let presence = XMPPPresence(type: "available", to: order.jid);
        AppDelegate.instance.stream.sendElement(presence)
        orderSelected = Int16(orders.count)
        let mutableItems = orders.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(order)
        orders = mutableItems.copy() as! NSOrderedSet
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
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
        if (AppDelegate.instance.token != nil) {
            let msg = XMPPMessage(type: "normal")
            let token = DDXMLElement(name: "token", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            token.setStringValue(AppDelegate.instance.token)
            msg.addChild(token)
            sender.sendElement(msg)
        }
        log("Success!");
    }
    
    @objc
    func xmppStream(sender: XMPPStream!, didReceiveTrust trust: SecTrustRef, completionHandler: (Bool) -> ()) {
        completionHandler(true)
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveIQ iq: XMPPIQ!) -> Bool {
        log(String(iq))
        if let order = order(name: iq.from().user) {
            order.iq(iq: iq)
        }
        return false
    }
    
    func xmppStream(sender: XMPPStream!, didReceiveMessage msg: XMPPMessage!) {
        (AppDelegate.instance.historyView?.view as? UITableView)?.reloadData()
        log(String(msg))
        if let order = order(name: msg.from().user) {
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
    }
    
    func xmppStream(sender: XMPPStream!, didReceivePresence presence: XMPPPresence!) {
        (AppDelegate.instance.historyView?.view as? UITableView)?.reloadData()
        log(String(presence))
        if let user = presence.from().user, let order = order(name: user) {
            order.presence(presence: presence)
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
