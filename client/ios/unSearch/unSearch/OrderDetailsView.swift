//
// Created by Igor E. Kuralenok on 09/03/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import FBSDKCoreKit
import unSearchCore

class OrderDetailsView: UIView {
    let scrollView = UIScrollView()
    let bottomView = UIView()
    let messagesView = UITableView()
    let answerView = UIWebView()
    
    var keyboardTracker: KeyboardStateTracker!
    var separator: SeparatorView!
    var messagesViewHConstraint: NSLayoutConstraint!
    var answerViewHConstraint: NSLayoutConstraint!
    var bottomViewBottom: NSLayoutConstraint!
    var navigationItem: UINavigationItem!
    var controller: OrderDetailsViewController!

    var bottomContents: UIView? {
        didSet {
            for v in bottomView.subviews {
                v.removeFromSuperview()
            }
            bottomView.removeConstraints(bottomView.constraints)
            if bottomContents != nil {
                bottomContents!.translatesAutoresizingMaskIntoConstraints = false
                bottomView.addSubview(bottomContents!)
                var constraints: [NSLayoutConstraint] = [];

                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .bottom, relatedBy: .equal, toItem: bottomView, attribute: .bottom, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .trailing, relatedBy: .equal, toItem: bottomView, attribute: .trailing, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .leading, relatedBy: .equal, toItem: bottomView, attribute: .leading, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .top, relatedBy: .equal, toItem: bottomView, attribute: .top, multiplier: 1, constant: 0))
                NSLayoutConstraint.activate(constraints)
            }
            else {
                bottomView.addConstraint(NSLayoutConstraint(item: bottomView, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: 0))
            }
            layoutIfNeeded()
        }
    }
    
    fileprivate var inAnswer = false
    func scrollToAnswer(_ animated: Bool) {
        guard !controller.answerText.isEmpty else {
            scrollToChat(animated)
            return
        }
        scrollView.setContentOffset(separator.frame.origin, animated: animated)
        separator.backgroundColor = UIColor.white
        separator.tagImage.image = UIImage(named: "chat_header_tag")!
        self.navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .action, target: self, action: #selector(shareAnswer))
        inAnswer = true
    }
    
    func scrollToChat(_ animated: Bool) {
        scrollView.setContentOffset(messagesView.frame.origin, animated: animated)
        separator.backgroundColor = Palette.CHAT_BACKGROUND
        separator.tagImage.image = UIImage(named: "chat_footer_tag")!
        inAnswer = false
        self.navigationItem.rightBarButtonItem = nil
    }
    
    let renderer = BNHtmlPdfKit(pageSize: BNPageSizeA4)!
    func shareAnswer() {
        let path = Bundle.main.bundlePath
        let baseURL = URL(fileURLWithPath: path);
        let html = "<html><head>\n"
            + "<link rel=\"stylesheet\" href=\"markdownpad-github-pdf.css\"></head>"
            + "<body>\(controller.answerText)</body></html>"
        let temp = URL(fileURLWithPath: NSTemporaryDirectory())
        let pdfUrl = URL(string: "unSearch-answer-" + controller.data.order.id + ".pdf", relativeTo: temp)
        renderer.delegate = self
        renderer.baseUrl = baseURL
        renderer.saveHtml(asPdf: html, toFile: pdfUrl!.path)
        FBSDKAppEvents.logEvent("Share answer", parameters: ["user": ExpLeagueProfile.active.jid.user, "room": controller.data.order.id])
    }
    
    func adjustScroll() {
        if (inAnswer) {
            scrollToAnswer(false)
        }
        else {
            scrollToChat(false)
        }
    }
    

    func dismissKeyboard() {
        endEditing(true)
    }

    override init(frame: CGRect) {
        super.init(frame: frame)

        separator = Bundle.main.loadNibNamed("Separator", owner: self, options: [:])?[0] as! SeparatorView
        addSubview(scrollView)
        addSubview(bottomView)
        scrollView.addSubview(messagesView)
        scrollView.addSubview(separator)
        scrollView.addSubview(answerView)
        scrollView.isScrollEnabled = false
        scrollView.clipsToBounds = false
        messagesView.register(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(describing: CellType.incoming))
        messagesView.register(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(describing: CellType.outgoing))
        messagesView.register(UINib(nibName: "LookingForExpert", bundle: nil), forCellReuseIdentifier: String(describing: CellType.lookingForExpert))
        messagesView.register(UINib(nibName: "AnswerReceived", bundle: nil), forCellReuseIdentifier: String(describing: CellType.answerReceived))
        messagesView.register(UINib(nibName: "TaskInProgress", bundle: nil), forCellReuseIdentifier: String(describing: CellType.taskInProgress))
        messagesView.register(UINib(nibName: "Setup", bundle: nil), forCellReuseIdentifier: String(describing: CellType.setup))
        messagesView.separatorStyle = .none
        messagesView.backgroundColor = Palette.CHAT_BACKGROUND
        messagesView.bounces = false
        scrollView.backgroundColor = messagesView.backgroundColor
        separator.translatesAutoresizingMaskIntoConstraints = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        answerView.translatesAutoresizingMaskIntoConstraints = false
        answerView.dataDetectorTypes = [.all]
        messagesView.translatesAutoresizingMaskIntoConstraints = false
        bottomView.translatesAutoresizingMaskIntoConstraints = false
        let pullGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pullGesture.delegate = self
        scrollView.addGestureRecognizer(pullGesture)
        answerView.scrollView.panGestureRecognizer.require(toFail: pullGesture)
        messagesView.panGestureRecognizer.require(toFail: pullGesture)
        answerView.scrollView.bounces = false
        messagesViewHConstraint = NSLayoutConstraint(item: messagesView, attribute: .height, relatedBy: .equal, toItem: scrollView, attribute: .height, multiplier: 1, constant: -15)
        answerViewHConstraint = NSLayoutConstraint(item: answerView, attribute: .height, relatedBy: .equal, toItem: scrollView, attribute: .height, multiplier: 1, constant: -15)
        bottomViewBottom = NSLayoutConstraint(item: bottomView, attribute: .bottom, relatedBy: .equal, toItem: self, attribute: .bottomMargin, multiplier: 1, constant: 0)
        keyboardTracker = KeyboardStateTracker(check: {self.controller.input.text.isFirstResponder}) { (height: CGFloat) -> () in
            self.bottomViewBottom.constant = -height;
            self.layoutIfNeeded()
            self.adjustScroll()
        }
        var constraints: [NSLayoutConstraint] = [];
                // main view constraints
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .top, relatedBy: .equal, toItem: self, attribute: .topMargin, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .width, relatedBy: .equal, toItem: self, attribute: .width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .bottom, relatedBy: .equal, toItem: bottomView, attribute: .top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: bottomView, attribute: .leading, relatedBy: .equal, toItem: self, attribute: .leading, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: bottomView, attribute: .trailing, relatedBy: .equal, toItem: self, attribute: .trailing, multiplier: 1, constant: 0))
        constraints.append(bottomViewBottom)

                // scroll view constraints
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .top, relatedBy: .equal, toItem: scrollView, attribute: .top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .width, relatedBy: .equal, toItem: self, attribute: .width, multiplier: 1, constant: 0))
        constraints.append(messagesViewHConstraint)
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .bottom, relatedBy: .equal, toItem: separator, attribute: .top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .width, relatedBy: .equal, toItem: self, attribute: .width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant:15))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .bottom, relatedBy: .equal, toItem: answerView, attribute: .top, multiplier: 1, constant: 0))

        constraints.append(answerViewHConstraint)
        constraints.append(NSLayoutConstraint(item: answerView, attribute: .width, relatedBy: .equal, toItem: self, attribute: .width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: answerView, attribute: .bottom, relatedBy: .equal, toItem: scrollView, attribute: .bottom, multiplier: 1, constant: 0))
        NSLayoutConstraint.activate(constraints)
        messagesView.backgroundView = nil

        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard)))
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    var gestureBegin: CGFloat = 0
    var gestureBeginOffset: CGFloat = 0
}

