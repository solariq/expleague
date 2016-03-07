//
//  MessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework

class MessagesVeiwController: UIViewController, ChatInputDelegate, ImageSenderQueue {
    var loaded = false
    let placeHolder = UIView()
    let scrollView = UIScrollView()
    let input = ChatInputViewController(nibName: "ChatInput", bundle: nil)
    let messagesView = UITableView()
    var answerView: UIWebView? = nil
    var answerDelegate: AnswerDelegate?
    let picker = UIImagePickerController()
    let progress = UIProgressView()
    var messagesViewHConstraint: NSLayoutConstraint?
    var answerViewHConstraint: NSLayoutConstraint?
    var inputViewHConstraint: NSLayoutConstraint?
    var scrollViewBottom: NSLayoutConstraint?

    var data: ChatMessagesModel? {
        didSet {
            messagesView.delegate = data
            messagesView.dataSource = data

            answerText = data!.answer
            if (loaded) {
                
                data!.controller = self
                messagesView.reloadData()
            }
        }
    }

    var answerText: String = "" {
        willSet (newValue){
            if (answerText != newValue) {
                let path = NSBundle.mainBundle().bundlePath
                let baseURL = NSURL.fileURLWithPath(path);
                answerView?.loadHTMLString("<html><head><script src=\"md-scripts.js\"></script>\n"
                    + "<link rel=\"stylesheet\" href=\"markdownpad-github.css\"></head>"
                    + "<body>\(newValue)</body></html>", baseURL: baseURL)
            }
        }
    }

