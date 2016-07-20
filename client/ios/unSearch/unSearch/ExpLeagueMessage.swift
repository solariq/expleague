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
import MMMarkdown

struct ExpLeagueOrderMetaChange {
    let type: ExpLeagueOrderMetaChangeType
    let target: ExpLeagueOrderMetaChangeTarget
    let name: String
}

enum ExpLeagueOrderMetaChangeType: String {
    case Add = "add"
    case Remove = "remove"
    case Visit = "visit"
}

enum ExpLeagueOrderMetaChangeTarget: String {
    case Tag = "tag"
    case Pattern = "pattern"
    case Phone = "phone"
    case Url = "url"
}

class ExpLeagueMessage: NSManagedObject {
    static let EXP_LEAGUE_SCHEME = "http://expleague.com/scheme"

    var isSystem: Bool {
        return type == .System || type == .ExpertAssignment || type == .ExpertProgress
    }

    var read: Bool {
        get {
            return properties["read"] as? String == "true"
        }
        set (v) {
            guard v != read else {
                return
            }
            self.setProperty("read", value: v ? "true" : "false")
            self.parent.messagesChanged()
            self.parent.notify()
        }
    }

    var parent: ExpLeagueOrder {
        return parentRaw as! ExpLeagueOrder
    }
    
    var ts: NSDate {
        return NSDate(timeIntervalSince1970: time)
    }
    
    var type: ExpLeagueMessageType {
        get {
            return ExpLeagueMessageType(rawValue: self.typeRaw)!
        }
        set (val) {
            self.typeRaw = val.rawValue
        }
    }
    
    var expert: ExpLeagueMember? {
        if (type == .ExpertAssignment) {
            if (body == nil || body!.isEmpty) {
                return parent.parent.expert(login: properties["login"] as! String, factory: {context in
                    return try! ExpLeagueMember(json: self.properties, context: context)
                })
            }
            else {
                let xml = try! DDXMLElement(XMLString: body)
                return parent.parent.expert(login: xml.attributeStringValueForName("login"), factory: {context in
                    return ExpLeagueMember(xml: xml, context: context)
                })
            }
        }
        return parent.parent.expert(login: from)
    }
    
    var change: ExpLeagueOrderMetaChange? {
        if (type == .ExpertProgress) {
            if let xml = try? DDXMLElement(XMLString: body) where body != nil && !body!.isEmpty {
                if let change = xml.elementForName("change", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                    return ExpLeagueOrderMetaChange(
                        type: ExpLeagueOrderMetaChangeType(rawValue: change.attributeStringValueForName("operation"))!,
                        target: ExpLeagueOrderMetaChangeTarget(rawValue: change.attributeStringValueForName("target"))!,
                        name: change.stringValue()
                    )
                }
            }
            else if properties["type"] as? String == "pageVisited" {
                return ExpLeagueOrderMetaChange(
                    type: .Visit,
                    target: .Url,
                    name: properties["data"] as! String
                )
            }
        }
        return nil
    }
    
