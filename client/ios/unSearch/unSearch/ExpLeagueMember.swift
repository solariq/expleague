//
//  ExpLeagueMember.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 12/04/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import XMPPFramework

class ExpLeagueMember: NSManagedObject {
    private dynamic var _xml: DDXMLElement?
    var xml: DDXMLElement {
        if (_xml == nil) {
            _xml = try! DDXMLElement(XMLString: xmlStr);
        }
        return _xml!
    }
    
    var id: XMPPJID {
        return XMPPJID.jidWithString(xml.attributeStringValueForName("jid"))
    }
    
    var login: String {
        return id.user
    }
    
    var avatarUrl: String {
        return xml.elementForName("avatar").stringValue()
    }
    
    var name: String {
        return xml.attributeStringValueForName("name")
    }
    
    var tasks: Int {
        return xml.attributeIntegerValueForName("tasks")
    }
    
    var rating: Double {
        return xml.attributeDoubleValueForName("rating")
    }
    
    var based: Int {
        return xml.attributeIntegerValueForName("basedOn")
    }
    
    var group: ExpLeagueMemberGroup {
        get {
            return ExpLeagueMemberGroup(rawValue: self.groupInt.shortValue)!
        }
        set (group) {
            groupInt = NSNumber(short: group.rawValue)
            save()
        }
    }
    
    var tags: [String] {
        var tags: [String] = []
        if let tagsE = xml.elementForName("tags") {
            for tag in tagsE.elementsForName("tag") as! [DDXMLElement] {
                tags.append(tag.stringValue())
            }
        }
        return tags
    }
        
    dynamic var available: Bool = false
    
    var avatar: UIImage {
        return AppDelegate.instance.activeProfile!.avatar(id.user, url: avatarUrl)
    }
    
    dynamic var _myTasks: NSNumber?
    var myTasks: Int {
        if (_myTasks != nil) {
            return _myTasks!.integerValue
        }
        var count = 0
        for o in AppDelegate.instance.activeProfile!.orders where o is ExpLeagueOrder {
            let order = o as! ExpLeagueOrder
            for i in 0..<order.count {
                let msg = order.message(i)
                if (msg.type == .Answer && msg.from.hasSuffix(id.user)) {
                    count += 1
                    break
                }
            }
        }
        _myTasks = count
        return count
    }
    
    func update(xml: DDXMLElement) {
        self.xmlStr = xml.XMLString()
        self.save();
    }
    
    init(xml: DDXMLElement, group: ExpLeagueMemberGroup, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Expert", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)

        self.xmlStr = xml.XMLString()
        self.groupInt = NSNumber(short: group.rawValue)
        save()
    }
    
    convenience init(xml: DDXMLElement, context: NSManagedObjectContext) {
        self.init(xml: xml, group: .Favorites, context: context)
    }
    
    convenience init(json: [String: AnyObject], context: NSManagedObjectContext) throws {
        self.init(xml: try DDXMLElement(XMLString: "<expert xmlns=\"http://expleague.com/scheme\" jid=\"\(json["login"]!)@\(AppDelegate.instance.activeProfile!.domain)\" login=\"\(json["login"]!)\" name=\"\(json["name"]!)\" tasks=\"0\" education=\"high\" available=\"false\" rating=\"5.0\" basedOn=\"0\"><avatar>\(json["avatar"]!)</avatar></expert>"), context: context)
    }
    
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
}

enum ExpLeagueMemberGroup: Int16 {
    case Favorites = 0
    case Top = 1
}
