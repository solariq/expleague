//
// Created by Igor E. Kuralenok on 09/03/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class OrderDetailsView: UIView {
    let scrollView = UIScrollView()
    let bottomView = UIView()
    let messagesView = UITableView()
    let answerView = UIWebView()

    var separator: SeparatorView!
    var messagesViewHConstraint: NSLayoutConstraint!
    var answerViewHConstraint: NSLayoutConstraint!
    var bottomViewBottom: NSLayoutConstraint!

    func keyboardHidden(notification: NSNotification) {
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        self.bottomViewBottom.constant = 0
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.layoutIfNeeded()
        }, completion: nil)
    }

    func keyboardShown(notification: NSNotification) {
        let kbSize = (notification.userInfo![UIKeyboardFrameEndUserInfoKey] as! NSValue).CGRectValue().size
        let duration = notification.userInfo![UIKeyboardAnimationDurationUserInfoKey] as! NSTimeInterval;
        let curve = notification.userInfo![UIKeyboardAnimationCurveUserInfoKey] as! UInt
        let options: UIViewAnimationOptions = [.BeginFromCurrentState, UIViewAnimationOptions(rawValue: (UIViewAnimationOptions.CurveEaseIn.rawValue << curve))]
        self.bottomViewBottom.constant = -kbSize.height
        UIView.animateWithDuration(duration, delay: 0, options: options, animations: { () -> Void in
            self.layoutIfNeeded()
        }, completion: nil)
    }

    var bottomContents: UIView? {
        didSet {
            for v in bottomView.subviews {
                v.removeFromSuperview()
                for c in bottomView.constraints {
                    if let first = c.firstItem as? UIView where first == v {
                        bottomView.removeConstraint(c)
                    }
                    else if let second = c.firstItem as? UIView where second == v {
                        bottomView.removeConstraint(c)
                    }
                }
            }
            if bottomContents != nil {
                bottomContents!.translatesAutoresizingMaskIntoConstraints = false
                bottomView.addSubview(bottomContents!)
                var constraints: [NSLayoutConstraint] = [];

                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .Bottom, relatedBy: .Equal, toItem: bottomView, attribute: .Bottom, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .Trailing, relatedBy: .Equal, toItem: bottomView, attribute: .Trailing, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .Leading, relatedBy: .Equal, toItem: bottomView, attribute: .Leading, multiplier: 1, constant: 0))
                constraints.append(NSLayoutConstraint(item: bottomContents!, attribute: .Top, relatedBy: .Equal, toItem: bottomView, attribute: .Top, multiplier: 1, constant: 0))
                NSLayoutConstraint.activateConstraints(constraints)
                layoutIfNeeded()
            }
        }
    }

    var inAnswer = false
    func scrollToAnswer(animated: Bool) {
        scrollView.setContentOffset(separator.frame.origin, animated: animated)
        separator.backgroundColor = UIColor.whiteColor()
        separator.tagImage.image = UIImage(named: "chat_header_tag")!
        inAnswer = true
    }
    
    func scrollToChat(animated: Bool) {
        scrollView.setContentOffset(messagesView.frame.origin, animated: animated)
        separator.backgroundColor = Palette.CHAT_BACKGROUND
        separator.tagImage.image = UIImage(named: "chat_footer_tag")!
        inAnswer = false
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

        separator = NSBundle.mainBundle().loadNibNamed("Separator", owner: self, options: [:])[0] as! SeparatorView
        addSubview(scrollView)
        addSubview(bottomView)
        scrollView.addSubview(messagesView)
        scrollView.addSubview(separator)
        scrollView.addSubview(answerView)
        scrollView.pagingEnabled = true
        scrollView.clipsToBounds = false
        messagesView.registerNib(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Incoming))
        messagesView.registerNib(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(CellType.Outgoing))
        messagesView.registerNib(UINib(nibName: "LookingForExpert", bundle: nil), forCellReuseIdentifier: String(CellType.LookingForExpert))
        messagesView.registerNib(UINib(nibName: "AnswerReceived", bundle: nil), forCellReuseIdentifier: String(CellType.AnswerReceived))
        messagesView.registerNib(UINib(nibName: "TaskInProgress", bundle: nil), forCellReuseIdentifier: String(CellType.TaskInProgress))
        messagesView.registerNib(UINib(nibName: "Setup", bundle: nil), forCellReuseIdentifier: String(CellType.Setup))
        messagesView.separatorStyle = .None
        messagesView.backgroundColor = Palette.CHAT_BACKGROUND
        messagesView.bounces = false
        scrollView.backgroundColor = messagesView.backgroundColor
        separator.translatesAutoresizingMaskIntoConstraints = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        answerView.translatesAutoresizingMaskIntoConstraints = false
        messagesView.translatesAutoresizingMaskIntoConstraints = false
        bottomView.translatesAutoresizingMaskIntoConstraints = false
        let pullGesture = UIPanGestureRecognizer(target: self, action: "handlePan:")
        pullGesture.delegate = self
        scrollView.gestureRecognizers!.removeAll()
        scrollView.addGestureRecognizer(pullGesture)
        answerView.scrollView.bounces = false
        messagesViewHConstraint = NSLayoutConstraint(item: messagesView, attribute: .Height, relatedBy: .Equal, toItem: scrollView, attribute: .Height, multiplier: 1, constant: -15)
        answerViewHConstraint = NSLayoutConstraint(item: answerView, attribute: .Height, relatedBy: .Equal, toItem: scrollView, attribute: .Height, multiplier: 1, constant: -15)
        bottomViewBottom = NSLayoutConstraint(item: bottomView, attribute: .Bottom, relatedBy: .Equal, toItem: self, attribute: .BottomMargin, multiplier: 1, constant: 0)
        var constraints: [NSLayoutConstraint] = [];
                // main view constraints
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .Top, relatedBy: .Equal, toItem: self, attribute: .TopMargin, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .Width, relatedBy: .Equal, toItem: self, attribute: .Width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: scrollView, attribute: .Bottom, relatedBy: .Equal, toItem: bottomView, attribute: .Top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: bottomView, attribute: .Leading, relatedBy: .Equal, toItem: self, attribute: .Leading, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: bottomView, attribute: .Trailing, relatedBy: .Equal, toItem: self, attribute: .Trailing, multiplier: 1, constant: 0))
        constraints.append(bottomViewBottom)

                // scroll view constraints
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .Top, relatedBy: .Equal, toItem: scrollView, attribute: .Top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .Width, relatedBy: .Equal, toItem: self, attribute: .Width, multiplier: 1, constant: 0))
        constraints.append(messagesViewHConstraint)
        constraints.append(NSLayoutConstraint(item: messagesView, attribute: .Bottom, relatedBy: .Equal, toItem: separator, attribute: .Top, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .Width, relatedBy: .Equal, toItem: self, attribute: .Width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant:15))
        constraints.append(NSLayoutConstraint(item: separator, attribute: .Bottom, relatedBy: .Equal, toItem: answerView, attribute: .Top, multiplier: 1, constant: 0))

        constraints.append(answerViewHConstraint)
        constraints.append(NSLayoutConstraint(item: answerView, attribute: .Width, relatedBy: .Equal, toItem: self, attribute: .Width, multiplier: 1, constant: 0))
        constraints.append(NSLayoutConstraint(item: answerView, attribute: .Bottom, relatedBy: .Equal, toItem: scrollView, attribute: .Bottom, multiplier: 1, constant: 0))
        NSLayoutConstraint.activateConstraints(constraints)
        messagesView.backgroundView = nil

        addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    var gestureBegin: CGFloat = 0
    var gestureBeginOffset: CGFloat = 0
}

