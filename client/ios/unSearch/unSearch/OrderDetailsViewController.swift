//
//  OrderDetailsViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework

class OrderDetailsVeiwController: UIViewController, ChatInputDelegate, ImageSenderQueue {
    var detailsView: OrderDetailsView? {
        return view as? OrderDetailsView
    }

    var messages: UITableView {
        return detailsView!.messagesView
    }
    
    var answer: UIWebView {
        return detailsView!.answerView
    }

    let input = ChatInputViewController(nibName: "ChatInput", bundle: nil)
    var answerDelegate: AnswerDelegate?
    let picker = UIImagePickerController()
    var pickerDelegate: ImagePickerDelegate?

    let data: ChatModel

    override func loadView() {
        view = OrderDetailsView(frame: UIScreen.mainScreen().applicationFrame)
    }
    
    var answerText: String = "" {
        willSet (newValue){
            if (answerText != newValue) {
                let path = NSBundle.mainBundle().bundlePath
                let baseURL = NSURL.fileURLWithPath(path);
                answer.loadHTMLString("<html><head><script src=\"md-scripts.js\"></script>\n"
                    + "<link rel=\"stylesheet\" href=\"markdownpad-github.css\"></head>"
                    + "<body>\(newValue)</body></html>", baseURL: baseURL)
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        
        edgesForExtendedLayout = .Bottom
        automaticallyAdjustsScrollViewInsets = false
        addChildViewController(input)
        input.didMoveToParentViewController(self)
        answerDelegate = AnswerDelegate(parent: self)
        answer.delegate = answerDelegate
        input.delegate = self;
        pickerDelegate = ImagePickerDelegate(queue: self, picker: picker)
        picker.delegate = pickerDelegate
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary
    }
        
    var state: ChatState? {
        willSet (newState) {
            if let state = newState where state != self.state {
                switch(state) {
                case .Chat:
                    detailsView!.bottomContents = input.view
                    break
                case .Feedback:
                    let feedback = NSBundle.mainBundle().loadNibNamed("FeedbackView", owner: self, options: [:])[0] as! FeedbackCell
                    feedback.ok = {
                        self.state = .Chat
                    }
                    
                    feedback.cancel = {
                        self.state = .Chat
                    }
                    detailsView!.bottomContents = feedback
                    break
                case .Ask:
                    detailsView!.scrollToAnswer(true)
                    break
                default:
                    break
                }
            }
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
        data.order.send(text: text)
        return true
    }

    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator: coordinator)
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        NSNotificationCenter.defaultCenter().addObserver(view, selector: "keyboardShown:", name: UIKeyboardWillShowNotification, object: nil)
        NSNotificationCenter.defaultCenter().addObserver(view, selector: "keyboardHidden:", name: UIKeyboardWillHideNotification, object: nil)
        data.controller = self
        if (data.order.text.characters.count > 15) {
            self.title = data.order.text.substringToIndex(data.order.topic.startIndex.advancedBy(15)) + "..."
        }
        else {
            self.title = data.order.text
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        scrollToLastMessage()

        if (state == .Chat) {
            detailsView!.scrollToChat(false)
        }
        else {
            detailsView!.scrollToAnswer(false)
        }
    }

    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        tabBarController?.tabBar.hidden = true
        data.markAsRead()
    }

    override func viewWillDisappear(animated: Bool) {
        super.viewWillDisappear(animated)
        AppDelegate.instance.activeProfile!.selected = nil
        NSNotificationCenter.defaultCenter().removeObserver(view)
        data.markAsRead()
        data.controller = nil
    }

    func attach(input: ChatInputViewController) {
        self.presentViewController(picker, animated: true, completion: nil)
        detailsView!.progress.tintColor = UIColor.blueColor()
    }
    
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        progress(detailsView!.progress)
    }
    
    func report(id: String, status: Bool) {
        detailsView?.progress.tintColor = status ? UIColor.greenColor() : UIColor.redColor()
        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        img.setStringValue(AppDelegate.instance.activeProfile!.imageUrl(id).absoluteString)
        data.order.send(xml: img)
    }

    func scrollToLastMessage() {
        if (data.cells.count > 0) {
            messages.scrollToRowAtIndexPath(NSIndexPath(forItem: data.cells.count - 1, inSection: 0), atScrollPosition: .Bottom, animated: true)
        }
    }


    init(data: ChatModel) {
        self.data = data
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class AnswerDelegate: NSObject, UIWebViewDelegate {
    func webView(webView: UIWebView, shouldStartLoadWithRequest request: NSURLRequest, navigationType: UIWebViewNavigationType) -> Bool {
        
        if let url = request.URL where url.scheme == "unsearch" {
            if (url.path == "/chat-messages") {
                if let indexStr = url.fragment, index = Int(indexStr) {
                    parent.messages.scrollToRowAtIndexPath(NSIndexPath(forItem: index - 1, inSection: 0), atScrollPosition: .Middle, animated: false)
                    parent.detailsView!.scrollToChat(true)
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
    
    let parent: OrderDetailsVeiwController
    init(parent: OrderDetailsVeiwController) {
        self.parent = parent
    }
}