extension OrderDetailsView: BNHtmlPdfKitDelegate {
    func htmlPdfKit(_ htmlPdfKit: BNHtmlPdfKit!, didSavePdfFile file: String!) {
        let objectsToShare = [URL(fileURLWithPath: file)]
        let activityVC = UIActivityViewController(activityItems: objectsToShare, applicationActivities: nil)
        controller.present(activityVC, animated: true, completion: nil)
    }
}

extension OrderDetailsView: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        let y = touch.location(in: scrollView).y
        return (y > separator.frame.maxY ? y - separator.frame.maxY : separator.frame.minY - y) < 80
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }

    @IBAction
    func handlePan(_ gestureRecognizer: UIPanGestureRecognizer) {
        switch (gestureRecognizer.state) {
        case .began:
            gestureBeginOffset = scrollView.contentOffset.y
            gestureBegin = gestureRecognizer.location(in: nil).y
            break
        case .changed:
            let translation = gestureRecognizer.translation(in: scrollView)
            let y = gestureRecognizer.location(in: nil).y
            scrollView.setContentOffset(CGPoint(x: 0, y: gestureBeginOffset + gestureBegin - y), animated: false)
            gestureRecognizer.setTranslation(translation, in: scrollView)
            break
        case .ended:
            let diffY = gestureRecognizer.location(in: nil).y - gestureBegin
            if (abs(diffY) > messagesView.frame.height / 4) {
                if (diffY < 0) {
                    scrollToAnswer(true)
                }
                else {
                    scrollToChat(true)
                }
            }
            else {
                scrollView.setContentOffset(CGPoint(x: 0, y: gestureBeginOffset), animated: true)
            }
            break
        default:
            break
        }
    }
}