    var pickerDelegate: ImagePickerDelegate?
    override func viewDidLoad() {
        super.viewDidLoad()
        
        edgesForExtendedLayout = .Bottom
        automaticallyAdjustsScrollViewInsets = false
        addChildViewController(input)
        input.didMoveToParentViewController(self)
        view.addSubview(scrollView)
        scrollView.addSubview(messagesView)
        scrollView.addSubview(progress)
        scrollView.addSubview(input.view)
        answerView = UIWebView()
        if (!answerText.isEmpty) {
            answerText += " "
        }
        answerDelegate = AnswerDelegate(parent: self)
        answerView?.delegate = answerDelegate
        scrollView.addSubview(answerView!)
        scrollView.pagingEnabled = true
        scrollView.clipsToBounds = false
//        scrollView.delegate = self
        input.text.layer.borderWidth = 2
        input.text.layer.borderColor = UIColor.lightGrayColor().CGColor
        input.text.layer.cornerRadius = 4
        input.delegate = self;
        progress.hidden = true
        messagesView.registerNib(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Incoming))
        messagesView.registerNib(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Outgoing))
        messagesView.registerNib(UINib(nibName: "LookingForExpert", bundle: nil), forCellReuseIdentifier: String(CellType.LookingForExpert))
        messagesView.registerNib(UINib(nibName: "AnswerReceived", bundle: nil), forCellReuseIdentifier: String(CellType.AnswerReceived))
        messagesView.registerNib(UINib(nibName: "ExpertInProgress", bundle: nil), forCellReuseIdentifier: String(CellType.ExpertInProgress))
        messagesView.registerNib(UINib(nibName: "Setup", bundle: nil), forCellReuseIdentifier: String(CellType.Setup))
        messagesView.registerNib(UINib(nibName: "Feedback", bundle: nil), forCellReuseIdentifier: String(CellType.Feedback))
        messagesView.separatorStyle = .None
        messagesView.backgroundColor = ChatCell.bgColor
        scrollView.backgroundColor = messagesView.backgroundColor
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        answerView!.translatesAutoresizingMaskIntoConstraints = false
        messagesView.translatesAutoresizingMaskIntoConstraints = false
        input.view!.translatesAutoresizingMaskIntoConstraints = false
        progress.translatesAutoresizingMaskIntoConstraints = false
        messagesViewHConstraint = NSLayoutConstraint(item: messagesView, attribute: .Height, relatedBy: .Equal, toItem: scrollView, attribute: .Height, multiplier: 1, constant:  -input.view.frame.height - 2)
        answerViewHConstraint = NSLayoutConstraint(item: answerView!, attribute: .Height, relatedBy: .Equal, toItem: scrollView, attribute: .Height, multiplier: 1, constant:  -input.view.frame.height - 2)
        scrollViewBottom = NSLayoutConstraint(item: scrollView, attribute: .Bottom, relatedBy: .Equal, toItem: view, attribute: .BottomMargin, multiplier: 1, constant: 0)

        inputViewHConstraint = NSLayoutConstraint(item: input.view!, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: input.view.frame.height)
        NSLayoutConstraint.activateConstraints([
            NSLayoutConstraint(item: scrollView, attribute: .Top, relatedBy: .Equal, toItem: view, attribute: .TopMargin, multiplier: 1, constant: 0),
            NSLayoutConstraint(item: scrollView, attribute: .Width, relatedBy: .Equal, toItem: view, attribute: .Width, multiplier: 1, constant: 0),
            scrollViewBottom!,

            NSLayoutConstraint(item: messagesView, attribute: .Top, relatedBy: .Equal, toItem: scrollView, attribute: .Top, multiplier: 1, constant: 0),
            NSLayoutConstraint(item: messagesView, attribute: .Width, relatedBy: .Equal, toItem: view, attribute: .Width, multiplier: 1, constant: 0),
            messagesViewHConstraint!,
            NSLayoutConstraint(item: messagesView, attribute: .Bottom, relatedBy: .Equal, toItem: progress, attribute: .Top, multiplier: 1, constant: 0),

            NSLayoutConstraint(item: progress, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: 2),
            NSLayoutConstraint(item: progress, attribute: .Width, relatedBy: .Equal, toItem: view, attribute: .Width, multiplier: 1, constant: 0),
            NSLayoutConstraint(item: progress, attribute: .Bottom, relatedBy: .Equal, toItem: input.view, attribute: .Top, multiplier: 1, constant: 0),

            inputViewHConstraint!,
            NSLayoutConstraint(item: input.view, attribute: .Width, relatedBy: .Equal, toItem: view, attribute: .Width, multiplier: 1, constant: 0),
            NSLayoutConstraint(item: input.view, attribute: .Bottom, relatedBy: .Equal, toItem: answerView, attribute: .Top, multiplier: 1, constant: 0),

            answerViewHConstraint!,
            NSLayoutConstraint(item: answerView!, attribute: .Width, relatedBy: .Equal, toItem: view, attribute: .Width, multiplier: 1, constant: 0),
            NSLayoutConstraint(item: answerView!, attribute: .Bottom, relatedBy: .Equal, toItem: scrollView, attribute: .Bottom, multiplier: 1, constant: 0),
        ])
        pickerDelegate = ImagePickerDelegate(queue: self, picker: picker)
        picker.delegate = pickerDelegate
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary
        messagesView.backgroundView = nil
        
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
        loaded = true
        if (data != nil) {
            data?.controller = self
        }
    }
    
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool {
        if (!AppDelegate.instance.stream.isConnected()) {
            let alertView = UIAlertController(title: "Experts League", message: "Connecting to server.\n\n", preferredStyle: .Alert)
            let completion = {
                //  Add your progressbar after alert is shown (and measured)
                let progressController = AppDelegate.instance.connectionProgressView
                let rect = CGRectMake(0, 54.0, alertView.view.frame.width, 50)
                progressController.completion = {
                    self.input.send(self)
                }
                progressController.view.frame = rect
                progressController.view.backgroundColor = alertView.view.backgroundColor
                alertView.view.addSubview(progressController.view)
                progressController.alert = alertView
                AppDelegate.instance.connect()
            }
            alertView.addAction(UIAlertAction(title: "Retry", style: .Default, handler: {(x: UIAlertAction) -> Void in
                AppDelegate.instance.disconnect()
                self.input.send(self)
            }))
            alertView.addAction(UIAlertAction(title: "Cancel", style: .Cancel, handler: nil))
            presentViewController(alertView, animated: true, completion: completion)
            return false
        }
        AppDelegate.instance.connect()
        data!.order.send(text: text)
        scrollView.setContentOffset(messagesView.frame.origin, animated: true)
        return true
    }

    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator: coordinator)
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardShown:", name: UIKeyboardWillShowNotification, object: nil)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardHidden:", name: UIKeyboardWillHideNotification, object: nil)
        if (data != nil) {
            data?.scrollToLastMessage(messagesView)
        }
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        tabBarController?.tabBar.hidden = true
        
        if (data == nil) {
            return
        }
        if (data!.order.text.characters.count > 15) {
            self.title = data!.order.text.substringToIndex(data!.order.topic.startIndex.advancedBy(15)) + "..."
        }
        else {
            self.title = data!.order.text
        }
        if (data!.order.isActive) {
            scrollView.setContentOffset(messagesView.frame.origin, animated: false)
        }
        else {
            scrollView.setContentOffset(progress.frame.origin, animated: false)
        }
    }

    override func viewWillDisappear(animated: Bool) {
        super.viewWillDisappear(animated)
        AppDelegate.instance.activeProfile!.selected = nil
        NSNotificationCenter.defaultCenter().removeObserver(self)
        data?.markAsRead()
    }

    
    func keyboardHidden(notification: NSNotification) {
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        self.scrollViewBottom!.constant = 0
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.view.layoutIfNeeded()
        }, completion: nil)
    }

    func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).CGRectValue().size
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        self.scrollViewBottom!.constant = -kbSize.height
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.view.layoutIfNeeded()
        }, completion: nil)
    }
    
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    func attach(input: ChatInputViewController) {
        self.presentViewController(picker, animated: true, completion: nil)
        progress.hidden = false
        progress.tintColor = UIColor.blueColor()
    }
    
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        progress(self.progress)
    }
    
    func report(id: String, status: Bool) {
        self.progress.tintColor = status ? UIColor.greenColor() : UIColor.redColor()
        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        img.setStringValue(AppDelegate.instance.activeProfile!.imageUrl(id).absoluteString)
        data!.order.send(xml: img)
    }
}

