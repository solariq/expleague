//
//  MessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class MessagesVeiwController: UIViewController, ChatInputDelegate {
    var order : ExpLeagueOrder?
    
    let placeHolder = UIView();
    let scrollView = UIScrollView()
    let input = ChatInputViewController(nibName: "ChatInput", bundle: nil)
    let messagesView = UITableView();
    private let answerView = UIWebView();
    var data: ChatMessagesModel?
    
    private var answerText: String = "<html><body>"
    
    func answerAppend(text: String) {
        answerText += text;
        answerView.loadHTMLString(answerText, baseURL: nil)
    }
    
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
        input.delegate = self;
        answerView.loadHTMLString("", baseURL: nil)
        messagesView.registerNib(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Incoming))
        messagesView.registerNib(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Outgoing))
        messagesView.separatorStyle = .None
        messagesView.backgroundColor = UIColor(red: 218.0/256.0, green: 234.0/256.0, blue: 239.0/256.0, alpha: 1.0)
        scrollView.backgroundColor = messagesView.backgroundColor

        messagesView.backgroundView = nil
        AppDelegate.instance.messagesView = self
        
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
    }
    
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool {
        if (data?.order != nil) {
            data?.order?.send(text)
        }
        else {
            let message = ChatMessage()
            message.append(text: text, time: NSDate().timeIntervalSince1970)
            data?.messages.append(message)
            messagesView.reloadData()
            scrollView.scrollRectToVisible(messagesView.frame, animated: true)
            dispatch_async(dispatch_get_main_queue(), {
                self.data?.scrollToLastMessage(self.messagesView)
            })
        }
        return true
    }

    func onMessage(msg: ExpLeagueMessage) {
        if (msg.parent != order) {
            return
        }
        data?.sync(msg.parent)
        messagesView.reloadData()
        data?.scrollToLastMessage(messagesView)
        scrollView.scrollRectToVisible(messagesView.frame, animated: true)
    }

    var tabBar: UIKit.UITabBar {
        get {
            return (UIApplication.sharedApplication().delegate as! AppDelegate).tabs.tabBar
        }
    }
    
    override func viewWillDisappear(animated: Bool) {
        tabBar.hidden = false;
        NSNotificationCenter.defaultCenter().removeObserver(self);
    }
    
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animateAlongsideTransition({(context: UIViewControllerTransitionCoordinatorContext) -> Void in
                self.adjustSizes()
                self.messagesView.reloadData()
            }, completion: nil
        )
        print("Transformation to new size: \(size)");
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        answerText = "<html><body>"
        answerAppend("")
        if (order == nil) {
            data = DemoChatMessagesModel(parent: self)
        }
        else {
            data = ChatMessagesModel(order: order, parent: self)
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
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardShown:", name: UIKeyboardDidShowNotification, object: nil)
        adjustSizes()
    }

    func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameBeginUserInfoKey] as! NSValue).CGRectValue().size
        let insets = UIEdgeInsetsMake(scrollView.contentInset.top, scrollView.contentInset.left, kbSize.height, scrollView.contentInset.right)
        scrollView.contentInset = insets
        scrollView.scrollIndicatorInsets = insets
        adjustSizes()
        scrollView.scrollRectToVisible(messagesView.frame, animated: true)
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
//        let kbSize = (notification.userInfo![UIKeyboardFrameBeginUserInfoKey] as! NSValue).CGRectValue().size
        let insets = UIEdgeInsetsMake(scrollView.contentInset.top, scrollView.contentInset.left, 0, scrollView.contentInset.right)
        scrollView.contentInset = insets
        scrollView.scrollIndicatorInsets = insets
        adjustSizes()
    }

    func adjustSizes() {
        let inputHeight = input.view.frame.height
        scrollView.frame = CGRectMake(0, 0, view.frame.width, view.frame.height)
        let visibleSize = CGSizeMake(scrollView.frame.width - scrollView.contentInset.right - scrollView.contentInset.left, scrollView.frame.height - scrollView.contentInset.bottom - scrollView.contentInset.top)
        messagesView.frame = CGRectMake(0, 0, visibleSize.width, visibleSize.height - inputHeight)
        input.view.frame = CGRectMake(0, messagesView.frame.maxY, visibleSize.width, inputHeight)
        answerView.frame = CGRectMake(0, input.view.frame.maxY, visibleSize.width, visibleSize.height - inputHeight)
        scrollView.contentSize = CGSizeMake(scrollView.frame.width, messagesView.frame.height + answerView.frame.height + input.view.frame.height)
    }
}

class ChatAction: NSObject {
    let action: ()->Void
    let caption: String
    
