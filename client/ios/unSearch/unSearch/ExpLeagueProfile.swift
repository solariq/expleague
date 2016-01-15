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
    
    override func awakeFromFetch() {
        orderSelected = Int16(orders.count - 1)
    }
    
    var jid: XMPPJID! {
        return XMPPJID.jidWithString(login + "@" + domain + "/unSearch");
    }
    
    func order(name name: String) -> ExpLeagueOrder? {
        let fit = orders.filter({
            let order = $0 as! ExpLeagueOrder
            return order.id == name
        })
        return fit.count > 0 ? (fit[0] as! ExpLeagueOrder) : nil
    }
    
    var selected: ExpLeagueOrder  {
        set (order) {
            self.orderSelected = Int16(orders.indexOfObject(order))
        }
        get {
            return orders[Int(self.orderSelected)] as! ExpLeagueOrder
        }
    }
    
    func placeOrder(topic topic: String, urgency: String, local: Bool, prof: Bool) -> ExpLeagueOrder {
        var rand = NSUUID().UUIDString;
        rand = rand.substringToIndex(rand.startIndex.advancedBy(8))
        let order = ExpLeagueOrder("room-" + login + "-" + rand, topic: topic, urgency: urgency, local: local, specific: prof, context: self.managedObjectContext!);
        let presence = XMPPPresence(type: "available", to: order.jid);
        AppDelegate.instance.stream.sendElement(presence)
        orderSelected = Int16(orders.count - 1)
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
                        try sender.registerWithPassword(passwd)
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
    }
    
    func xmppStream(sender: XMPPStream!, didReceivePresence presence: XMPPPresence!) {
        (AppDelegate.instance.historyView?.view as? UITableView)?.reloadData()
        log(String(presence))
        if let user = presence.from().user, let order = order(name: user) {
            order.presence(presence: presence)
        }
    }
    
    func log(msg: String) {
        let dateFormatter = NSDateFormatter()
        dateFormatter.dateStyle = .ShortStyle
        dateFormatter.timeStyle = .LongStyle
        
        print(dateFormatter.stringFromDate(NSDate()) + ": " + msg)
    }
}
