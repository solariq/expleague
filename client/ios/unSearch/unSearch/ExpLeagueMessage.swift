//
//  ExpLeagueMessage.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import XMPPFramework

class ExpLeagueMessage: NSManagedObject {
    static let EXP_LEAGUE_SCHEME = "http://expleague.com/scheme"
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }

    init(msg: XMPPMessage, parent: ExpLeagueOrder, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Message", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        let attrs = msg.attributesAsDictionary()
        self.from = attrs["from"] != nil ? (msg.from().resource != nil ? msg.from().resource : "system") : "me";
        self.parentRaw = parent
        let properties = NSMutableDictionary()
        var textChildren = msg.elementsForName("subject")
        if (textChildren.count == 0) {
            textChildren = msg.elementsForName("body")
            if (self.from == "me") {
                self.type = .ClientMessage
            }
            else if (self.from.isEmpty || (attrs["from"] != nil && msg.from().resource == nil)) {
                self.type = .SystemMessage
            }
            else {
                self.type = .ExpertMessage
            }
        }
        else {
            self.type = .TopicStarter
        }
        if (type == .SystemMessage) {
            if let element = msg.elementForName("expert", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                properties["type"] = "expert"
                properties["login"] = element.attributeStringValueForName("login")
                properties["name"] = element.attributeStringValueForName("name")
                properties["tasks"] = element.attributeStringValueForName("tasks")
            }
            else if (!textChildren.isEmpty && textChildren[0].stringValue.hasPrefix("{\"type\":\"visitedPages\"")) {
                do {
                    let json = try NSJSONSerialization.JSONObjectWithData(textChildren[0].stringValue.dataUsingEncoding(NSUTF8StringEncoding)!, options: NSJSONReadingOptions.AllowFragments)
                    properties.addEntriesFromDictionary(json as! [String : AnyObject])
                }
                catch {
                    AppDelegate.instance.activeProfile?.log("\(error)")
                }
            }
        }
        else {
            self.body = textChildren.count > 0 ? textChildren[0].stringValue : nil
            if (isAnswer){
                class Visitor: ExpLeagueMessageVisitor {
                    var shortAnswer: String? = nil
                    func message(message: ExpLeagueMessage, text: String) {
                        if (shortAnswer == nil) {
                            shortAnswer = text
                        }
                    }
                    func message(message: ExpLeagueMessage, title: String, text: String) {
                        shortAnswer = title
                    }
                    func message(message: ExpLeagueMessage, title: String, link: String) {}
                    func message(message: ExpLeagueMessage, title: String, image: UIImage) {
                        if (shortAnswer == nil) {
                            shortAnswer = title
                        }
                    }
                }
                let visitor = Visitor()
                visitParts(visitor)
                if (visitor.shortAnswer != nil) {
                    properties["short"] = visitor.shortAnswer
                }
            }
        }
        self.time = attrs["time"] != nil ? Double(attrs["time"] as! String)!: CFAbsoluteTimeGetCurrent()
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWithMutableData: data)
        archiver.encodeObject(properties)
        archiver.finishEncoding()
        self.propertiesRaw = data.xmpp_base64Encoded()

        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    var isAnswer: Bool {
        return body != nil && body!.hasPrefix("{\"type\":\"response\"")
    }
    
    var isRead: Bool {
        return properties["read"] as? String == "true"
    }

    var parent: ExpLeagueOrder {
        return parentRaw as! ExpLeagueOrder
    }
    
    var type: ExpLeagueMessageType {
        get {
            return ExpLeagueMessageType(rawValue: self.typeRaw)!
        }
        set (val) {
            self.typeRaw = val.rawValue
        }
    }
    
    var incoming: Bool {
        return self.type == .SystemMessage || self.type == .ExpertMessage
    }
    
    func setProperty(name: String, value: AnyObject) {
        let properties = NSMutableDictionary()
        properties.addEntriesFromDictionary(self.properties)
        properties[name] = value
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWithMutableData: data)
        archiver.encodeObject(properties.copy() as! NSDictionary)
        archiver.finishEncoding()
        self.propertiesRaw = data.xmpp_base64Encoded()
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
    }
    
    var properties: [String: AnyObject] {
        if (self.propertiesRaw != nil) {
            let data = NSData(base64EncodedString: self.propertiesRaw!, options: [])
            let archiver = NSKeyedUnarchiver(forReadingWithData: data!)
            return archiver.decodeObject() as! [String: AnyObject]
        }
        return [:];
    }
    
    func visitParts(visitor: ExpLeagueMessageVisitor) {
        if (type == .SystemMessage) {
            return
        }
        if (body != nil && body!.hasPrefix("{")) {
            do {
                let json = try NSJSONSerialization.JSONObjectWithData(body!.dataUsingEncoding(NSUTF8StringEncoding)!, options: NSJSONReadingOptions.AllowFragments)
                let content = json["content"] as! NSArray
                for item in content {
                    if let textItem = (item as! NSDictionary)["text"] as? NSDictionary {
                        var text: String = ""
                        if textItem["text"] != nil {
                            text = textItem["text"] as! String
                            try! text = NSRegularExpression(pattern: "(\n)+", options: []).stringByReplacingMatchesInString(text, options: [], range: NSMakeRange(0, text.characters.count), withTemplate: "\n")
                            text = text.stringByReplacingOccurrencesOfString("&nbsp;", withString: " ")
                            try! text = NSRegularExpression(pattern: "( )+", options: []).stringByReplacingMatchesInString(text, options: [], range: NSMakeRange(0, text.characters.count), withTemplate: " ")
                            text = text.stringByReplacingOccurrencesOfString("&lt;", withString: "<")
                            text = text.stringByReplacingOccurrencesOfString("&gt;", withString: ">")
                            text = text.stringByReplacingOccurrencesOfString("&quot;", withString: "\"")
                        }
                        if let title = textItem["title"] as? String {
                            visitor.message(self, title: title, text: text)
                        }
                        else {
                            visitor.message(self, text: text)
                        }
                    }
                    if let textItem = (item as! NSDictionary)["link"] as? NSDictionary {
                        if let title = textItem["title"] as? String {
                            if let _ = NSURL(string: textItem["href"] as! String) {
                                visitor.message(self, title: title, link: textItem["href"] as! String)
                            }
                            else {
                                visitor.message(self, text: title)
                            }
                        }
                    }
                    if let imageItem = (item as! NSDictionary)["image"] as? NSDictionary {
                        if let urlStr = imageItem["image"] as? String {
                            let title: String
                            if let t = imageItem["title"] as? String {
                                title = t
                            }
                            else {
                                title = "Вложение"
                            }
                            let storageName = urlStr.stringByReplacingOccurrencesOfString("/", withString: "-")
                            if AppDelegate.instance.activeProfile!.hasImage(storageName) {
                                if let image = AppDelegate.instance.activeProfile!.loadImage(storageName) {
                                    visitor.message(self, title: title, image: image)
                                }
                                else {
                                    visitor.message(self, title: title, text: urlStr)
                                }
                            }
                            else if let url = NSURL(string: urlStr),
                               let data = NSData(contentsOfURL: url),
                               let image = UIImage(data: data) {
                                visitor.message(self, title: title, image: image)
                                AppDelegate.instance.activeProfile!.saveImage(storageName, image: image)
                            }
                            else {
                                visitor.message(self, title: title, text: urlStr)
                            }
                        }
                    }
                    
                }
            }
            catch {
                AppDelegate.instance.activeProfile!.log("\(error)")
            }
        }
        else {
            if (body != nil) {
                visitor.message(self, text: body!)
            }
        }
    }
}

protocol ExpLeagueMessageVisitor {
    func message(message: ExpLeagueMessage, text: String)
    func message(message: ExpLeagueMessage, title: String, text: String)
    func message(message: ExpLeagueMessage, title: String, link: String)
    func message(message: ExpLeagueMessage, title: String, image: UIImage)
}

enum ExpLeagueMessageType: Int16 {
    case TopicStarter  = 0
    case ExpertMessage = 1
    case ClientMessage = 2
    case SystemMessage = 3
}
