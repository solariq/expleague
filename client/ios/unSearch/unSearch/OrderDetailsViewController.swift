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
fileprivate func < <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l < r
  case (nil, _?):
    return true
  default:
    return false
  }
}


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
    let orderAttachmentsModel = OrderAttachmentsModel()

    let data: ChatModel

    override func loadView() {
        view = OrderDetailsView(frame: UIScreen.main.applicationFrame)
    }
    
    var answerText: String = "" {
        willSet (newValue){
            if (answerText != newValue) {
                let path = Bundle.main.bundlePath
                let baseURL = URL(fileURLWithPath: path);
                answer.loadHTMLString("<html><head><script src=\"md-scripts.js\"></script>\n"
                    + "<link rel=\"stylesheet\" href=\"markdownpad-github.css\"></head>"
                    + "<body>\(newValue)</body></html>", baseURL: baseURL)
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        PurchaseHelper.instance.register(["com.expleague.unSearch.Star30r", "com.expleague.unSearch.Star150r"])
        detailsView!.navigationItem = navigationItem
        detailsView!.controller = self
        edgesForExtendedLayout = .bottom
        automaticallyAdjustsScrollViewInsets = false
        addChildViewController(input)
        input.didMove(toParentViewController: self)
        answerDelegate = AnswerDelegate(parent: self)
        answer.delegate = answerDelegate
        input.delegate = self;
    }
        
    var state: ChatState? {
        willSet (newState) {
            if let state = newState, state != self.state {
                switch(state) {
                case .chat:
                    detailsView!.bottomContents = input.view
                case .ask:
                    let ask = Bundle.main.loadNibNamed("ContinueView", owner: self, options: [:])?[0] as! ContinueCell
                    ask.ok = {
                        let feedback = FeedbackViewController(owner: self)
                        feedback.modalPresentationStyle = .overCurrentContext
                        self.providesPresentationContextTransitionStyle = true;
                        self.definesPresentationContext = true;

                        self.present(feedback, animated: true, completion: nil)
                    }
                    
                    ask.cancel = {
                        self.state = .chat
                        self.data.order.continueTask()
                    }
                    detailsView!.bottomContents = ask
                case .closed:
                    detailsView?.bottomContents = nil
                case .save:
                    let ask = Bundle.main.loadNibNamed("SaveView", owner: self, options: [:])?[0] as! SaveCell
                    ask.ok = {
                        self.data.order.markSaved()
                        self.state = .closed
                    }
                    
                    ask.cancel = {
                        self.state = .closed
                    }
                    detailsView!.bottomContents = ask
                }
                DispatchQueue.main.async {
                    self.enforceScroll = true
                    self.view.layoutIfNeeded()
                    self.detailsView?.adjustScroll()
                }
            }
        }
    }
    
    func chatInput(_ chatInput: ChatInputViewController, didSend text: String) -> Bool {
        data.order.send(text: text)
        return true
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animate(alongsideTransition: { (context: UIViewControllerTransitionCoordinatorContext) -> Void in
            self.messages.reloadData()
            self.detailsView!.adjustScroll()
        }, completion: nil)
        super.viewWillTransition(to: size, with: coordinator)
    }
    
    fileprivate var enforceScroll = false
    fileprivate var shown = false
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        data.controller = self
        data.sync(true)
        detailsView?.keyboardTracker.start()
        enforceScroll = true
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        AppDelegate.instance.historyView?.selected = nil
        detailsView?.keyboardTracker.stop()
        data.markAsRead()
        data.controller = nil
        shown = false
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        if (enforceScroll) {
            scrollToLastMessage()
            if (data.state == .chat || data.state == .save) {
                detailsView!.scrollToChat(false)
            }
            else {
                detailsView!.scrollToAnswer(false)
            }
            enforceScroll = false
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        tabBarController?.tabBar.isHidden = true
        if (data.order.unreadCount == 0 && state == .ask) {
            DispatchQueue.main.async {
                let alert = UIAlertController(title: "unSearch", message: "Не забудьте оценить ответ эксперта!", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                self.present(alert, animated: true, completion: nil)
            }
        }
        data.markAsRead()
        shown = true
    }
    
    
     func attach(_ input: ChatInputViewController) {
        let addAttachmentAlert = AddAttachmentAlertController(filter: nil) { imageId in
            let alert = UIAlertController(title: "unSearch", message: "Отправить выбранную фотографию эксперту?", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Да", style: .default, handler: {action in
                let attachment = OrderAttachment(imageId: imageId)
                AppDelegate.instance.uploader.upload(attachment)
                QObject.track(attachment, #selector(OrderAttachment.progressChanged)) {
                    if(attachment.progress < 0) { // error
                        input.progress.tintColor = Palette.ERROR
                        input.progress.progress = 1.0
                        let error = attachment.error != nil ? " : \(attachment.error!)" : "."
                        let warning = UIAlertController(title: "unSearch", message: "Не удалось отослать изображение\(error)", preferredStyle: .alert)
                        warning.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                        self.present(warning, animated: true, completion: nil)
                        return false
                    }
                    else if (attachment.progress < 1) {
                        input.progress.tintColor = Palette.CONTROL
                        input.progress.progress = attachment.progress!
                        return true
                    }
                    else {
                        input.progress.tintColor = Palette.OK
                        input.progress.progress = attachment.progress!
                        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
                        img.stringValue = attachment.url.absoluteString
                        self.data.order.send(xml: img, type: "groupchat")
                        
                        return false
                    }
                }
            }))
            alert.addAction(UIAlertAction(title: "Нет", style: .cancel, handler: nil))
            self.present(alert, animated: true, completion: nil)
        }
        addAttachmentAlert.modalPresentationStyle = .overCurrentContext
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
        
        present(addAttachmentAlert, animated: true, completion: nil)
        input.progress.tintColor = UIColor.blue
    }
    
    func append(_ id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        progress(input.progress)
    }
    
    func report(_ id: String, status: Bool) {
        input.progress.tintColor = status ? UIColor.green : UIColor.red
    }

    func scrollToLastMessage() {
        if let index = data.lastIndex {
            messages.scrollToRow(at: index as IndexPath, at: .top, animated: true)
        }
        if (shown) {
            data.markAsRead()
        }
    }
    
    func close() {
        _ = navigationController?.popViewController(animated: false)
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
    
    fileprivate var busy = false
    @IBAction func fire(_ sender: AnyObject) {
        guard !busy else {
            return
        }
        let rate = self.rate
        if (rate == 4 || rate == 5) {
            busy = true
            let purchaseId = rate == 4 ? "com.expleague.unSearch.Star30r" : "com.expleague.unSearch.Star150r"
            PurchaseHelper.instance.request(purchaseId) {rc, payment in
                switch(rc) {
                case .accepted:
                    self.owner.data.order.feedback(stars: rate!, payment: payment)
                    self.dismiss(animated: true, completion: nil)
                case .error:
                    let alert = UIAlertController(title: "unSearch", message: "Не удалось провести платеж!", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                    self.present(alert, animated: true, completion: nil)
                case .rejected:
                    let alert = UIAlertController(title: "unSearch", message: "Платеж отклонен!", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                    self.present(alert, animated: true, completion: nil)
                    break
                }
                self.busy = false
            }
        }
        else {
            owner.data.order.feedback(stars: rate!, payment: nil)
            self.dismiss(animated: true, completion: nil)
        }
    }
    @IBAction func cancel(_ sender: AnyObject) {
        self.dismiss(animated: true, completion: nil)
    }
    
    @IBOutlet var stars: [UIImageView]!
    @IBOutlet weak var text: UITextView!
    
    var rate: Int?
    override func viewDidLoad() {
        feedback.layer.cornerRadius = Palette.CORNER_RADIUS
        feedback.clipsToBounds = true
        scoreButton.layer.cornerRadius = Palette.CORNER_RADIUS
        scoreButton.clipsToBounds = true
        cancelButton.layer.cornerRadius = Palette.CORNER_RADIUS
        cancelButton.clipsToBounds = true
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(self.handleTap(_:))))
        updateDescription(nil, order: owner.data.order)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        owner.detailsView?.keyboardTracker.stop()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        owner.detailsView?.keyboardTracker.start()
    }
    
    func handleTap(_ recognizer: UITapGestureRecognizer) {
        let tap = recognizer.location(in: feedback)
        var minDistance = CGFloat.infinity;
        for i in 0..<stars.count {
            let rect = stars[i].frame
            let starCenter = CGPoint(x: rect.origin.x + rect.width / 2, y: rect.origin.y + rect.height / 2)
            let distance = self.distance(tap, starCenter)
            if (distance < 20 && distance < minDistance) {
                rate = i + 1
                minDistance = distance
            }
        }
        updateDescription(rate, order: owner.data.order);
    }
    
    func updateDescription(_ rate: Int?, order: ExpLeagueOrder) {
        if (rate == nil || rate! == 0) {
            let pages = owner.data.lastAnswer?.progress.pagesCount ?? 0
            let calls = owner.data.lastAnswer?.progress.callsCount ?? 0
            text.text = "Чтобы найти ответ на ваш вопрос, эксперт просмотрел \(pages) страниц\(Lang.rusNumEnding(pages, variants: ["", "ы", ""])), сделал \(calls) звон\(Lang.rusNumEnding(calls, variants: ["ок", "ка", "ков"]))."
            scoreButton.isEnabled = false
            return
        }
        for i in 0..<stars.count {
            stars[i].isHighlighted = i < rate
        }
        switch rate! {
        case 5:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Отличный ответ! Не ожидал такого.\nБольшое спасибо эксперту.\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 13)]))
            text.append(NSAttributedString(string: "\n150р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.text.attributedText = text
        case 4:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Хороший ответ. Именно это и ожидалось.\nСпасибо эксперту.\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 13)]))
            text.append(NSAttributedString(string: "\n30р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.text.attributedText = text
        case 3:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Нормальный ответ, но хотелось большего.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 13)]))
            text.append(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.text.attributedText = text
        case 2:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Старались, но не смогли мне помочь.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 13)]))
            text.append(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.text.attributedText = text
        case 1:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Только зря потратил время.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 13)]))
            text.append(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.text.attributedText = text
        default:
            break
        }
        text.textAlignment = .center
        scoreButton.isEnabled = true
    }
    
    func distance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let xDist = p2.x - p1.x
        let yDist = p2.y - p1.y
        return sqrt((xDist * xDist) + (yDist * yDist));
    }

    let owner: OrderDetailsViewController
    init(owner: OrderDetailsViewController) {
        self.owner = owner
        super.init(nibName: "FeedbackView", bundle: nil)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class AnswerDelegate: NSObject, UIWebViewDelegate {
    func webView(_ webView: UIWebView, shouldStartLoadWith request: URLRequest, navigationType: UIWebViewNavigationType) -> Bool {
        
        if let url = request.url , url.scheme == "unsearch" {
            if (url.path == "/chat-messages") {
                if let indexStr = url.fragment, let index = Int(indexStr) {
                    parent.messages.scrollToRow(at: parent.data.translateToIndex(index)!, at: .middle, animated: false)
                    parent.detailsView!.scrollToChat(true)
                }
            }
            return false
        }
        else if let url = request.url , (url.scheme?.hasPrefix("http"))! {
            UIApplication.shared.openURL(url)
            return false
        }

        return true
    }
    
    let parent: OrderDetailsViewController
    init(parent: OrderDetailsViewController) {
        self.parent = parent
    }
}
