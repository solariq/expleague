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
    var data: ChatMessagesModel? {
        didSet {
            data!.controller = self
            messagesView.delegate = data
            messagesView.dataSource = data
            answerText = data!.answer
        }
    }

    var answerText: String = "" {
        willSet (newValue){
            if (answerText != newValue) {
                answerView?.loadHTMLString("<html><body>\(newValue)</body></html>", baseURL: nil)
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
            answerView!.loadHTMLString("<html><body>\(answerText)</body></html>", baseURL: nil)
        }
        answerDelegate = AnswerDelegate(parent: self)
        answerView?.delegate = answerDelegate
        scrollView.addSubview(answerView!)
        scrollView.pagingEnabled = true
        scrollView.clipsToBounds = false
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
        pickerDelegate = ImagePickerDelegate(queue: self, picker: picker)
        picker.delegate = pickerDelegate
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary

        messagesView.backgroundView = nil
        
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
        loaded = true
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
        return true
    }

    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator: coordinator)
        coordinator.animateAlongsideTransition({(context: UIViewControllerTransitionCoordinatorContext) -> Void in
                self.adjustSizes()
                self.messagesView.reloadData()
            }, completion: nil
        )
        print("Transformation to new size: \(size)");
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        tabBarController?.tabBar.hidden = true
        adjustSizes()
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardShown:", name: UIKeyboardDidShowNotification, object: nil)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardHidden:", name: UIKeyboardDidHideNotification, object: nil)

        super.viewWillAppear(animated)
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
//        adjustSizes()
        if (data!.order.isActive) {
            scrollView.scrollRectToVisible(messagesView.frame, animated: false)
        }
        else {
            scrollView.scrollRectToVisible(answerView!.frame, animated: false)
        }
        if (data!.order.text.characters.count > 15) {
            self.title = data!.order.text.substringToIndex(data!.order.topic.startIndex.advancedBy(15)) + "..."
        }
        else {
            self.title = data!.order.text
        }
    }

    override func viewWillDisappear(animated: Bool) {
        super.viewWillDisappear(animated)
        tabBarController?.tabBar.hidden = false
        AppDelegate.instance.activeProfile!.selected = nil
        NSNotificationCenter.defaultCenter().removeObserver(self)
    }

    
    func keyboardHidden(notification: NSNotification) {
        let insets = UIEdgeInsetsMake(scrollView.contentInset.top, scrollView.contentInset.left, 0, scrollView.contentInset.right)
        scrollView.contentInset = insets
        scrollView.scrollIndicatorInsets = insets
        adjustSizes()
        scrollView.scrollRectToVisible(messagesView.frame, animated: false)
    }

    func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).CGRectValue().size
        let insets = UIEdgeInsetsMake(scrollView.contentInset.top, scrollView.contentInset.left, kbSize.height, scrollView.contentInset.right)
        scrollView.contentInset = insets
        scrollView.scrollIndicatorInsets = insets
        adjustSizes()
        scrollView.scrollRectToVisible(messagesView.frame, animated: false)
    }
    
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    func adjustSizes() {
        let inputHeight = input.view.frame.height
        let frame = view.window != nil ? view.window!.frame : view.frame
        print("\(frame) \(view.window) \(navigationController)")
        scrollView.frame = CGRectMake(0, 0, frame.width, frame.height - (navigationController != nil ? navigationController!.navigationBar.frame.maxY : 64))
        let visibleSize = CGSizeMake(scrollView.frame.width - scrollView.contentInset.right - scrollView.contentInset.left, scrollView.frame.height - scrollView.contentInset.bottom - scrollView.contentInset.top)
        messagesView.frame = CGRectMake(0, 0, visibleSize.width, visibleSize.height - inputHeight - 3)
        progress.frame = CGRectMake(0, messagesView.frame.maxY, visibleSize.width, 3)
        input.view.frame = CGRectMake(0, progress.frame.maxY, visibleSize.width, inputHeight)
        answerView!.frame = CGRectMake(0, input.view.frame.maxY, visibleSize.width, visibleSize.height - inputHeight)
        scrollView.contentSize = CGSizeMake(scrollView.frame.width, messagesView.frame.height + answerView!.frame.height + input.view.frame.height)
    }
    
    func attach(input: ChatInputViewController) {
        self.presentViewController(picker, animated: true, completion: nil)
        progress.hidden = false
        progress.tintColor = UIColor.blueColor()
    }
    
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        img.setStringValue(id)
        data!.order.send(xml: img)
        progress(self.progress)
    }
    
    func report(id: String, status: Bool) {
        self.progress.tintColor = status ? UIColor.greenColor() : UIColor.redColor()
    }
}

