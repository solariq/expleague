//
//  MessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class MessagesVeiwController: UIViewController {
    var order : ExpLeagueOrder?
    
    let placeHolder = UIView();
    let scrollView = UIScrollView()
    let input = ChatInputViewController(nibName: "ChatInput", bundle: nil)
    let messagesView = UITableView();
    let answerView = UIWebView();
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        edgesForExtendedLayout = .Bottom
        automaticallyAdjustsScrollViewInsets = false
        addChildViewController(input)
        input.didMoveToParentViewController(self)
        view.addSubview(scrollView)
        scrollView.addSubview(messagesView)
        scrollView.addSubview(input.view)
        scrollView.addSubview(answerView)
        scrollView.pagingEnabled = true
        scrollView.clipsToBounds = false
        input.text.layer.borderWidth = 2
        input.text.layer.borderColor = UIColor.lightGrayColor().CGColor
        input.text.layer.cornerRadius = 4
        answerView.loadRequest(NSURLRequest(URL: NSURL(string: "http://google.com")!))

        messagesView.registerNib(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Incoming))
        messagesView.registerNib(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Outgoing))
        messagesView.separatorStyle = .None
        messagesView.backgroundColor = UIColor(red: 218.0/256.0, green: 234.0/256.0, blue: 239.0/256.0, alpha: 1.0)
        messagesView.backgroundView = nil


        AppDelegate.instance.messagesView = self
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
    }
    
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    func onMessage(msg: ExpLeagueMessage) {
        if (msg.parent != order) {
            return
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
    
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        print(size);
    }
    

    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        let data: DemoChatMessages
        if (order == nil) {
            data = DemoChatMessages()
        }
        else {
            data = DemoChatMessages()
        }
        messagesView.dataSource = data
        messagesView.delegate = data


        tabBar.hidden = true;
//        if (order == nil) {
//            let orders = AppDelegate.instance.activeProfile!.orders
//            order = orders.count > 0 ? orders[orders.count - 1] as? ExpLeagueOrder : nil
//        }
//        collectionView?.collectionViewLayout!.messageBubbleFont = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
//        collectionView?.dataSource = order

        
        if (order?.topic.characters.count > 15) {
            self.title = order!.topic.substringToIndex(order!.topic.startIndex.advancedBy(15)) + "..."
        }
        else {
            self.title = order?.topic
        }
        self.title = order?.topic
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        adjustSizes()
    }
    
    func adjustSizes() {
        print(input.view.frame)
        let inputHeight = input.view.frame.height
        
        scrollView.frame = CGRectMake(0, 0, view.frame.width, view.frame.height)
        messagesView.frame = CGRectMake(0, 0, scrollView.frame.width, scrollView.frame.height - inputHeight)
        input.view.frame = CGRectMake(0, messagesView.frame.maxY, scrollView.frame.width, inputHeight)
        answerView.frame = CGRectMake(0, input.view.frame.maxY, scrollView.frame.width, scrollView.frame.height - inputHeight)
        scrollView.contentSize = CGSizeMake(scrollView.frame.width, messagesView.frame.height + answerView.frame.height + input.view.frame.height)
    }
}

class ChatMessage {
    let defaultFont: UIFont = UIFont(name: "Helvetica Neue", size: 12)!
    var separatorHeight: CGFloat = 8
    
    private var parts:[AnyObject] = []
    private var timeStamps: [NSTimeInterval] = []
    private var incoming = false
    
    func append(text text: String, time: NSTimeInterval) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(richText text: NSAttributedString, time: NSTimeInterval) {
        parts.append(text)
        timeStamps.append(time)
    }

    func size(width width: CGFloat) throws -> CGSize {
        var size = CGSizeMake(0, 0)
        for i in 0 ..< parts.count {
            if (i > 0) {
                size.height += separatorHeight
            }
            let blockSize = self.blockSize(width: width, index: i)
            size.width = max(size.width, blockSize.width)
            size.height += blockSize.height
        }
        return size;
    }

    private weak var cellInner: MessageView?
    var cell: MessageView? {
        get {
            return cellInner
        }
        set(cell) {
            var currentVOffset: CGFloat = 0.0
            for i in 0 ..< parts.count {
                if (i > 0) {
                    currentVOffset += separatorHeight
                }
                var block: UIView?
                if let text = parts[i] as? String {
                    let label = UILabel()
                    label.text = text
                    label.font = defaultFont
                    label.textColor = cell!.incoming ? UIColor.whiteColor() : UIColor.blackColor()
                    block = label
                }
                else if let richText = parts[i] as? NSAttributedString {
                    let label = UILabel()
                    label.attributedText = richText
                    label.font = defaultFont
                    label.textColor = cell!.incoming ? UIColor.whiteColor() : UIColor.blackColor()
                    block = label
                }
                else if let image = parts[i] as? UIImage {
                    let imageView = UIImageView()
                    imageView.image = image
                    block = imageView
                }
                if (block != nil) {
                    cell!.content.addSubview(block!)
                    block!.frame.origin = CGPointMake(0, currentVOffset)
                    let blockSize = self.blockSize(width: cell!.contentWidth, index: i)
                    block!.frame.size = blockSize
                    currentVOffset += blockSize.height
                }
            }
        }
    }
    
    private func blockSize(width width: CGFloat, index: Int) -> CGSize {
        let blockSize: CGSize
        if let text = parts[index] as? String {
            blockSize = text.boundingRectWithSize(
                CGSizeMake(width, CGFloat(MAXFLOAT)),
                options: NSStringDrawingOptions.UsesLineFragmentOrigin,
                attributes: [
                    NSFontAttributeName : defaultFont],
                context: nil).size
        }
        else if let richText = parts[index] as? NSAttributedString {
            blockSize = richText.boundingRectWithSize(
                CGSizeMake(width, CGFloat(MAXFLOAT)),
                options: NSStringDrawingOptions.UsesLineFragmentOrigin,
                context: nil).size
        }
        else if let image = parts[index] as? UIImage {
            var bs = image.size
            bs.height *= bs.width / width
            blockSize = bs
        }
        else {
            blockSize = CGSizeMake(0, 0)
        }
        return blockSize
    }
}

@objc
class DemoChatMessages: NSObject, UITableViewDataSource, UITableViewDelegate {
    override init() {
        super.init()
        var msg = ChatMessage()
        msg.incoming = true;
        msg.append(text: "Hello", time: NSDate().timeIntervalSince1970)
        messages.append(msg)
        msg = ChatMessage()
        msg.incoming = false;
        msg.append(richText: NSAttributedString(string: "Hello! Long long long text here, Long long long text here.", attributes: [
            NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleHeadline)
        ]), time: NSDate().timeIntervalSince1970)
        messages.append(msg)
    }
    
    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section > 0 ? 0 : messages.count;
    }
    
    let messages: [ChatMessage] = [];
    
    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let message = messages[indexPath.item]
        if (message.cell != nil) {
            return message.cell!
        }
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.incoming ? CellType.Incoming : CellType.Outgoing), forIndexPath: indexPath) as! MessageView
        message.cell = cell
        return cell
    }
    
    var model: MessageView?
    func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        let message = messages[indexPath.item]
        if (message.cell != nil) {
            return message.cell!.frame.height
        }
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.incoming ? CellType.Incoming : CellType.Outgoing), forIndexPath: indexPath) as! MessageView
        message.cell = cell
        return message.size(width: CGFloat)
    }
}

enum CellType: Int {
    case Incoming = 0
    case Outgoing = 1
}
