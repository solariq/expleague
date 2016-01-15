//
//  MessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import JSQMessagesViewController

class ELMessagesVeiwController: JSQMessagesViewController {
    var order : ExpLeagueOrder?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        inputToolbar!.contentView!.leftBarButtonItem = nil
        automaticallyScrollsToMostRecentMessage = true
        senderId = "Я"
        senderDisplayName = "Я"
    }
    
    func onMessage(msg: ExpLeagueMessage) {
        if (msg.parent != order) {
            return
        }
        if (msg.incoming) {
            self.finishReceivingMessage()
        }
        else {
            self.finishSendingMessage()
        }
    }

    var tabBar: UIKit.UITabBar {
        get {
            return (UIApplication.sharedApplication().delegate as! AppDelegate).tabs.tabBar
        }
    }
    override func viewWillDisappear(animated: Bool) {
        tabBar.hidden = false;
    }

    override func viewWillAppear(animated: Bool) {
        tabBar.hidden = true;
        collectionView?.dataSource = order
        if (order?.topic.characters.count > 15) {
            navigationItem.title = order!.topic.substringToIndex(order!.topic.startIndex.advancedBy(15)) + "..."
        }
        else {
            navigationItem.title = order?.topic
        }
        navigationItem.title = order?.topic
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        collectionView!.collectionViewLayout.springinessEnabled = true
    }
    
    override func didPressSendButton(button: UIButton!, withMessageText text: String!, senderId: String!, senderDisplayName: String!, date: NSDate!) {
        JSQSystemSoundPlayer.jsq_playMessageSentSound()
        if let o = order {
            o.send(text)
        }
        else {
            let controller = UIAlertController(
                title: "Experts League",
                message: "No order selected to send the message!",
                preferredStyle: UIAlertControllerStyle.Alert)
            controller.addAction(UIAlertAction(title: "Dismiss", style: UIAlertActionStyle.Default, handler: nil))
            presentViewController(controller, animated: true, completion: nil)
        }
    }

    override func didPressAccessoryButton(sender: UIButton!) {
        print("Camera pressed!")
    }
}