class AnswerDelegate: NSObject, UIWebViewDelegate {
    func webView(webView: UIWebView, shouldStartLoadWithRequest request: NSURLRequest, navigationType: UIWebViewNavigationType) -> Bool {
        
        if let url = request.URL where url.scheme == "unsearch" {
            if (url.path == "/chat-messages") {
                if let indexStr = url.fragment, index = Int(indexStr) {
                    parent.messagesView.scrollToRowAtIndexPath(NSIndexPath(forItem: index, inSection: 0), atScrollPosition: .Middle, animated: false)
                    parent.scrollView.scrollRectToVisible(parent.messagesView.frame, animated: true)
                }
            }
            return false
        }
        else if let url = request.URL where url.scheme.hasPrefix("http") {
            UIApplication.sharedApplication().openURL(url)
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
        let startedFrom = lastKnownMessage
        var model = cells.last!
        while (lastKnownMessage < order.count) {
            let msg = order.message(lastKnownMessage)
            if (!msg.isRead) {
                msg.setProperty("read", value: "true")
            }
            if (model is LookingForExpertModel || model is FeedbackModel) {
                cells.removeLast();
                model = cells.last!
            }
            if (msg.type == .ExpertAssignment) {
                if (progressModel == nil || !progressModel!.accept(msg)) {
                    if (msg.properties["type"] as! String == "expert") {
                        haveActiveExpert = true
                        progressModel = ExpertInProgressModel(order: order)
                        progressCellIndex = cells.count
                        cells.append(progressModel!)
                    }
                }
                else {
                    lastKnownMessage++;
                }
            }
            else if (msg.type == .ExpertCancel) {
                if (progressCellIndex != nil) {
                    cells.removeAtIndex(progressCellIndex!)
                }
                haveActiveExpert = false
                progressModel = nil
                progressCellIndex = nil
                lastKnownMessage++;
            }
            else if (msg.type == .ExpertProgress) {
                progressModel?.accept(msg)
                lastKnownMessage++
            }
            else if (!model.accept(msg)) { // switch model
                if (msg.type == .Answer) {
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += (msg.body!);
                    answer += "\n<a href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"

                    model = AnswerReceivedModel(id: id, progress: progressModel!)
                    progressModel = nil
                    if (progressCellIndex != nil) {
                        cells.removeAtIndex(progressCellIndex!)
                        progressCellIndex = nil
                    }
                    haveActiveExpert = false
                }
                else if (msg.type == .ExpertMessage) {
                    model = ChatMessageModel(incoming: true, author: msg.from)
                }
                else {
                    model = ChatMessageModel(incoming: false, author: "me")
                }
                cells.append(model)
            }
            else {
                lastKnownMessage++
            }
        }
        if (order.count > 0 && !haveActiveExpert && order.isActive) {
            if !(cells.last! is FeedbackModel || cells.last! is LookingForExpertModel) {
                if (model is AnswerReceivedModel) {
                    cells.append(FeedbackModel(order: order))
                }
                else {
                    cells.append(LookingForExpertModel(order: order))
                }
            }
        }
        if(!order.isActive && (cells.last! is LookingForExpertModel || cells.last! is ExpertInProgressModel)) {
            cells.removeLast()
        }
        if (startedFrom != lastKnownMessage && controller != nil) {
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
