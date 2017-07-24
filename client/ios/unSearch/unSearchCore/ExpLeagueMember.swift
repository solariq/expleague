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


public class ExpLeagueMember: NSManagedObject {
    fileprivate dynamic var _xml: DDXMLElement?
    var xml: DDXMLElement {
        if (_xml == nil) {
            _xml = try! DDXMLElement(xmlString: xmlStr);
        }
        return _xml!
    }
    
    public var id: XMPPJID {
        return XMPPJID(string: xml.attributeStringValue(forName: "jid"))
    }
    
    public var login: String {
        return id.user
    }
    
    public var avatarUrl: String {
        return xml.forName("avatar") != nil ? xml.forName("avatar")!.stringValue! : ""
    }
    
    public var name: String {
        return xml.attribute(forName: "name") != nil ? xml.attributeStringValue(forName: "name") : ""
    }
    
    public var tasks: Int {
        return xml.attribute(forName: "tasks") != nil ? xml.attributeIntegerValue(forName: "tasks") : 0
    }
    
    public var rating: Double {
        return xml.attribute(forName: "rating") != nil ? xml.attributeDoubleValue(forName: "rating") : 2.5
    }
    
    public var based: Int {
        return xml.attribute(forName: "basedOn") != nil ? xml.attributeIntegerValue(forName: "basedOn") : 0
    }
    
    public var group: ExpLeagueMemberGroup {
        get {
            return ExpLeagueMemberGroup(rawValue: self.groupInt.int16Value)!
        }
        set (group) {
            groupInt = NSNumber(value: group.rawValue as Int16)
            save()
        }
    }
    
    public var tags: [String] {
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
        
    public dynamic var available: Bool = false {
        willSet (next) {
            guard next != available else {
                return
            }
            if next {
                self.profile.online(expert: self)
            }
            else {
                self.profile.online(expert: self, false)
            }
        }
        didSet {
            DispatchQueue.main.async {
                self.changed()
            }
        }
    }
    
    public var avatar: UIImage {
        return ExpLeagueProfile.active.avatar(id.user, url: avatarUrl)
    }
    
    dynamic var _myTasks: NSNumber?
    public var myTasks: Int {
        if (_myTasks != nil) {
            return _myTasks!.intValue
        }
        var count = 0
        for o in ExpLeagueProfile.active.orders {
            let order = o as! ExpLeagueOrder
            guard !order.fake else {
                continue
            }
            count += order.experts.contains(self) ? 1 : 0
        }
        _myTasks = count as NSNumber?
        return count
    }
    
    func updateXml(_ xml: DDXMLElement) {
        xmlStr = xml.xmlString
        save()
        changed()
    }
    
    public func changed() {
        QObject.notify(#selector(changed), self)
    }
    
    init(xml: DDXMLElement, group: ExpLeagueMemberGroup, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entity(forEntityName: "Expert", in: context)!, insertInto: context)
        self.profile = ExpLeagueProfile.active
        self.xmlStr = xml.xmlString
        self.groupInt = NSNumber(value: group.rawValue as Int16)
        save()
        if (available) {
            self.profile.online(expert: self)
        }
    }
    
    convenience init(xml: DDXMLElement, context: NSManagedObjectContext) {
        self.init(xml: xml, group: .favorites, context: context)
    }
    
    convenience init(json: [String: AnyObject], context: NSManagedObjectContext) throws {
        self.init(xml: try DDXMLElement(xmlString: "<expert xmlns=\"http://expleague.com/scheme\" jid=\"\(json["login"]!)@\(ExpLeagueProfile.active.domain)\" login=\"\(json["login"]!)\" name=\"\(json["name"]!)\" tasks=\"0\" education=\"high\" available=\"false\" rating=\"5.0\" basedOn=\"0\"><avatar>\(json["avatar"] as? String ?? "")</avatar></expert>"), context: context)
    }
    
    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
}

public enum ExpLeagueMemberGroup: Int16 {
    case favorites = 0
    case top = 1
}
