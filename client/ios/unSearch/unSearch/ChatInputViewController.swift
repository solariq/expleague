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
    @IBOutlet weak var text: UITextView!
    override func viewDidLoad() {
        text.delegate = self
        text.scrollEnabled = false
        text.textContainer
    }
    @IBAction func send(sender: AnyObject) {
    }
    @IBAction func attach(sender: AnyObject) {
    }
    
    var parent: MessagesVeiwController {
        return (parentViewController as! MessagesVeiwController)
    }
    
    func textViewDidBeginEditing(textView: UITextView) {
        parent.scrollView.scrollRectToVisible(CGRectMake(0, 0, view.frame.maxX, view.frame.maxY), animated: true)
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