    init(action: () -> Void, caption: String) {
        self.action = action
        self.caption = caption
    }
    
    func push() {
        action()
    }
}

class ChatMessage {
    let defaultFont: UIFont = UIFont(name: "Helvetica Neue", size: 14)!
    let separatorHeight: CGFloat = 8
    
    var author: String?
    
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

    func append(image img: UIImage, time: NSTimeInterval) {
        parts.append(img)
        timeStamps.append(time)
    }

    func append(action run: () -> Void, caption: String, time: NSTimeInterval) {
        parts.append(ChatAction(action: run, caption: caption))
        timeStamps.append(time)
    }

    func size(width width: CGFloat) -> CGSize {
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

    private var cellInner: MessageView?
    var cell: MessageView? {
        get {
            return cellInner
        }
        set(cell) {
            for view in cell!.content.subviews{
                view.removeFromSuperview()
            }
            cell!.content.autoresizesSubviews = false
            cell!.content.autoresizingMask = .None
            var height: CGFloat = 0.0
            var width: CGFloat = 0.0
            cell!.content.frame.size = size(width: cell!.contentWidth)

            for i in 0 ..< parts.count {
                if (i > 0) {
                    height += separatorHeight
                }
                var block: UIView?
                if let text = parts[i] as? String {
                    let label = UILabel()
                    label.text = text
                    label.font = defaultFont
                    label.textColor = cell!.incoming ? UIColor.whiteColor() : UIColor.blackColor()
                    label.lineBreakMode = .ByWordWrapping
                    label.textAlignment = .Left
                    label.numberOfLines = 0
                    block = label
                }
                else if let richText = parts[i] as? NSAttributedString {
                    let label = UILabel()
                    label.attributedText = richText
                    label.textColor = cell!.incoming ? UIColor.whiteColor() : UIColor.blackColor()
                    label.lineBreakMode = .ByWordWrapping
                    label.textAlignment = .Left
                    label.numberOfLines = 0
                    block = label
                }
                else if let image = parts[i] as? UIImage {
                    let imageView = UIImageView()
                    imageView.image = image
                    block = imageView
                }
                else if let action = parts[i] as? ChatAction {
                    let button = UIButton(type: .Custom)
                    button.setTitle(action.caption, forState: .Normal)
                    button.addTarget(action, action: "push", forControlEvents: [.PrimaryActionTriggered])
                    block = button
                }
                if (block != nil) {
                    cell!.content.addSubview(block!)
                    block!.frame.origin = CGPointMake(0, height)
                    let blockSize = self.blockSize(width: cell!.contentWidth, index: i)
                    block!.frame.size = blockSize
                    height += blockSize.height
                    width = max(width, blockSize.width)
                }
            }
            cell!.contentSize = CGSizeMake(width, height)
        }
    }
    
    private func blockSize(width width: CGFloat, index: Int) -> CGSize {
        let blockSize: CGSize
        if let text = parts[index] as? String {
            blockSize = text.boundingRectWithSize(
                CGSizeMake(width, CGFloat(MAXFLOAT)),
                options: NSStringDrawingOptions.UsesLineFragmentOrigin,
                attributes: [
                    NSFontAttributeName : defaultFont
                ],
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
        else if let _ = parts[index] as? ChatAction {
            blockSize = CGSizeMake(width - 10, 40)
        }
        else {
            blockSize = CGSizeMake(0, 0)
        }
        return blockSize
    }
}

@objc
class ChatMessagesModel: NSObject, UITableViewDataSource, UITableViewDelegate {
    var lastKnownMessage: Int = 0
    let order: ExpLeagueOrder?
    let parent: MessagesVeiwController
    init(order: ExpLeagueOrder?, parent: MessagesVeiwController) {
        self.order = order
        self.parent = parent
        super.init()
        if (order == nil) {
            return
        }
        sync(order!)
    }
    
    class AnswerVisitor: ExpLeagueMessageVisitor {
        let parent: MessagesVeiwController
        init(parent: MessagesVeiwController) {
            self.parent = parent;
        }
        
        func message(message: ExpLeagueMessage, text: String) {
            parent.answerAppend("<p>\(text)</p>';")
        }
        
        func message(message: ExpLeagueMessage, title: String, text: String) {
            parent.answerAppend("<h3>\(title)</h3><p>\(text)</p>")
        }
        
        func message(message: ExpLeagueMessage, title: String, link: String) {
            parent.answerAppend("<a href=\"\(link)\">\(title)</a>")
        }
        func message(message: ExpLeagueMessage, title: String, image: UIImage) {
            let data = UIImageJPEGRepresentation(image, 1.0)!
            parent.answerAppend("<h3>\(title)</h3><img align='middle' src='data:image/jpeg;base64,\(data.base64EncodedStringWithOptions([]))'/>")
        }
    }
    
    class MessageVisitor: ExpLeagueMessageVisitor {
        let model: ChatMessage
        init(model: ChatMessage) {
            self.model = model;
        }
        func message(message: ExpLeagueMessage, text: String) {
            model.append(text: text, time: message.time)
        }
        
        func message(message: ExpLeagueMessage, title: String, text: String) {
            let result = NSMutableAttributedString()
            result.appendAttributedString(NSAttributedString(string: title, attributes: [
                NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleHeadline)
                ]))
            result.appendAttributedString(NSAttributedString(string: "\n" + text, attributes: [
                NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
                ]))
            model.append(richText: result, time: message.time)
        }
        
        func message(message: ExpLeagueMessage, title: String, link: String) {
            model.append(
                richText: NSAttributedString(string: title, attributes: [
                        NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody),
                        NSLinkAttributeName: link,
                        NSForegroundColorAttributeName: UIColor.blueColor()
                    ]),
                time: message.time)
        }
        func message(message: ExpLeagueMessage, title: String, image: UIImage) {
            model.append(
                richText: NSAttributedString(
                    string: title,
                    attributes: [
                        NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody),
                        NSForegroundColorAttributeName: UIColor.blueColor()]),
                time: message.time
            )
            model.append(image: image, time: message.time)
        }
    }
    
