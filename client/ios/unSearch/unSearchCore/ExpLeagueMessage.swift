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

public struct ExpLeagueOrderMetaChange {
    public let type: ExpLeagueOrderMetaChangeType
    public let target: ExpLeagueOrderMetaChangeTarget
    public let name: String
}

public enum ExpLeagueOrderMetaChangeType: String {
    case Add = "add"
    case Remove = "remove"
    case Visit = "visit"
}

public enum ExpLeagueOrderMetaChangeTarget: String {
    case Tag = "tag"
    case Pattern = "pattern"
    case Phone = "phone"
    case Url = "url"
}

public class ExpLeagueMessage: NSManagedObject {
    public static let EXP_LEAGUE_SCHEME = "http://expleague.com/scheme"

    public var isSystem: Bool {
        return type == .system || type == .expertAssignment || type == .expertProgress
    }

    public var read: Bool {
        get {
            return properties["read"] as? String == "true"
        }
        set (v) {
            guard v != read else {
                return
            }
            self.setProperty("read", value: NSString(string: v ? "true" : "false"))
            self.parent.messagesChanged()
            self.parent.notify()
        }
    }

    public var parent: ExpLeagueOrder {
        return parentRaw as! ExpLeagueOrder
    }
    
    public var ts: Date {
        return Date(timeIntervalSince1970: time)
    }
    
    public var type: ExpLeagueMessageType {
        get {
            return ExpLeagueMessageType(rawValue: self.typeRaw)!
        }
        set (val) {
            self.typeRaw = val.rawValue
        }
    }
    
    public var expert: ExpLeagueMember? {
        if (type == .expertAssignment) {
            if (body == nil || body!.isEmpty) {
                return parent.parent.expert(login: properties["login"] as! String, factory: {context in
                    return try! ExpLeagueMember(json: self.properties, context: context)
                })
            }
            else {
                let xml = try! DDXMLElement(xmlString: body!)
                return parent.parent.expert(login: xml.attributeStringValue(forName: "login"), factory: {context in
                    return ExpLeagueMember(xml: xml, context: context)
                })
            }
        }
        return parent.parent.expert(login: from)
    }
    
