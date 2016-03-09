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
    var inputViewHConstraint: NSLayoutConstraint?
    override func viewDidLoad() {
        text.delegate = self
        text.scrollEnabled = false
        hDiff = text.frame.minY + view.frame.height - text.frame.maxY
        text.layer.borderWidth = 2
        text.layer.borderColor = UIColor(red: 202, green: 210, blue: 227, alpha: 1).CGColor
        text.layer.cornerRadius = 4
        view!.translatesAutoresizingMaskIntoConstraints = false
        inputViewHConstraint = NSLayoutConstraint(item: view, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: view.frame.height)
        NSLayoutConstraint.activateConstraints([
            inputViewHConstraint!
        ])
        textViewDidEndEditing(text)
    }
    
    @IBAction func send(sender: AnyObject) {
        if !text.text.isEmpty && (delegate == nil || delegate!.chatInput(self, didSend: text.text)) {
            text.text = ""
            textViewDidChange(text)
            parent.detailsView!.scrollToChat(true)
        }
    }
    @IBAction func attach(sender: AnyObject) {
        delegate?.attach(self)
    }
    
    var delegate: ChatInputDelegate?
    
    var parent: OrderDetailsVeiwController {
        return (parentViewController as! OrderDetailsVeiwController)
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
            inputViewHConstraint?.constant = newSize.height + hDiff
            view.layoutIfNeeded()
            UITextView.commitAnimations()
        }
    }
}

protocol ChatInputDelegate {
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool
    func attach(chatInput: ChatInputViewController)
}
