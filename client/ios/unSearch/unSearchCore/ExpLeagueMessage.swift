//
//  ExpLeagueMessage.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import CoreGraphics
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
            return readInner?.boolValue ?? true
        }
        set (v) {
            guard v != read else {
                return
            }
            updateSync {
                self.readInner = NSNumber(value: v)
                if (v) {
                    self.parent.onMessageRead(message: self)
                }
            }
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
                if (xml.attribute(forName: "login") != nil) {
                    return parent.parent.expert(login: xml.attributeStringValue(forName: "login"), factory: {context in
                        return ExpLeagueMember(xml: xml, context: context)
                    })
                }
                else if (xml.attribute(forName: "jid") != nil){
                    let jid = XMPPJID(string: xml.attributeStringValue(forName: "jid"))!
                    return parent.parent.expert(login: jid.user, factory: {context in
                        return ExpLeagueMember(xml: xml, context: context)
                    })
                }
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
    
    fileprivate dynamic var _html: String?
    public var html: String {
        if _html == nil {
            _html = md2html(md: body!)
        }
        return _html!
    }
    
    public var isEmpty: Bool {
        return type != .answer && body?.isEmpty ?? true || body?.range(of: "\n") == nil
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
            _properties = nil
        }
    }
    
    fileprivate var _properties: [String: AnyObject]? = nil
    public var properties: [String: AnyObject] {
        guard _properties == nil else {
            return _properties!
        }
        if (self.propertiesRaw != nil) {
            let data = Data(base64Encoded: self.propertiesRaw!, options: [])
            let archiver = NSKeyedUnarchiver(forReadingWith: data!)
            _properties = (archiver.decodeObject() as! [String: AnyObject])
        }
        else {
            _properties = [:]
        }
        return _properties!
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
                ExpLeagueProfile.active.log("Unable to load image \(properties["image"] ?? "none" as AnyObject): \(error)");
            }
        }
        if (body != nil) {
            visitor.message(self, text: body!)
        }
    }
    
    var id: String? {
        return properties["id"] as? String
    }
    
    static let cutRE = try! NSRegularExpression(pattern: "\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]", options: [])
    static let brokenLinksRE = try! NSRegularExpression(pattern: "(\\w+|\\/)#\\s+", options: [])
    fileprivate func md2html(md: String) -> String {
        if (!(body ?? "").hasPrefix("<answer")) {
            return body!
        }
        do {
            let answer = try! DDXMLElement(xmlString: body!)
            var answerText = answer.stringValue ?? ""
            if let firstLineEnd = answerText.range(of: "\n")?.lowerBound {
                answerText = answerText.substring(from: firstLineEnd)
            }
            
            answerText = ExpLeagueMessage.brokenLinksRE.stringByReplacingMatches(in: answerText, options: .withoutAnchoringBounds, range: NSRange(location: 0, length: answerText.characters.count), withTemplate: "\\1#")
            let matches = ExpLeagueMessage.cutRE.matches(in: answerText, options: [], range: NSRange(location: 0, length: answerText.characters.count))
            
            var finalMD = ""
            var lastMatchIndex = 0
            var index = 0
            for match in matches as [NSTextCheckingResult] {
                let whole = match.rangeAt(0)
                let id = "cut-\(self.id!)-\(index)"
                let id_1 = "cuts-\(self.id!)-1-\(index)"
                var section = String(answerText.utf16[String.UTF16Index(lastMatchIndex)..<String.UTF16Index(whole.location)])!
                section.append("<a class=\"cut\" id=\"")
                section.append(id_1)
                section.append("\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">")
                section.append((answerText as NSString).substring(with: match.rangeAt(1)))
                section.append("</a>")
                section.append("<div class=\"cut\" id=\"" + id + "\">")
                section.append((answerText as NSString).substring(with: match.rangeAt(2)))
                section.append("\n<a class=\"hide\" href=\"#" + id_1 + "\" onclick=\"javascript:showHide('" + id + "','" + id_1 + "')\">скрыть</a>")
                section.append("</div>")
                finalMD.append(section)
                lastMatchIndex = whole.location + whole.length
                index += 1
            }
            finalMD.append(String(answerText.utf16[String.UTF16Index(lastMatchIndex)..<String.UTF16Index(answerText.utf16.count)])!)
            return try MMMarkdown.htmlString(withMarkdown: finalMD, extensions: [
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
        return ""
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
            body = answer.xmlString
            let answerText = answer.stringValue ?? ""
            if let firstLineEnd = answerText.range(of: "\n")?.lowerBound {
                let shortAnswer = answerText.substring(to: firstLineEnd)
                properties["short"] = shortAnswer
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
