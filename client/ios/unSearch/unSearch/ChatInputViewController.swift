//
//  ChatInputViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 19/01/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ChatInputViewController: UIViewController, UITextViewDelegate {
    var placeHolder = "Напишите эксперту"
    @IBOutlet weak var text: UITextView!
    var hDiff = CGFloat(0)
    override func viewDidLoad() {
        text.delegate = self
        text.scrollEnabled = false
        hDiff = text.frame.minY + view.frame.height - text.frame.maxY
        textViewDidEndEditing(text)
    }
    
    @IBAction func send(sender: AnyObject) {
        if !text.text.isEmpty && (delegate == nil || delegate!.chatInput(self, didSend: text.text)) {
            text.text = ""
            textViewDidChange(text)
            parent.scrollView.scrollRectToVisible(parent.messagesView.frame, animated: true)
        }
    }
    @IBAction func attach(sender: AnyObject) {
        delegate?.attach(self)
    }
    
    var delegate: ChatInputDelegate?
    
    var parent: MessagesVeiwController {
        return (parentViewController as! MessagesVeiwController)
    }
    
    func textViewDidBeginEditing(textView: UITextView) {
        if (text.textColor == UIColor.lightGrayColor()) {
            text.textColor = UIColor.blackColor()
            text.text = ""
        }
    }
    
    func textViewDidEndEditing(textView: UITextView) {
        if (text.text.isEmpty) {
            text.text = placeHolder
            text.textColor = UIColor.lightGrayColor()
        }
    }

    func textViewDidChange(textView: UITextView) {
        let fixedWidth = text.frame.width
        let prevHeight = text.frame.height
        let newSize = textView.sizeThatFits(CGSizeMake(fixedWidth, CGFloat(MAXFLOAT)))
        if (newSize.height != prevHeight) {
            print(newSize.height + hDiff)
            UITextView.beginAnimations(nil, context: nil)
            UITextView.setAnimationDuration(0.2)
            parent.inputViewHConstraint?.constant = newSize.height + hDiff
            parent.messagesViewHConstraint?.constant = -newSize.height - hDiff - 2
            parent.answerViewHConstraint?.constant = -newSize.height - hDiff - 2
            parent.view.layoutIfNeeded()
            UITextView.commitAnimations()
        }
    }
}

protocol ChatInputDelegate {
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool
    func attach(chatInput: ChatInputViewController)
}