    public var change: ExpLeagueOrderMetaChange? {
        if (type == .expertProgress) {
            if (body != nil && !body!.isEmpty) {
                let xml = try! DDXMLElement(xmlString: body!)
                if let change = xml.forName("change", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                    return ExpLeagueOrderMetaChange(
                        type: ExpLeagueOrderMetaChangeType(rawValue: change.attributeStringValue(forName: "operation"))!,
                        target: ExpLeagueOrderMetaChangeTarget(rawValue: change.attributeStringValue(forName: "target"))!,
                        name: change.stringValue!
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
    
    public var html: String {
        return body!
    }
    
    public func setProperty(_ name: String, value: AnyObject) {
        let properties = NSMutableDictionary()
        properties.addEntries(from: self.properties)
        properties[name] = value
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWith: data)
        archiver.encode(properties.copy() as! NSDictionary)
        archiver.finishEncoding()
        updateSync {
            self.propertiesRaw = data.xmpp_base64Encoded()
        }
    }
    
    public var properties: [String: AnyObject] {
        if (self.propertiesRaw != nil) {
            let data = Data(base64Encoded: self.propertiesRaw!, options: [])
            let archiver = NSKeyedUnarchiver(forReadingWith: data!)
            return archiver.decodeObject() as! [String: AnyObject]
        }
        return [:];
    }
    
    public func visitParts(_ visitor: ExpLeagueMessageVisitor) {
        if (type == .system || type == .topic) {
            return
        }
        if (properties["image"] != nil) {
            do {
                let imageUrl = URL(string: properties["image"] as! String)!
                let request = URLRequest(url: imageUrl)
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returning: nil)
                    
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
        super.init(entity: NSEntityDescription.entity(forEntityName: "Message", in: context)!, insertInto: context)
        let attrs = msg.attributesAsDictionary()
        let deviceId = parent.parent.login.lowercased()
        if let from = attrs["from"] {
            let fromJid = msg.from()
            if (fromJid?.domain.hasPrefix("muc."))! {
                if (fromJid?.resource == nil) {
                    self.from = "system"
                }
                else if (deviceId.hasPrefix((fromJid?.resource)!)) {
                    self.from = "me"
                }
                else {
                    self.from = (fromJid?.resource)!
                }
            }
            else if (from.range(of: "@") == nil) {
                self.from = "system"
            }
            else if (deviceId.hasPrefix((fromJid?.user)!)) {
                self.from = "me"
            }
            else {
                self.from = (fromJid?.user)!
            }
        }
        else {
            self.from = "me"
        }
        self.parentRaw = parent
        let properties = NSMutableDictionary()
        properties.setValue(msg.elementID(), forKeyPath: "id")
        var textChildren = msg.elements(forName: "subject")
        if (textChildren.count == 0) {
            textChildren = msg.elements(forName: "body")
            if (self.from == "me") {
                self.type = .clientMessage
            }
            else if (self.from == "system") {
                self.type = .system
            }
            else {
                self.type = .expertMessage
            }
        }
        else {
            self.type = .topic
        }
        
        if let element = msg.forName("expert", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .expertAssignment
            body = element.xmlString
        }
        else if let feedback = msg.forName("feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .feedback
            properties["stars"] = feedback.attributeIntegerValue(forName: "stars")
        }
        else if let _ = msg.forName("cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = type == .clientMessage ? .clientCancel : .expertCancel
        }
        else if let _ = msg.forName("done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .clientDone
        }
        else if let progress = msg.forName("progress", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            body = progress.xmlString
            type = .expertProgress
        }
        else if let answer = msg.forName("answer", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            do {
                var answerText = answer.stringValue ?? ""
                if let firstLineEnd = answerText.range(of: "\n")?.lowerBound {
                    let shortAnswer = answerText.substring(to: firstLineEnd)
                    answerText = answerText.substring(from: firstLineEnd)
                    properties["short"] = shortAnswer
                }
//                answerText = answerText.stringByReplacingOccurrencesOfString("\t", withString: " ")
                let re = try! NSRegularExpression(pattern: "\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]", options: [])
                let matches = re.matches(in: answerText, options: [], range: NSRange(location: 0, length: (answerText.characters.count)))
                
                var finalMD = ""
                var lastMatchIndex = answerText.startIndex
                var index = 0
                for match in matches as [NSTextCheckingResult] {
                    let whole = match.rangeAt(0)
                    let id = "cut-\(msg.attributeStringValue(forName: "id")!)-\(index)"
                    let id_1 = "cuts-\(msg.attributeStringValue(forName: "id")!)-1-\(index)"
                    finalMD += answerText.substring(with: lastMatchIndex..<answerText.index(answerText.startIndex, offsetBy: whole.location))
                    finalMD += "<a class=\"cut\" id=\"" + id_1 + "\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">" +
                                    (answerText as NSString).substring(with: match.rangeAt(1)) +
                                "</a>" +
                                "<div class=\"cut\" id=\"" + id + "\">" + (answerText as NSString).substring(with: match.rangeAt(2)) +
                                    "\n<a class=\"hide\" href=\"#\(id_1)\" onclick=\"javascript:showHide('" + id + "','" + id_1 + "')\">скрыть</a>" +
                                "</div>";
                    lastMatchIndex = answerText.index(answerText.startIndex, offsetBy: whole.location + whole.length)
                    index += 1
                }
                finalMD += answerText.substring(with: lastMatchIndex..<answerText.endIndex)
                self.body = try MMMarkdown.htmlString(withMarkdown: finalMD, extensions: [
                        .autolinkedURLs,
                        .fencedCodeBlocks,
                        .tables,
                        .underscoresInWords,
                        .strikethroughs,
                        .gitHubFlavored
                    ])
            }
            catch {
                parent.parent.log("\(error)")
            }
            type = .answer
        }
        else if (!textChildren.isEmpty) {
            self.body = textChildren[0].stringValue
        }
        else if let image = msg.forName("image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            properties["image"] = image.stringValue
        }
        else {
            type = .system
        }
        
        let re = try! NSRegularExpression(pattern: ".+-(\\d+)", options: [])
        let msgId = msg.elementID()

        
        if let time = attrs["time"] {
            self.time = Double(time)!
        }
        else if msgId != nil, let match = re.firstMatch(in: msgId!, options: [], range: NSRange(location: 0, length: (msgId?.characters.count)!)) {
            let time = (msgId! as NSString).substring(with: match.rangeAt(1))
            self.time = Double(time)!
        }
        else {
            self.time = attrs["time"] != nil ? Double(attrs["time"]!)!: Date().timeIntervalSince1970
        }
        let data = NSMutableData()
        let archiver = NSKeyedArchiver(forWritingWith: data)
        archiver.encode(properties)
        archiver.finishEncoding()
        self.propertiesRaw = data.xmpp_base64Encoded()
        self.save()
    }

    override init(entity: NSEntityDescription, insertInto context: NSManagedObjectContext?) {
        super.init(entity: entity, insertInto: context)
    }
}

public protocol ExpLeagueMessageVisitor {
    func message(_ message: ExpLeagueMessage, text: String)
    func message(_ message: ExpLeagueMessage, title: String, text: String)
    func message(_ message: ExpLeagueMessage, title: String, link: String)
    func message(_ message: ExpLeagueMessage, title: String, image: UIImage)
}

public  enum ExpLeagueMessageType: Int16 {
    case topic = 0
    case expertMessage = 1
    case clientMessage = 2
    case system = 3
    case answer = 4
    case expertProgress = 5
    case expertAssignment = 6
    case expertCancel = 7
    case feedback = 8
    case clientCancel = 9
    case clientDone = 10
}
