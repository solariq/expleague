//
//  ExpLeagueOrder.swift
//  unSearch
//
//  Created by Igor Kuralenok on 14.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import JSQMessagesViewController
import XMPPFramework

class ExpLeagueOrder: NSManagedObject {
    override init(entity: NSEntityDescription, insertIntoManagedObjectContext context: NSManagedObjectContext?) {
        super.init(entity: entity, insertIntoManagedObjectContext: context)
    }

    init(_ roomId: String, topic: String, urgency: String, local: Bool, specific: Bool, context: NSManagedObjectContext) {
        super.init(entity: NSEntityDescription.entityForName("Order", inManagedObjectContext: context)!, insertIntoManagedObjectContext: context)
        self.started = CFAbsoluteTimeGetCurrent()
        self.id = roomId.lowercaseString
        self.topic = topic
        self.flags |= local ? ExpLeagueOrderFlags.LocalTask.rawValue : 0
        self.flags |= specific ? ExpLeagueOrderFlags.SpecificTask.rawValue : 0
        self.flags += ExpLeagueOrder.urgencyDict[urgency]!
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
        
    }
    
    var stream: XMPPStream {
        return AppDelegate.instance.stream
    }
    func message(index: Int) -> ExpLeagueMessage {
        return messagesRaw[index] as! ExpLeagueMessage
    }
    
    func message(var message msg: XMPPMessage) {
        if (msg.elementsForName("body").count > 0 && msg.body().containsString("Welcome to room \(id)")) { // initial message
            msg = XMPPMessage(type: "groupchat", to: jid)
            msg.addSubject(topic)
            stream.sendElement(msg)
        }

        let message = ExpLeagueMessage(msg: msg, parent: self, context: self.managedObjectContext!)
        let mutableItems = messagesRaw.mutableCopy() as! NSMutableOrderedSet
        mutableItems.addObject(message)
        messagesRaw = mutableItems.copy() as! NSOrderedSet
        do {
            try self.managedObjectContext!.save()
        } catch {
            fatalError("Failure to save context: \(error)")
        }
        AppDelegate.instance.messagesView?.onMessage(message)
    }
    
    func iq(iq iq: XMPPIQ) {
    }
    
    func presence(presence presence: XMPPPresence) {
        
    }
    
    func send(text: String) {
        let msg = XMPPMessage(type: "groupchat", to: jid)
        msg.addBody(text)
        message(message:msg)
        stream.sendElement(msg)
    }
    
    var jid : XMPPJID {
        return XMPPJID.jidWithString(id + "@muc." + AppDelegate.instance.activeProfile!.domain)
    }
    
    static let urgencyDict : [String: Int16] = [
        "asap" : 256,
        "day" : 128,
        "week" : 0
    ]
}

extension ExpLeagueOrder: JSQMessagesCollectionViewDataSource {
    func senderId() -> String! {
        return "me"
    }
    
    func senderDisplayName() -> String! {
        return "Я"
    }