extension OrderDetailsView: UIGestureRecognizerDelegate {
    func gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldReceiveTouch touch: UITouch) -> Bool {
        let y = touch.locationInView(scrollView).y
        return (y > separator.frame.maxY ? y - separator.frame.maxY : separator.frame.minY - y) < 80
    }

    @IBAction
    func handlePan(gestureRecognizer: UIPanGestureRecognizer) {
        switch (gestureRecognizer.state) {
        case .Began:
            gestureBeginOffset = scrollView.contentOffset.y
            gestureBegin = gestureRecognizer.locationInView(nil).y
            break
        case .Changed:
            let translation = gestureRecognizer.translationInView(scrollView)
            let y = gestureRecognizer.locationInView(nil).y
            scrollView.setContentOffset(CGPointMake(0, gestureBeginOffset + gestureBegin - y), animated: false)
            gestureRecognizer.setTranslation(translation, inView: scrollView)
            break
        case .Ended:
            let diffY = gestureRecognizer.locationInView(nil).y - gestureBegin
            if (abs(diffY) > messagesView.frame.height / 4) {
                if (diffY < 0) {
                    scrollToAnswer(true)
                }
                else {
                    scrollToChat(true)
                }
            }
            else {
                scrollView.setContentOffset(CGPointMake(0, gestureBeginOffset), animated: true)
            }
            break
        default:
            break
        }
    }
}
