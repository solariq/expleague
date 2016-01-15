//
//  ExpLeagueMessage.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import JSQMessagesViewController
import XMPPFramework

class ExpLeagueMessage: NSManagedObject {
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }

    init(msg: XMPPMessage, parent: ExpLeagueOrder, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Message", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        let attrs = msg.attributesAsDictionary()
        self.from = attrs["from"] != nil ? msg.from().resource : "me";
        self.parentRaw = parent
        var textChildren = msg.elementsForName("subject")
        if (textChildren.count == 0) {
            textChildren = msg.elementsForName("body")
            if (self.from == "me") {
                self.type = .ClientMessage
            }
            else if (self.from.isEmpty) {
                self.type = .SystemMessage
            }
            else {
                self.type = .ExpertMessage
            }
        }
        else {
            self.type = .TopicStarter
        }
        self.body = textChildren.count > 0 ? textChildren[0].stringValue : nil
        self.time = attrs["time"] != nil ? Double(attrs["time"] as! String)!: CFAbsoluteTimeGetCurrent()
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
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
    
    func jsq() -> ExpLeagueJSQTextMessage {
        return ExpLeagueJSQTextMessage(delegate: self)
    }
}

enum ExpLeagueMessageType: Int16 {
    case TopicStarter  = 0
    case ExpertMessage = 1
    case ClientMessage = 2
    case SystemMessage = 3
}

class ExpLeagueJSQTextMessage: NSObject, JSQMessageData {
    @nonobjc static var avatars : [String: JSQMessageAvatarImageDataSource] = [:]
    @nonobjc static var incomingAvaWidth = UInt(20)
    @nonobjc static var outgoingAvaWidth = UInt(20)
    
    let delegate: ExpLeagueMessage
    
    init(delegate: ExpLeagueMessage) {
        self.delegate = delegate
    }
    var incoming: Bool {
        get {
            return delegate.from != "me"
        }
    }
    
    var avatar: JSQMessageAvatarImageDataSource {
        get {
            var result = ExpLeagueJSQTextMessage.avatars[delegate.from]
            if (result == nil) {
                let name = delegate.from
                let diameter = delegate.incoming ? ExpLeagueJSQTextMessage.incomingAvaWidth : ExpLeagueJSQTextMessage.outgoingAvaWidth
                
                let rgbValue = name.hash
                let r = CGFloat(Float((rgbValue & 0xFF0000) >> 16)/255.0)
                let g = CGFloat(Float((rgbValue & 0xFF00) >> 8)/255.0)
                let b = CGFloat(Float(rgbValue & 0xFF)/255.0)
                let color = UIColor(red: r, green: g, blue: b, alpha: 0.5)
                
                let nameLength = name.characters.count
                let initials : String? = name.substringToIndex(name.startIndex.advancedBy(min(2, nameLength))).uppercaseString
                result = JSQMessagesAvatarImageFactory.avatarImageWithUserInitials(initials!, backgroundColor: color, textColor: UIColor.blackColor(), font: UIFont.systemFontOfSize(CGFloat(13)), diameter: diameter)
                

                ExpLeagueJSQTextMessage.avatars[delegate.from] = result
            }
            return result!
        }
    }
    
    func senderId() -> String! {
        return incoming ? "me" : delegate.from
    }
    
    func senderDisplayName() -> String! {
        return incoming ? "Я" : delegate.from
    }
    
    func date() -> NSDate! {
        return NSDate(timeIntervalSince1970: delegate.time)
    }
    
    func isMediaMessage() -> Bool {
        return false
    }
    
    func messageHash() -> UInt {
        return delegate.hash >= 0 ? UInt(delegate.hash) : UInt(-delegate.hash)
    }
    
    func text() -> String! {
        return delegate.body
    }
}
