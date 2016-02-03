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
    override func viewDidLoad() {
        text.delegate = self
        text.scrollEnabled = false
        text.textContainer
        textViewDidEndEditing(text)
    }
    
    @IBAction func send(sender: AnyObject) {
        if !text.text.isEmpty && (delegate == nil || delegate!.chatInput(self, didSend: text.text)) {
            text.text = ""
            textViewDidChange(text)
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
        UITextView.beginAnimations(nil, context: nil)
        UITextView.setAnimationDuration(0.5)
        let fixedWidth = text.frame.width
        let prevHeight = text.frame.height
        let newSize : CGSize = textView.sizeThatFits(CGSizeMake(fixedWidth, CGFloat(MAXFLOAT)))
        var newFrame : CGRect = textView.frame
        newFrame.size = CGSizeMake(CGFloat(fmaxf((Float)(newSize.width), (Float)(fixedWidth))), newSize.height)
        text.frame = newFrame
        view.frame.size.height += newFrame.height - prevHeight
        parent.adjustSizes()
        UITextView.commitAnimations()
    }
}

protocol ChatInputDelegate {
    func chatInput(chatInput: ChatInputViewController, didSend text: String) -> Bool
    func attach(chatInput: ChatInputViewController)
}
