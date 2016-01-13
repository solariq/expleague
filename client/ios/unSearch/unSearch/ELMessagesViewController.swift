//
//  ELMessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import JSQMessagesViewController

class ELMessagesVeiwController: JSQMessagesViewController {
    var order: ELOrder?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        inputToolbar!.contentView!.leftBarButtonItem = nil
        automaticallyScrollsToMostRecentMessage = true
        ELConnection.instance.outgoingAvaWidth = UInt(collectionView!.collectionViewLayout.outgoingAvatarViewSize.width)
        ELConnection.instance.incomingAvaWidth = UInt(collectionView!.collectionViewLayout.incomingAvatarViewSize.width)
        order!.onChange(self, callback: {
            self.finishReceivingMessage()
        })
//        navigationController?.navigationBar.topItem?.title = "Logout"
//
//        sender = (sender != nil) ? sender : "Anonymous"
//        let profileImageUrl = user?.providerData["cachedUserProfile"]?["profile_image_url_https"] as? NSString
//        if let urlString = profileImageUrl {
//            setupAvatarImage(sender, imageUrl: urlString as String, incoming: false)
//            senderImageUrl = urlString as String
//        } else {
//            setupAvatarColor(sender, incoming: false)
//            senderImageUrl = ""
//        }
        
    }
    
    override func viewWillDisappear(animated: Bool) {
        order!.unbind(self)
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        collectionView!.collectionViewLayout.springinessEnabled = true
    }
    
    override func didPressSendButton(button: UIButton!, withMessageText text: String!, senderId: String!, senderDisplayName: String!, date: NSDate!) {
        JSQSystemSoundPlayer.jsq_playMessageSentSound()
        
        order!.send(text)
        
        finishSendingMessage()
    }

    override func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return order!.size()
    }

    
    override func didPressAccessoryButton(sender: UIButton!) {
        print("Camera pressed!")
    }
    
    override func collectionView(collectionView: JSQMessagesCollectionView!, messageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageData! {
        return order!.messageAsJSQ(indexPath.item)
    }
    
    let outgoingBubbleImage = JSQMessagesBubbleImageFactory().incomingMessagesBubbleImageWithColor(UIColor.jsq_messageBubbleLightGrayColor())
    let incomingBubbleImage = JSQMessagesBubbleImageFactory().outgoingMessagesBubbleImageWithColor(UIColor.jsq_messageBubbleGreenColor())
    
    override func collectionView(collectionView: JSQMessagesCollectionView!, messageBubbleImageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageBubbleImageDataSource! {
        return order!.messageAsJSQ(indexPath.item).incoming ? incomingBubbleImage : outgoingBubbleImage
    }
    
    override func collectionView(collectionView: JSQMessagesCollectionView!, avatarImageDataForItemAtIndexPath indexPath: NSIndexPath!) -> JSQMessageAvatarImageDataSource! {
        return order!.messageAsJSQ(indexPath.item).avatar
    }
    
    override func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = super.collectionView(collectionView, cellForItemAtIndexPath: indexPath) as! JSQMessagesCollectionViewCell
        
        let message = order!.messageAsJSQ(indexPath.item)
        if !message.incoming {
            cell.textView!.textColor = UIColor.blackColor()
        } else {
            cell.textView!.textColor = UIColor.whiteColor()
        }
        
        let attributes : [String:AnyObject] = [NSForegroundColorAttributeName:cell.textView!.textColor!, NSUnderlineStyleAttributeName: 1]
        cell.textView!.linkTextAttributes = attributes
        
        //        cell.textView.linkTextAttributes = [NSForegroundColorAttributeName: cell.textView.textColor,
        //            NSUnderlineStyleAttributeName: NSUnderlineStyle.StyleSingle]
        return cell
    }
    
    
    // View  usernames above bubbles
    override func collectionView(collectionView: JSQMessagesCollectionView!, attributedTextForMessageBubbleTopLabelAtIndexPath indexPath: NSIndexPath!) -> NSAttributedString! {
        let message = order!.messageAsJSQ(indexPath.item)
        // Sent by me, skip
        if !message.incoming {
            return nil;
        }
        
        // Same as previous sender, skip
        if indexPath.item > 0 {
            let previousMessage = order!.messageAsJSQ(indexPath.item - 1);
            if previousMessage.senderId() == message.senderId() {
                return nil;
            }
        }
        
        return NSAttributedString(string:message.senderDisplayName())
    }
    
    override func collectionView(collectionView: JSQMessagesCollectionView!, layout collectionViewLayout: JSQMessagesCollectionViewFlowLayout!, heightForMessageBubbleTopLabelAtIndexPath indexPath: NSIndexPath!) -> CGFloat {
        let message = order!.messageAsJSQ(indexPath.item)
        
        // Sent by me, skip
        if !message.incoming {
            return CGFloat(0.0);
        }
        
        // Same as previous sender, skip
        if indexPath.item > 0 {
            let previousMessage = order!.messageAsJSQ(indexPath.item - 1)
            if previousMessage.senderId() == message.senderId() {
                return CGFloat(0.0);
            }
        }
        
        return kJSQMessagesCollectionViewCellLabelHeightDefault
    }
}