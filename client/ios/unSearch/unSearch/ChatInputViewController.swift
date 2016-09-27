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
    @IBOutlet weak var progress: UIProgressView!
    override func viewDidLoad() {
        text.delegate = self
        text.isScrollEnabled = false
        hDiff = text.frame.minY + view.frame.height - text.frame.maxY
        text.layer.borderWidth = 2
        text.layer.borderColor = Palette.BORDER.cgColor
        text.layer.cornerRadius = Palette.CORNER_RADIUS
        view!.translatesAutoresizingMaskIntoConstraints = false
        progress.progress = 0.0
        progress.backgroundColor = Palette.CONTROL_BACKGROUND
        inputViewHConstraint = NSLayoutConstraint(item: view, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: view.frame.height)
        NSLayoutConstraint.activate([
            inputViewHConstraint!
        ])
        textViewDidEndEditing(text)
    }
    
    @IBAction func send(_ sender: AnyObject) {
        if !text.text.isEmpty && text.text != placeHolder && (delegate == nil || delegate!.chatInput(self, didSend: text.text)) {
            text.text = ""
            textViewDidChange(text)
            owner.detailsView!.scrollToChat(true)
        }
    }
    @IBAction func attach(_ sender: AnyObject) {
        delegate?.attach(self)
    }
    
    var delegate: ChatInputDelegate?
    
    var owner: OrderDetailsViewController {
        return parent as! OrderDetailsViewController
    }
    
    func textViewDidBeginEditing(_ textView: UITextView) {
        if (text.textColor == UIColor.lightGray) {
            text.textColor = UIColor.black
            text.text = ""
        }
    }
    
    func textViewDidEndEditing(_ textView: UITextView) {
        if (text.text.isEmpty) {
            text.text = placeHolder
            text.textColor = UIColor.lightGray
        }
    }

    func textViewDidChange(_ textView: UITextView) {
        let fixedWidth = text.frame.width
        let prevHeight = text.frame.height
        let newSize = textView.sizeThatFits(CGSize(width: fixedWidth, height: CGFloat(MAXFLOAT)))
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
    func chatInput(_ chatInput: ChatInputViewController, didSend text: String) -> Bool
    func attach(_ chatInput: ChatInputViewController)
}
