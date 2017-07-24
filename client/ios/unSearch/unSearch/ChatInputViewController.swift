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
    var hDiff = CGFloat(16)
    var inputViewHConstraint: NSLayoutConstraint?
    @IBOutlet weak var progress: UIProgressView!
    override func viewDidLoad() {
        text.delegate = self
        text.isScrollEnabled = false
        text.layer.borderWidth = 2
        text.layer.borderColor = Palette.BORDER.cgColor
        text.layer.cornerRadius = Palette.CORNER_RADIUS
        view!.translatesAutoresizingMaskIntoConstraints = false
        progress.progress = 0.0
        progress.backgroundColor = Palette.CONTROL_BACKGROUND
        textViewDidEndEditing(text)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        inputViewHConstraint = (parent as! ChatViewController).inputHeight
        inputViewHConstraint?.constant = 49
    }
    
    @IBAction func send(_ sender: AnyObject) {
        resignFirstResponder()
        if !text.text.isEmpty && text.text != placeHolder && (delegate == nil || delegate!.chatInput(self, didSend: text.text)) {
            text.text = ""
            textViewDidChange(text)
            let fixedWidth = text.frame.width
            let newSize = text.sizeThatFits(CGSize(width: fixedWidth, height: CGFloat(MAXFLOAT)))
            UIView.animate(withDuration: 0.3, delay: 0, options: [], animations: {
                self.inputViewHConstraint?.constant = newSize.height + self.hDiff
            }, completion: { (Bool) -> () in
                self.view.layoutIfNeeded()
                self.owner.view.layoutIfNeeded()
            })
        }
    }
    @IBAction func attach(_ sender: AnyObject) {
        delegate?.attach(self)
    }
    
    var delegate: ChatInputDelegate?
    
    var owner: ChatViewController {
        return parent as! ChatViewController
    }
    
    func textViewDidBeginEditing(_ textView: UITextView) {
        if (text.textColor == UIColor.lightGray) {
            text.textColor = UIColor.black
            text.text = ""
            delegate?.startEditing()
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
            UIView.animate(withDuration: 0.3, delay: 0, options: [], animations: {
                self.inputViewHConstraint?.constant = newSize.height + self.hDiff
            }, completion: { (Bool) -> () in
                self.view.layoutIfNeeded()
                self.owner.view.layoutIfNeeded()
            })
        }
    }
}

protocol ChatInputDelegate {
    func chatInput(_ chatInput: ChatInputViewController, didSend text: String) -> Bool
    func attach(_ chatInput: ChatInputViewController)
    func startEditing()
}