    func setProperty(name: String, value: AnyObject) {
        let properties = NSMutableDictionary()
        properties.addEntriesFromDictionary(self.properties)
        properties[name] = value
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWithMutableData: data)
        archiver.encodeObject(properties.copy() as! NSDictionary)
        archiver.finishEncoding()
        updateSync {
            self.propertiesRaw = data.xmpp_base64Encoded()
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
        if (type == .System || type == .Topic) {
            return
        }
        if (properties["image"] != nil) {
            do {
                let imageUrl = NSURL(string: properties["image"] as! String)!
                let request = NSURLRequest(URL: imageUrl)
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returningResponse: nil)
                    
                if let image = UIImage(data: imageData) {
                    visitor.message(self, title: "", image: image)
                }
            }
            catch {
                ExpLeagueProfile.active.log("Unable to load image \(properties["image"]): \(error)");
            }
        }
        if (body != nil) {
            visitor.message(self, text: body!)
        }
    }
    
    var id: String? {
        return properties["id"] as? String
    }
    
    
    init(msg: XMPPMessage, parent: ExpLeagueOrder, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Message", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        let attrs = msg.attributesAsDictionary()
        let deviceId = parent.parent.login.lowercaseString
        if let from = attrs["from"] as? String {
            let fromJid = msg.from()
            if (fromJid.domain.hasPrefix("muc.")) {
                if (fromJid.resource == nil) {
                    self.from = "system"
                }
                else if (deviceId.hasPrefix(fromJid.resource)) {
                    self.from = "me"
                }
                else {
                    self.from = fromJid.resource
                }
            }
            else if (from.rangeOfString("@") == nil) {
                self.from = "system"
            }
            else if (deviceId.hasPrefix(fromJid.user)) {
                self.from = "me"
            }
            else {
                self.from = fromJid.user
            }
        }
        else {
            self.from = "me"
        }
        self.parentRaw = parent
        let properties = NSMutableDictionary()
        properties.setValue(msg.elementID(), forKeyPath: "id")
        var textChildren = msg.elementsForName("subject")
        if (textChildren.count == 0) {
            textChildren = msg.elementsForName("body")
            if (self.from == "me") {
                self.type = .ClientMessage
            }
            else if (self.from == "system") {
                self.type = .System
            }
            else {
                self.type = .ExpertMessage
            }
        }
        else {
            self.type = .Topic
        }
        
        if let element = msg.elementForName("expert", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .ExpertAssignment
            body = element.XMLString()
        }
        else if let feedback = msg.elementForName("feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .Feedback
            properties["stars"] = feedback.attributeIntegerValueForName("stars")
        }
        else if let _ = msg.elementForName("cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = type == .ClientMessage ? .ClientCancel : .ExpertCancel
        }
        else if let _ = msg.elementForName("done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .ClientDone
        }
        else if let progress = msg.elementForName("progress", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            body = progress.XMLString()
            type = .ExpertProgress
        }
        else if let answer = msg.elementForName("answer", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            do {
                var answerText = answer.stringValue()
                if let firstLineEnd = answerText.rangeOfString("\n")?.startIndex {
                    let shortAnswer = answerText.substringToIndex(firstLineEnd)
                    answerText = answerText.substringFromIndex(firstLineEnd)
                    properties["short"] = shortAnswer
                }
//                answerText = answerText.stringByReplacingOccurrencesOfString("\t", withString: " ")
                let re = try! NSRegularExpression(pattern: "\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]", options: [])
                let matches = re.matchesInString(answerText, options: [], range: NSRange(location: 0, length: answerText.characters.count))
                
                var finalMD = ""
                var lastMatchIndex = answerText.startIndex
                var index = 0
                for match in matches as [NSTextCheckingResult] {
                    let whole = match.rangeAtIndex(0)
                    let id = "cut-\(msg.attributeStringValueForName("id"))-\(index)"
                    let id_1 = "cuts-\(msg.attributeStringValueForName("id"))-1-\(index)"
                    finalMD += answerText.substringWithRange(lastMatchIndex..<answerText.startIndex.advancedBy(whole.location))
                    finalMD += "<a class=\"cut\" id=\"" + id_1 + "\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">" + (answerText as NSString).substringWithRange(match.rangeAtIndex(1)) + "</a>" +
                               "<div class=\"cut\" id=\"" + id + "\">" + (answerText as NSString).substringWithRange(match.rangeAtIndex(2)) +
                               "\n<a class=\"hide\" href=\"#\(id_1)\" onclick=\"javascript:showHide('" + id + "','" + id_1 + "')\">скрыть</a></div>";
                    lastMatchIndex = answerText.startIndex.advancedBy(whole.location).advancedBy(whole.length)
                    index += 1
                }
                finalMD += answerText.substringWithRange(lastMatchIndex..<answerText.endIndex)
                self.body = try MMMarkdown.HTMLStringWithMarkdown(finalMD, extensions: [
                        .AutolinkedURLs,
                        .FencedCodeBlocks,
                        .Tables,
                        .UnderscoresInWords,
                        .Strikethroughs,
                        .GitHubFlavored
                    ])
            }
            catch {
                parent.parent.log("\(error)")
            }
            type = .Answer
        }
        else if (!textChildren.isEmpty) {
            self.body = textChildren[0].stringValue
        }
        else if let image = msg.elementForName("image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            properties["image"] = image.stringValue()
        }
        else {
            type = .System
        }
        
        let re = try! NSRegularExpression(pattern: ".+-(\\d+)", options: [])
        let msgId = msg.elementID()

        
        if let time = attrs["time"] as? String{
            self.time = Double(time)!
        }
        else if let match = re.firstMatchInString(msgId, options: [], range: NSRange(location: 0, length: msgId.characters.count)) {
            let time = (msgId as NSString).substringWithRange(match.rangeAtIndex(1))
            self.time = Double(time)!
        }
        else {
            self.time = attrs["time"] != nil ? Double(attrs["time"] as! String)!: NSDate().timeIntervalSince1970
        }
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWithMutableData: data)
        archiver.encodeObject(properties)
        archiver.finishEncoding()
        self.propertiesRaw = data.xmpp_base64Encoded()
        self.save()
    }

    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }
}

protocol ExpLeagueMessageVisitor {
    func message(message: ExpLeagueMessage, text: String)
    func message(message: ExpLeagueMessage, title: String, text: String)
    func message(message: ExpLeagueMessage, title: String, link: String)
    func message(message: ExpLeagueMessage, title: String, image: UIImage)
}

enum ExpLeagueMessageType: Int16 {
    case Topic = 0
    case ExpertMessage = 1
    case ClientMessage = 2
    case System = 3
    case Answer = 4
    case ExpertProgress = 5
    case ExpertAssignment = 6
    case ExpertCancel = 7
    case Feedback = 8
    case ClientCancel = 9
    case ClientDone = 10
}