class AnswerDelegate: NSObject, UIWebViewDelegate {
    func webView(webView: UIWebView, shouldStartLoadWithRequest request: NSURLRequest, navigationType: UIWebViewNavigationType) -> Bool {
        
        if let url = request.URL where url.scheme == "unsearch" {
            if (url.path == "/chat-messages") {
                if let indexStr = url.fragment, index = Int(indexStr) {
                    parent.messagesView.scrollToRowAtIndexPath(NSIndexPath(forItem: index - 1, inSection: 0), atScrollPosition: .Middle, animated: false)
                    parent.scrollView.setContentOffset(parent.messagesView.frame.origin, animated: true)
                }
            }
            return false
        }
        else if let url = request.URL where url.scheme.hasPrefix("http") {
            UIApplication.sharedApplication().openURL(url)
            return false
        }

        return true
    }
    
    let parent: MessagesVeiwController
    init(parent: MessagesVeiwController) {
        self.parent = parent
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

@objc
class ChatMessagesModel: NSObject, UITableViewDataSource, UITableViewDelegate {
    private var lastKnownMessage: Int = 0
    private var haveActiveExpert = false
    let order: ExpLeagueOrder
    let lock: dispatch_queue_t

    weak var controller: MessagesVeiwController?
    var answer: String = "" {
        didSet {
            if let controller = self.controller {
                controller.answerText = answer
            }
        }
    }
    
    func markAsRead() {
        for i in 0..<order.count {
            let msg = order.message(i)
            if (!msg.isRead) {
                msg.setProperty("read", value: "true")
            }
        }
    }
    
    var progressModel: ExpertInProgressModel? = nil
    var progressCellIndex: Int?
    func sync() {
        dispatch_sync(lock){
            self.syncInner()
        }
    }
    
    private func syncInner() {
        if (cells.isEmpty) {
            cells.append(SetupModel(order: order))
        }
        let cellsCount = cells.count
        let startedFrom = lastKnownMessage
        var model = cells.last!
        var modelChangeCount = 0
        if (model is LookingForExpertModel || model is FeedbackModel) {
            cells.removeLast();
            model = cells.last!
        }

        while (lastKnownMessage < order.count) {
            if (modelChangeCount > 2) {
                print("Loop found! Enforce next message")
                lastKnownMessage++
            }
            let msg = order.message(lastKnownMessage)
            print("\(order.jid) -> \(msg.type)")
            if (!model.accept(msg)) { // switch model
                modelChangeCount++
                var newModel : ChatCellModel? = nil
                if (msg.type == .ExpertAssignment) {
                    haveActiveExpert = true
                    progressModel = ExpertInProgressModel(order: order)
                    progressCellIndex = cells.count
                    newModel = progressModel!
                }
                else if (msg.type == .ExpertCancel) {
                    if (progressCellIndex != nil) {
                        cells.removeAtIndex(progressCellIndex!)
                    }
                    haveActiveExpert = false
                    progressModel = nil
                    progressCellIndex = nil
                }
                else if (msg.type == .ExpertProgress) {
                    progressModel?.accept(msg)
                }
                else if (msg.type == .Answer) {
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += (msg.body!);
                    answer += "\n<a href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"
                    newModel = AnswerReceivedModel(id: id, progress: progressModel!)
                    progressModel = nil
                    if (progressCellIndex != nil) {
                        cells.removeAtIndex(progressCellIndex!)
                        progressCellIndex = nil
                    }
                    haveActiveExpert = false
                }
                else if (msg.type == .ExpertMessage) {
                    newModel = ChatMessageModel(incoming: true, author: msg.from)
                }
                else {
                    newModel = ChatMessageModel(incoming: false, author: "me")
                }
                if (newModel != nil) {
                    cells.append(newModel!)
                    model = cells.last!
                    continue
                }
            }
            lastKnownMessage++
            modelChangeCount = 0
        }
        if (!haveActiveExpert && order.isActive) {
            if !(cells.last! is FeedbackModel || cells.last! is LookingForExpertModel) {
                if (model is AnswerReceivedModel) {
                    cells.append(FeedbackModel(order: order))
                }
                else {
                    cells.append(LookingForExpertModel(order: order))
                }
            }
        }
        if(!order.isActive && progressModel != nil) {
            cells.removeAtIndex(progressCellIndex!)
            progressCellIndex = nil
            progressModel = nil
        }
        if(order.status == .Closed && cells.last! is FeedbackModel) {
            cells.removeLast()
        }

        if ((startedFrom != lastKnownMessage || cells.count != cellsCount) && controller != nil) {
            controller!.answerText = answer
            controller!.messagesView.reloadData()
            scrollToLastMessage(controller!.messagesView)
        }
    }

    var cells: [ChatCellModel] = [];
    
    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section > 0 ? 0 : cells.count;
    }
    
    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let message = cells[indexPath.item]
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.type), forIndexPath: indexPath) as! ChatCell
        cell.controller = controller
        cell.frame.size.width = tableView.frame.width
        try! message.form(chatCell: cell)
        return cell
    }
    
    func tableView(tableView: UITableView, willDisplayCell cell: UITableViewCell, forRowAtIndexPath indexPath: NSIndexPath) {
        cell.backgroundColor = UIColor.clearColor()
        cell.contentView.backgroundColor = UIColor.clearColor()
    }
    
    var modelCells: [CellType: ChatCell] = [:]
    func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        let model = cells[indexPath.item]
        var modelCell = modelCells[model.type]
        if modelCell == nil {
            modelCell = (tableView.dequeueReusableCellWithIdentifier(String(model.type)) as! ChatCell)
            modelCells[model.type] = modelCell
        }
        modelCell?.frame.size.width = tableView.frame.width
        if let compositeCell = modelCell as? CompositeChatCell {
            return model.height(maxWidth: compositeCell.maxContentSize.width)
        }
        else {
            return model.height(maxWidth: tableView.frame.width)
        }
    }

    func scrollToLastMessage(tableView: UITableView) {
        if (cells.count > 0) {
            tableView.scrollToRowAtIndexPath(NSIndexPath(forItem: cells.count - 1, inSection: 0), atScrollPosition: .Bottom, animated: true)
        }
    }
    
    init(order: ExpLeagueOrder) {
        self.order = order
        self.lock = dispatch_queue_create("com.expleague.LockQueue\(order.jid.user)", nil)
        super.init()
        order.model = self
        sync()
    }
}
