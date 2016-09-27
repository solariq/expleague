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
fileprivate func < <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l < r
  case (nil, _?):
    return true
  default:
    return false
  }
}

fileprivate func > <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l > r
  default:
    return rhs < lhs
  }
}


class ExpLeagueMember: NSManagedObject {
    fileprivate dynamic var _xml: DDXMLElement?
    var xml: DDXMLElement {
        if (_xml == nil) {
            _xml = try! DDXMLElement(xmlString: xmlStr);
        }
        return _xml!
    }
    
    var id: XMPPJID {
        return XMPPJID(string: xml.attributeStringValue(forName: "jid"))
    }
    
    var login: String {
        return id.user
    }
    
    var avatarUrl: String {
        return xml.forName("avatar")!.stringValue!
    }
    
    var name: String {
        return xml.attributeStringValue(forName: "name")
    }
    
    var tasks: Int {
        return xml.attributeIntegerValue(forName: "tasks")
    }
    
    var rating: Double {
        return xml.attributeDoubleValue(forName: "rating")
    }
    
    var based: Int {
        return xml.attributeIntegerValue(forName: "basedOn")
    }
    
    var group: ExpLeagueMemberGroup {
        get {
            return ExpLeagueMemberGroup(rawValue: self.groupInt.int16Value)!
        }
        set (group) {
            groupInt = NSNumber(value: group.rawValue as Int16)
            save()
        }
    }
    
    var tags: [String] {
        var tags: [String] = []
        var scores: [String: Double] = [:]
        if let tagsE = xml.forName("tags") {
            for tag in tagsE.elements(forName: "tag") {
                let name = tag.stringValue
                tags.append(name!)
                scores[name!] = tag.attributeDoubleValue(forName: "score")
            }
        }
        tags.sort() {
            return scores[$0]! > scores[$1]
        }
        return tags
    }
        
    dynamic var available: Bool = false {
        didSet {
            DispatchQueue.main.async {
                self.badge?.update(self)
                self.view?.update()
            }
        }
    }
    
    var avatar: UIImage {
        return AppDelegate.instance.activeProfile!.avatar(id.user, url: avatarUrl)
    }
    
    dynamic var _myTasks: NSNumber?
    var myTasks: Int {
        if (_myTasks != nil) {
            return _myTasks!.intValue
        }
        var count = 0
        for o in AppDelegate.instance.activeProfile!.orders {
            let order = o as! ExpLeagueOrder
            guard !order.fake else {
                continue
            }
            count += order.messages.filter{msg in msg.type == .answer && msg.from == id.user}.isEmpty ? 0 : 1
        }
        _myTasks = count as NSNumber?
        return count
    }
    
    func updateXml(_ xml: DDXMLElement) {
        xmlStr = xml.xmlString
        save()
    }
    
    dynamic weak var badge: ExpertCell?
    dynamic weak var view: ExpertViewController?
    override func notify() {
        badge?.update(self)
        view?.update()
    }
    
    init(xml: DDXMLElement, group: ExpLeagueMemberGroup, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "Expert", in: context)!, insertInto: context)
        self.xmlStr = xml.xmlString
        self.groupInt = NSNumber(value: group.rawValue as Int16)
        save()
    }
    
    convenience init(xml: DDXMLElement, context: NSManagedObjectContext) {
        self.init(xml: xml, group: .favorites, context: context)
    }
    
    convenience init(json: [String: AnyObject], context: NSManagedObjectContext) throws {
        self.init(xml: try DDXMLElement(xmlString: "<expert xmlns=\"http://expleague.com/scheme\" jid=\"\(json["login"]!)@\(AppDelegate.instance.activeProfile!.domain)\" login=\"\(json["login"]!)\" name=\"\(json["name"]!)\" tasks=\"0\" education=\"high\" available=\"false\" rating=\"5.0\" basedOn=\"0\"><avatar>\(json["avatar"]!)</avatar></expert>"), context: context)
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
}

enum ExpLeagueMemberGroup: Int16 {
    case favorites = 0
    case top = 1
}
