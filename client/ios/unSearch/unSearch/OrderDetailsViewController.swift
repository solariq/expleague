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

class OrderDetailsViewController: UIViewController, ChatInputDelegate, ImageSenderQueue {
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
        detailsView!.navigationItem = navigationItem
        detailsView!.controller = self
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
                case .Ask:
                    let ask = NSBundle.mainBundle().loadNibNamed("ContinueView", owner: self, options: [:])[0] as! ContinueCell
                    ask.ok = {
                        let feedback = FeedbackViewController(parent: self)
                        feedback.modalPresentationStyle = .OverCurrentContext
                        self.providesPresentationContextTransitionStyle = true;
                        self.definesPresentationContext = true;

                        self.presentViewController(feedback, animated: true, completion: nil)
                    }
                    
                    ask.cancel = {
                        self.state = .Chat
                        self.data.order.continueTask()
                    }
                    detailsView!.bottomContents = ask
                    break
                case .Closed:
                    detailsView?.bottomContents = nil
                    break
                }
                self.view.layoutIfNeeded()
                self.detailsView?.adjustScroll()
            }
        }
    }
    
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool {
        if (!AppDelegate.instance.ensureConnected({self.chatInput(chatInput, didSend: text)})) {
            return false
        }
        AppDelegate.instance.connect()
        data.order.send(text: text)
        return true
    }

    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animateAlongsideTransition({ (context: UIViewControllerTransitionCoordinatorContext) -> Void in
            self.messages.reloadData()
            self.detailsView!.adjustScroll()
        }, completion: nil)
        super.viewWillTransitionToSize(size, withTransitionCoordinator: coordinator)
    }
    
    private var enforceScroll = false
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        detailsView!.keyboardTracker.start()
        data.controller = self
        data.sync()
        enforceScroll = true
    }

    override func viewWillDisappear(animated: Bool) {
        super.viewWillDisappear(animated)
        AppDelegate.instance.historyView?.selected = nil
        data.markAsRead()
        
        data.controller = nil
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        if (enforceScroll) {
            scrollToLastMessage()
            if (state == .Chat || (state == .Closed && data.order.fake)) {
                detailsView!.scrollToChat(false)
            }
            else {
                detailsView!.scrollToAnswer(false)
            }
            enforceScroll = false
        }
    }

    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        
        tabBarController?.tabBar.hidden = true
        data.markAsRead()
    }

    func attach(input: ChatInputViewController) {
        self.presentViewController(picker, animated: true, completion: nil)
        input.progress.tintColor = UIColor.blueColor()
    }
    
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        progress(input.progress)
    }
    
    func report(id: String, status: Bool) {
        input.progress.tintColor = status ? UIColor.greenColor() : UIColor.redColor()
        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
        img.setStringValue(AppDelegate.instance.activeProfile!.imageUrl(id).absoluteString)
        data.order.send(xml: img, type: "groupchat")
    }

    func scrollToLastMessage() {
        if let index = data.lastIndex {
            messages.scrollToRowAtIndexPath(index, atScrollPosition: .Top, animated: true)
        }
    }
    
    func close() {
        navigationController?.popViewControllerAnimated(false)
    }


    init(data: ChatModel) {
        self.data = data
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class FeedbackViewController: UIViewController {
    @IBOutlet weak var feedback: FeedbackCell!
    @IBOutlet weak var cancelButton: UIButton!
    @IBOutlet weak var scoreButton: UIButton!
    @IBAction func fire(sender: AnyObject) {
        parent.data.order.feedback(stars: feedback.rate)
        if (feedback.rate == 4) {
            UIApplication.sharedApplication().openURL(NSURL(string: "https://www.paypal.me/expleague/30")!)
        }
        else if (feedback.rate == 5) {
            UIApplication.sharedApplication().openURL(NSURL(string: "https://www.paypal.me/expleague/150")!)
        }
        self.dismissViewControllerAnimated(true, completion: nil)
    }
    @IBAction func cancel(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
    }
    override func viewDidLoad() {
        feedback.layer.cornerRadius = Palette.CORNER_RADIUS
        feedback.clipsToBounds = true
        scoreButton.layer.cornerRadius = Palette.CORNER_RADIUS
        scoreButton.clipsToBounds = true
        cancelButton.layer.cornerRadius = Palette.CORNER_RADIUS
        cancelButton.clipsToBounds = true
    }
    
    let parent: OrderDetailsViewController
    init(parent: OrderDetailsViewController) {
        self.parent = parent
        super.init(nibName: "FeedbackView", bundle: nil)
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
                    parent.messages.scrollToRowAtIndexPath(parent.data.translateToIndex(index)!, atScrollPosition: .Middle, animated: false)
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
    
    let parent: OrderDetailsViewController
    init(parent: OrderDetailsViewController) {
        self.parent = parent
    }
}
