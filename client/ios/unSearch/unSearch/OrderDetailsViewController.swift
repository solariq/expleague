//
//  OrderDetailsViewController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 12.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import StoreKit
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
                case .Save:
                    let ask = NSBundle.mainBundle().loadNibNamed("SaveView", owner: self, options: [:])[0] as! SaveCell
                    ask.ok = {
                        self.data.order.markSaved()
                        self.state = .Closed
                    }
                    
                    ask.cancel = {
                        self.state = .Closed
                    }
                    detailsView!.bottomContents = ask
                    break
                }
                self.view.layoutIfNeeded()
                self.detailsView?.adjustScroll()
            }
        }
    }
    
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool {
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
        data.controller = self
        data.sync()
        detailsView?.keyboardTracker.start()
        enforceScroll = true
    }

    override func viewWillDisappear(animated: Bool) {
        super.viewWillDisappear(animated)
        AppDelegate.instance.historyView?.selected = nil
        detailsView?.keyboardTracker.stop()
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
        if (rate == 4) {
            iapRequest("com.expleague.unSearch.Star30r")
        }
        else if (rate == 5) {
            iapRequest("com.expleague.unSearch.Star150r")
        }
        else {
            parent.data.order.feedback(stars: rate!)
            self.dismissViewControllerAnimated(true, completion: nil)
        }
    }
    @IBAction func cancel(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
    }
    
    @IBOutlet var stars: [UIImageView]!
    @IBOutlet weak var text: UITextView!
    
    private func iapRequest(id: String) {
        let productRequest = SKProductsRequest(productIdentifiers: [id])
        productRequest.delegate = self
        productRequest.start()
    }
    
    var rate: Int?
    override func viewDidLoad() {
        feedback.layer.cornerRadius = Palette.CORNER_RADIUS
        feedback.clipsToBounds = true
        scoreButton.layer.cornerRadius = Palette.CORNER_RADIUS
        scoreButton.clipsToBounds = true
        cancelButton.layer.cornerRadius = Palette.CORNER_RADIUS
        cancelButton.clipsToBounds = true
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(self.handleTap(_:))))
        SKPaymentQueue.defaultQueue().addTransactionObserver(self)
        updateDescription(nil, order: parent.data.order)
    }
    
    func handleTap(recognizer: UITapGestureRecognizer) {
        let tap = recognizer.locationInView(feedback)
        var minDistance = CGFloat.infinity;
        for i in 0..<stars.count {
            let rect = stars[i].frame
            let starCenter = CGPointMake(rect.origin.x + rect.width / 2, rect.origin.y + rect.height / 2)
            let distance = self.distance(tap, starCenter)
            if (distance < 20 && distance < minDistance) {
                rate = i + 1
                minDistance = distance
            }
        }
        updateDescription(rate, order: parent.data.order);
    }
    
    func updateDescription(rate: Int?, order: ExpLeagueOrder) {
        if (rate == nil || rate! == 0) {
            let pages = parent.data.lastAnswer?.progress.pagesCount ?? 0
            let calls = parent.data.lastAnswer?.progress.callsCount ?? 0
            text.text = "Чтобы найти ответ на Ваш вопрос эксперт просмотрел \(pages) страниц\(Lang.rusNumEnding(pages, variants: ["", "ы", ""])), сделал \(calls) звон\(Lang.rusNumEnding(calls, variants: ["ок", "ка", "ков"]))."
            scoreButton.enabled = false
            return
        }
        for i in 0..<stars.count {
            stars[i].highlighted = i < rate
        }
        switch rate! {
        case 5:
            let text = NSMutableAttributedString()
            text.appendAttributedString(NSAttributedString(string: "Отличный ответ! Не ожидал такого.\nБольшое спасибо эксперту.\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFontOfSize(13)]))
            text.appendAttributedString(NSAttributedString(string: "\n150р", attributes: [NSFontAttributeName: UIFont.boldSystemFontOfSize(15)]))
            self.text.attributedText = text
        case 4:
            let text = NSMutableAttributedString()
            text.appendAttributedString(NSAttributedString(string: "Хороший ответ. Именно это и ожидалось.\nСпасибо эксперту.\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFontOfSize(13)]))
            text.appendAttributedString(NSAttributedString(string: "\n30р", attributes: [NSFontAttributeName: UIFont.boldSystemFontOfSize(15)]))
            self.text.attributedText = text
        case 3:
            let text = NSMutableAttributedString()
            text.appendAttributedString(NSAttributedString(string: "Нормальный ответ, но хотелось большего.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFontOfSize(13)]))
            text.appendAttributedString(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFontOfSize(15)]))
            self.text.attributedText = text
        case 2:
            let text = NSMutableAttributedString()
            text.appendAttributedString(NSAttributedString(string: "Старались, но не смогли мне помочь.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFontOfSize(13)]))
            text.appendAttributedString(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFontOfSize(15)]))
            self.text.attributedText = text
        case 1:
            let text = NSMutableAttributedString()
            text.appendAttributedString(NSAttributedString(string: "Только зря потратил время.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFontOfSize(13)]))
            text.appendAttributedString(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFontOfSize(15)]))
            self.text.attributedText = text
        default:
            break
        }
        text.textAlignment = .Center
        scoreButton.enabled = true
    }
    
    func distance(p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let xDist = p2.x - p1.x
        let yDist = p2.y - p1.y
        return sqrt((xDist * xDist) + (yDist * yDist));
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

extension FeedbackViewController: SKProductsRequestDelegate {
    func productsRequest(request: SKProductsRequest, didReceiveResponse response: SKProductsResponse) {
        guard response.products.count == 1 else {
            let alert = UIAlertController(title: "unSearch", message: "Не удалось запросить платеж", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            self.showViewController(alert, sender: self)
            print("No store products \(request) found")
            return
        }
        let payment = SKPayment(product: response.products[0])
        SKPaymentQueue.defaultQueue().addPayment(payment)
    }
}

extension FeedbackViewController: SKPaymentTransactionObserver {
    func paymentQueue(queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction:AnyObject in transactions {
            if let trans:SKPaymentTransaction = transaction as? SKPaymentTransaction{
                switch trans.transactionState {
                case .Purchased:
                    parent.data.order.feedback(stars: rate!)
                    self.dismissViewControllerAnimated(true, completion: nil)
                    SKPaymentQueue.defaultQueue().finishTransaction(transaction as! SKPaymentTransaction)
                    break;
                case .Failed:
                    print("Purchased Failed");
                    SKPaymentQueue.defaultQueue().finishTransaction(transaction as! SKPaymentTransaction)
                    break;
                default:
                    break;
                }
            }
        }
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