    func sync(order: ExpLeagueOrder) {
        var model: ChatMessage? = messages.last
        while (lastKnownMessage < order.count) {
            let msg = order.message(lastKnownMessage)
            if (model == nil || model!.author != msg.from) {
                model = ChatMessage()
                model!.incoming = msg.incoming
                model!.author = msg.from
                messages.append(model!)
            }
            if (!msg.isAnswer) {
                msg.visitParts(MessageVisitor(model: model!))
            }
            else {
                let id = "message-\(self.lastKnownMessage)"
                self.parent.answerAppend("<div id=\"\(id)\"/>")
                model!.append(
                    action: {
                        self.parent.scrollView.scrollRectToVisible(self.parent.answerView.frame, animated: true)
                        self.parent.answerView.stringByEvaluatingJavaScriptFromString("document.getElementById('\(id)').scrollIntoView()")
                    },
                    caption: "Получен ответ",
                    time: msg.time)

                msg.visitParts(AnswerVisitor(parent: parent))
            }
            lastKnownMessage++
        }
    }

    var messages: [ChatMessage] = [];
    
    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section > 0 ? 0 : messages.count;
    }
    
    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let message = messages[indexPath.item]
        if (message.cell != nil) {
            return message.cell!
        }
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.incoming ? CellType.Incoming : CellType.Outgoing), forIndexPath: indexPath) as! MessageView
        message.cell = cell
        return cell
    }
    
    func tableView(tableView: UITableView, willDisplayCell cell: UITableViewCell, forRowAtIndexPath indexPath: NSIndexPath) {
        cell.backgroundColor = UIColor.clearColor()
        cell.contentView.backgroundColor = UIColor.clearColor()
    }
    
    var models: [CellType: MessageView] = [:]
    func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        let message = messages[indexPath.item]
        if (message.cell != nil) {
            return message.cell!.frame.height
        }
        if (models[message.incoming ? .Incoming : .Outgoing] == nil) {
            models[.Outgoing] = (tableView.dequeueReusableCellWithIdentifier(String(CellType.Outgoing)) as! MessageView)
            models[.Incoming] = (tableView.dequeueReusableCellWithIdentifier(String(CellType.Incoming)) as! MessageView)
        }
        let model = models[message.incoming ? .Incoming : .Outgoing]!
        let height = max(35 + 8, model.extraHeight + message.size(width: model.contentWidth).height)
        //        let height = 24 + message.size(width: tableView.frame.size.width - 35 - 18 - 16).height
        return height
    }

    func scrollToLastMessage(tableView: UITableView) {
        if (messages.count > 0) {
            tableView.scrollToRowAtIndexPath(NSIndexPath(forItem: messages.count - 1, inSection: 0), atScrollPosition: .Top, animated: true)
        }
    }

}


@objc
class DemoChatMessagesModel: ChatMessagesModel {
    init(parent: MessagesVeiwController) {
        super.init(order: nil, parent: parent)
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
        let title = "Well, come"
        let text = "Come come come"
        parent.answerAppend("<h3>\(title)</h3><p>\(text)</p>'")
    }
}

enum CellType: Int {
    case Incoming = 0
    case Outgoing = 1
}