    func collectionView(collectionView: JSQMessagesCollectionView!, messageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageData! {
        return message(indexPath.item).jsq()
    }
    
    func collectionView(collectionView: JSQMessagesCollectionView!, didDeleteMessageAtIndexPath indexPath: NSIndexPath!) {
        // must not happen
    }
    
    @nonobjc static let systemColor = UIColor(red: 1.0, green: 0.2, blue: 0, alpha: 0.1)
    @nonobjc static let clientColor = UIColor(red: 0, green: 0.2, blue: 1.0, alpha: 0.05)
    @nonobjc static let expertColor = UIColor(red: 238.0/256.0, green: 238.0/256.0, blue: 238.0/256.0, alpha: 1)
    @nonobjc static let topicColor = UIColor(red: 0, green: 1.0, blue: 0, alpha: 0.1)
    
    @nonobjc static let topicBubbleImage = JSQMessagesBubbleImageFactory().outgoingMessagesBubbleImageWithColor(ExpLeagueOrder.topicColor)
    @nonobjc static let outgoingBubbleImage = JSQMessagesBubbleImageFactory().outgoingMessagesBubbleImageWithColor(ExpLeagueOrder.clientColor)
    @nonobjc static let incomingBubbleImage = JSQMessagesBubbleImageFactory().incomingMessagesBubbleImageWithColor(ExpLeagueOrder.expertColor)
    @nonobjc static let systemBubbleImage = JSQMessagesBubbleImageFactory().incomingMessagesBubbleImageWithColor(ExpLeagueOrder.systemColor)
    
    func collectionView(collectionView: JSQMessagesCollectionView!, messageBubbleImageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageBubbleImageDataSource! {
        switch (message(indexPath.item).type) {
        case .TopicStarter:
            return ExpLeagueOrder.topicBubbleImage
        case .ExpertMessage:
            return ExpLeagueOrder.incomingBubbleImage
        case .ClientMessage:
            return ExpLeagueOrder.outgoingBubbleImage
        case .SystemMessage:
            return ExpLeagueOrder.systemBubbleImage
        }
    }
    
    func collectionView(collectionView: JSQMessagesCollectionView!, avatarImageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageAvatarImageDataSource! {
        return message(indexPath.item).jsq().avatar
    }

    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : messagesRaw.count
    }
    
    
    var outgoingCellIdentifier: String {
        return JSQMessagesCollectionViewCellOutgoing.cellReuseIdentifier()
    }
    var outgoingMediaCellIdentifier: String {
        return JSQMessagesCollectionViewCellOutgoing.mediaCellReuseIdentifier()
    }
    var incomingCellIdentifier: String {
        return JSQMessagesCollectionViewCellIncoming.cellReuseIdentifier()
    }
    var incomingMediaCellIdentifier: String {
        return JSQMessagesCollectionViewCellIncoming.mediaCellReuseIdentifier()
    }
    
    func collectionView(cv: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let collectionView = cv as! JSQMessagesCollectionView
        let message = self.message(indexPath.item)
        let messageItem = message.jsq()
        
        let isOutgoingMessage = !message.incoming;
        let isMediaMessage = messageItem.isMediaMessage()
        
        let cellIdentifier : String;
        if (isMediaMessage) {
            cellIdentifier = isOutgoingMessage ? self.outgoingMediaCellIdentifier : self.incomingMediaCellIdentifier
        }
        else {
            cellIdentifier = isOutgoingMessage ? self.outgoingCellIdentifier : self.incomingCellIdentifier
        }
        
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier(cellIdentifier, forIndexPath:indexPath) as! JSQMessagesCollectionViewCell
        cell.delegate = collectionView
        
        if (!isMediaMessage) {
            cell.textView!.text = messageItem.text()
            
            if (UIDevice.jsq_isCurrentDeviceBeforeiOS8()) {
                //  workaround for iOS 7 textView data detectors bug
                cell.textView!.text = nil;
                cell.textView!.attributedText = NSAttributedString(string: messageItem.text(), attributes: [
                    NSFontAttributeName: collectionView.collectionViewLayout.messageBubbleFont
                ]);
            }
            
            let bubble = self.collectionView(collectionView, messageBubbleImageDataForItemAtIndexPath: indexPath)
            cell.messageBubbleImageView!.image = bubble.messageBubbleImage()
            cell.messageBubbleImageView!.highlightedImage = bubble.messageBubbleHighlightedImage()
        }
//        else {
//            let messageMedia = messageItem.media()
//            cell.mediaView = [messageMedia mediaView] ?: [messageMedia mediaPlaceholderView];
//            NSParameterAssert(cell.mediaView != nil);
//        }
        
        var needsAvatar = true;
        if (isOutgoingMessage && CGSizeEqualToSize(collectionView.collectionViewLayout.outgoingAvatarViewSize, CGSizeZero)) {
            needsAvatar = false;
        }
        else if (!isOutgoingMessage && CGSizeEqualToSize(collectionView.collectionViewLayout.incomingAvatarViewSize, CGSizeZero)) {
            needsAvatar = false;
        }
        
        if (needsAvatar) {
            let avatarImage = messageItem.avatar;
            cell.avatarImageView!.image = avatarImage.avatarImage();
            cell.avatarImageView!.highlightedImage = avatarImage.avatarHighlightedImage()
        }
        
        cell.cellTopLabel!.attributedText = self.collectionView(collectionView, attributedTextForCellTopLabelAtIndexPath:indexPath);
//        cell.messageBubbleTopLabel.attributedText = [collectionView.dataSource collectionView:collectionView attributedTextForMessageBubbleTopLabelAtIndexPath:indexPath];
//        cell.cellBottomLabel!.attributedText = self.collectionView(collectionView, attributedTextForCellBottomLabelAtIndexPath:indexPath);
        
        let bubbleTopLabelInset = CGFloat(needsAvatar ? 60.0 : 15.0)
        
        if (isOutgoingMessage) {
            cell.messageBubbleTopLabel!.textInsets = UIEdgeInsetsMake(0.0, 0.0, 0.0, bubbleTopLabelInset);
        }
        else {
            cell.messageBubbleTopLabel!.textInsets = UIEdgeInsetsMake(0.0, bubbleTopLabelInset, 0.0, 0.0);
        }
        
        cell.textView!.dataDetectorTypes = .All
        cell.backgroundColor = UIColor.clearColor();
        cell.layer.rasterizationScale = UIScreen.mainScreen().scale;
        cell.layer.shouldRasterize = true
        
        cell.textView!.textColor = UIColor.blackColor()
        
        let attributes : [String:AnyObject] = [NSForegroundColorAttributeName:cell.textView!.textColor!, NSUnderlineStyleAttributeName: 1]
        cell.textView!.linkTextAttributes = attributes
        return cell
    }
    
    // View  usernames above bubbles
    func collectionView(collectionView: JSQMessagesCollectionView!, attributedTextForCellTopLabelAtIndexPath indexPath: NSIndexPath!) -> NSAttributedString! {
        let message = self.message(indexPath.item).jsq()
        // Sent by me, skip
        if !message.incoming {
            return nil;
        }
        
        // Same as previous sender, skip
        if indexPath.item > 0 {
            let previousMessage = self.message(indexPath.item - 1).jsq();
            if previousMessage.senderId() == message.senderId() {
                return nil;
            }
        }
        
        return NSAttributedString(string:message.senderDisplayName())
    }
    
    func collectionView(collectionView: JSQMessagesCollectionView!, layout collectionViewLayout: JSQMessagesCollectionViewFlowLayout!, heightForMessageBubbleTopLabelAtIndexPath indexPath: NSIndexPath!) -> CGFloat {
        let message = self.message(indexPath.item).jsq()
        
        // Sent by me, skip
        if !message.incoming {
            return CGFloat(0.0);
        }
        
        // Same as previous sender, skip
        if indexPath.item > 0 {
            let previousMessage = self.message(indexPath.item - 1).jsq();
            if previousMessage.senderId() == message.senderId() {
                return CGFloat(0.0);
            }
        }
        
        return kJSQMessagesCollectionViewCellLabelHeightDefault
    }
}

enum ExpLeagueOrderFlags: Int16 {
    case LocalTask = 16384
    case SpecificTask = 8196
}
