//
//  MessagesViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class MessagesVeiwController: UIViewController, ChatInputDelegate, ImageSenderQueue {
    static let INITIAL_WEBVIEW_CONTENT = "<html><body></body></html>"
    var order: ExpLeagueOrder? {
        didSet {
            if (order == nil) {
                return
            }
            answerText = MessagesVeiwController.INITIAL_WEBVIEW_CONTENT
            data = ChatMessagesModel(order: order, parent: self)
            messagesView.dataSource = data
            messagesView.delegate = data
            if (loaded) {
                messagesView.reloadData()

                if (order!.status == .Closed) {
                    scrollView.scrollRectToVisible(answerView!.frame, animated: false)
                }
                else {
                    scrollView.scrollRectToVisible(messagesView.frame, animated: false)
                    messagesView.scrollToRowAtIndexPath(NSIndexPath(forRow: data!.cells.count - 1, inSection: 0), atScrollPosition: .Bottom, animated: false)
                }

            }
            
            if (order!.text.characters.count > 15) {
                self.title = order!.text.substringToIndex(order!.topic.startIndex.advancedBy(15)) + "..."
            }
            else {
                self.title = order!.text
            }
            self.title = order!.text
        }
    }
    
    var loaded = false
    let placeHolder = UIView()
    let scrollView = UIScrollView()
    let input = ChatInputViewController(nibName: "ChatInput", bundle: nil)
    let messagesView = UITableView()
    var answerView : UIWebView? = nil
    let picker = UIImagePickerController()
    let progress = UIProgressView()
    var data: ChatMessagesModel?
    var answerUpdateTimer: NSTimer?
    
    private var answerText: String = INITIAL_WEBVIEW_CONTENT
    
    func answerAppend(text: String) {
        answerText = answerText.substringToIndex(answerText.endIndex.advancedBy(-"</body></html>".characters.count))
        answerText += text + "</body></html>";
//        print("answerText changed: \(answerText)")
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
        scrollView.addSubview(answerView!)
        scrollView.pagingEnabled = true
        scrollView.clipsToBounds = false
        input.text.layer.borderWidth = 2
        input.text.layer.borderColor = UIColor.lightGrayColor().CGColor
        input.text.layer.cornerRadius = 4
        input.delegate = self;
        
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
        if (data?.order != nil) {
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
                    //                progressController.alert = alertView
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
            data?.order?.send(text)
        }
        else {
            let message = ChatMessageModel(incoming: true, author: "me")
            message.append(text: text, time: NSDate().timeIntervalSince1970)
            data?.cells.append(message)
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
        super.viewWillDisappear(animated)
        tabBar.hidden = false
        answerUpdateTimer?.invalidate()
        answerUpdateTimer = nil
        AppDelegate.instance.messagesView = nil
        AppDelegate.instance.activeProfile!.selected = nil
        NSNotificationCenter.defaultCenter().removeObserver(self);
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
        tabBar.hidden = true;
        if (order != nil) {
            data?.sync(order!)
            messagesView.reloadData()
        }
        AppDelegate.instance.messagesView = self
        answerUpdateTimer = NSTimer.scheduledTimerWithTimeInterval(0.1, target: self, selector: "updateAnswerText", userInfo: nil, repeats: true)
    }
    
    var prevAnswerText: String?
    func updateAnswerText() {
        if (prevAnswerText == nil || prevAnswerText! != answerText) {
            answerView?.loadHTMLString(answerText, baseURL: nil)
            prevAnswerText = answerText
        }
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        NSNotificationCenter.defaultCenter().addObserver(self, selector: "keyboardShown:", name: UIKeyboardDidShowNotification, object: nil)
        adjustSizes()
    }

    func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).CGRectValue().size
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
        messagesView.frame = CGRectMake(0, 0, visibleSize.width, visibleSize.height - inputHeight - 3)
        progress.frame = CGRectMake(0, messagesView.frame.maxY, visibleSize.width, 3)
        input.view.frame = CGRectMake(0, progress.frame.maxY, visibleSize.width, inputHeight)
        answerView!.frame = CGRectMake(0, input.view.frame.maxY, visibleSize.width, visibleSize.height - inputHeight)
        scrollView.contentSize = CGSizeMake(scrollView.frame.width, messagesView.frame.height + answerView!.frame.height + input.view.frame.height)
    }
    
    func attach(input: ChatInputViewController) {
        self.presentViewController(picker, animated: true, completion: nil)
    }
    
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        order?.send("{\"content\": [{\"image\": {\"image\": \"\(id)\"}}]}")
        progress(self.progress)
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
    let order: ExpLeagueOrder?
    let parent: MessagesVeiwController
    init(order: ExpLeagueOrder?, parent: MessagesVeiwController) {
        self.order = order
        self.parent = parent
        super.init()
        if (order != nil) {
            sync(order!)
        }
    }
    var progressModel: ExpertInProgressModel? = nil
    var progressCellIndex: Int?
    func sync(order: ExpLeagueOrder) {
        if (cells.isEmpty) {
            cells.append(SetupModel(order: order))
        }
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
                        progressModel = ExpertInProgressModel(mvc: parent)
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
                    model = AnswerReceivedModel(controller: parent, progress: progressModel!)
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
                    cells.append(FeedbackModel(controller: self.parent))
                }
                else {
                    cells.append(LookingForExpertModel(mvc: parent))
                }
            }
        }
        if(!order.isActive && (cells.last! is LookingForExpertModel || cells.last! is ExpertInProgressModel)) {
            cells.removeLast()
        }
    }

    var cells: [ChatCellModel] = [];
    
    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section > 0 ? 0 : cells.count;
    }
    
    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let message = cells[indexPath.item]
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.type), forIndexPath: indexPath) as! ChatCell
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
            tableView.scrollToRowAtIndexPath(NSIndexPath(forItem: cells.count - 1, inSection: 0), atScrollPosition: .Top, animated: true)
        }
    }
}
