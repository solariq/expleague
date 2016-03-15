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

class ExpLeagueMessage: NSManagedObject {
    static let EXP_LEAGUE_SCHEME = "http://expleague.com/scheme"

    var isSystem: Bool {
        return type == .System || type == .ExpertAssignment || type == .ExpertProgress
    }

    var isRead: Bool {
        return properties["read"] as? String == "true"
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
        if (type == .System || type == .Topic) {
            return
        }
        if (properties["image"] != nil) {
            do {
                let imageUrl = NSURL(string: properties["image"] as! String)!
                let request = NSURLRequest(URL: imageUrl)
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returningResponse: nil)
                    
                if let image = UIImage(data: imageData) {
                    visitor.message(self, title: "Приложение", image: image)
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
                self.type = .System
            }
            else {
                self.type = .ExpertMessage
            }
        }
        else {
            self.type = .Topic
        }
        if (type == .System) {
            if let element = msg.elementForName("expert", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                properties["type"] = "expert"
                properties["login"] = element.attributeStringValueForName("login")
                properties["name"] = element.attributeStringValueForName("name")
                properties["tasks"] = element.attributeStringValueForName("tasks")
                if let ava = element.elementForName("avatar", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                    properties["avatar"] = ava.stringValue()
                    parent.parent.avatar(properties["login"] as! String, url: ava.stringValue())
                }
                type = .ExpertAssignment
                body = element.XMLString()
            }
            if let _ = msg.elementForName("cancel", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
                properties["command"] = "cancel"
                type = .ExpertCancel
            }
            
            if (!textChildren.isEmpty && textChildren[0].stringValue.hasPrefix("{\"type\":\"pageVisited\"")) {
                type = .ExpertProgress
                do {
                    let json = try NSJSONSerialization.JSONObjectWithData(textChildren[0].stringValue.dataUsingEncoding(NSUTF8StringEncoding)!, options: NSJSONReadingOptions.AllowFragments)
                    properties.addEntriesFromDictionary(json as! [String : AnyObject])
                }
                catch {
                    AppDelegate.instance.activeProfile?.log("\(error)")
                }
            }
        }
        else if let answer = msg.elementForName("answer", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            do {
                var answerText = answer.stringValue()
                if let firstLineEnd = answerText.rangeOfString("\n")?.startIndex {
                    let shortAnswer = answerText.substringToIndex(firstLineEnd)
                    answerText = answerText.substringFromIndex(firstLineEnd)
                    properties["short"] = shortAnswer
                }
                let re = try! NSRegularExpression(pattern: "\\+\\[([^\\]]+)\\]([^-]*(?:-[^\\[][^-]*)*)-\\[\\1\\]", options: [])
                let matches = re.matchesInString(answerText, options: [], range: NSRange(location: 0, length: answerText.characters.count))
                
                var finalMD = ""
                var lastMatchIndex = answerText.startIndex
                var index = 0
                for match in matches as [NSTextCheckingResult] {
                    let whole = match.rangeAtIndex(0)
                    let id = "cut-\(msg.attributeStringValueForName("id"))-\(index)"
                    let id_1 = "cuts-\(msg.attributeStringValueForName("id_1"))-\(index)"
                    finalMD += answerText.substringWithRange(lastMatchIndex..<answerText.startIndex.advancedBy(whole.location))
                    finalMD += "<a class=\"cut\" id=\"" + id_1 + "\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">" + (answerText as NSString).substringWithRange(match.rangeAtIndex(1)) + "</a>" +
                        "<div class=\"cut\" id=\"" + id + "\" style=\"display: none\">" + (answerText as NSString).substringWithRange(match.rangeAtIndex(2)) +
                        "\n<a class=\"hide\" href=\"javascript:showHide('" + id + "','" + id_1 + "')\">скрыть</a></div>";
                    lastMatchIndex = answerText.startIndex.advancedBy(whole.location).advancedBy(whole.length)
                    index++
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
        else if let feedback = msg.elementForName("feedback", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .Feedback
            properties["stars"] = feedback.attributeIntegerValueForName("stars")
        }
        else if let _ = msg.elementForName("done", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            type = .System
        }
        else {
            self.body = textChildren.count > 0 ? textChildren[0].stringValue : nil
        }
        
        if let image = msg.elementForName("image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME) {
            properties["image"] = image.stringValue()
        }
        self.time = attrs["time"] != nil ? Double(attrs["time"] as! String)!: NSDate().timeIntervalSince1970
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
}
