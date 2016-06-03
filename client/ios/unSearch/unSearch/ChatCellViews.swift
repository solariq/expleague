//
//  Cells.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 20/01/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

enum CellAlignment: Int {
    case Left = 0
    case Right = 1
    case Center = 2
}

class ChatCell: UITableViewCell {
    static let defaultFont = UIFont(name: "Helvetica", size: 15)!
    static let topicFont = UIFont.systemFontOfSize(17)
    var controller: OrderDetailsViewController?
    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = UIColor.clearColor()
        selectionStyle = .None
    }
}

class SimpleChatCell: ChatCell {
    @IBOutlet weak var separator: UIView!
    var action: (() -> Void)?
    @IBAction func fire(sender: UIButton) {
        action?()
    }
    @IBOutlet weak var button: UIButton!
    
    var controlColor = Palette.CONTROL

    class var height: CGFloat {
        return 0.0;
    }
    
    var actionHighlighted = false {
        didSet {
            if (actionHighlighted) {
                separator.hidden = true
                button.backgroundColor = controlColor
                button.setTitleColor(UIColor.whiteColor(), forState: .Normal)
                button.setTitleColor(UIColor.whiteColor(), forState: .Selected)
            }
            else {
                separator.hidden = false
                button.backgroundColor = UIColor.whiteColor()
                button.setTitleColor(controlColor, forState: .Normal)
                button.setTitleColor(controlColor, forState: .Selected)
            }
            button.layoutIfNeeded()
        }
    }
    
    override func awakeFromNib() {
        frame.size.height += 8
        super.awakeFromNib()
        contentView.layer.borderColor = Palette.BORDER.CGColor
        contentView.layer.borderWidth = 2
        contentView.layer.cornerRadius = Palette.CORNER_RADIUS
        contentView.clipsToBounds = true
        contentView.backgroundColor = UIColor.whiteColor()
        backgroundColor = UIColor.clearColor()
    }
    
    override func layoutSubviews() {
        autoresizesSubviews = false
        contentView.clipsToBounds = true
        contentView.backgroundColor = UIColor.whiteColor()
        contentView.frame = CGRectMake(24, 6, frame.width - 48, frame.height - 8)
//        super.layoutSubviews()
    }
}

class SetupChatCell: ChatCell {
    static let ATTACHMENTS_HEIGHT = CGFloat(210)
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var attachmentsView: UICollectionView!
    @IBOutlet weak var topic: UITextView!
    @IBOutlet weak var topicHeight: NSLayoutConstraint!
    @IBOutlet weak var attachmentsHeight: NSLayoutConstraint!
    
    var attachments: Int = 0 {
        didSet {
            if (attachments > 0) {
                attachmentsHeight.constant = SetupChatCell.ATTACHMENTS_HEIGHT
            }
            else {
                attachmentsHeight.constant = 0
            }
        }
    }
    
    var textWidth: CGFloat {
        return frame.width - 32
    }
    
    var textHeight: CGFloat {
        get {
            return topicHeight.constant
        }
        set (h) {
            topicHeight.constant = h
        }
    }
    
    static var labelHeight = CGFloat(18)
    class func height(textHeight size: CGFloat, attachments: Int) -> CGFloat {
        return SetupChatCell.labelHeight + 16 + size + 8 + (attachments > 0 ? SetupChatCell.ATTACHMENTS_HEIGHT : 0);
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = Palette.CHAT_BACKGROUND
        SetupChatCell.labelHeight = status.frame.height
        attachmentsView.registerClass(AttachmentCell.self, forCellWithReuseIdentifier: "AttachmentCell")
        attachmentsView.backgroundView = nil
        attachmentsView.backgroundColor = UIColor.clearColor()
        attachmentsView.translatesAutoresizingMaskIntoConstraints = false

        topic.editable = false
        topic.backgroundColor = UIColor.clearColor()
        topic.textContainerInset = UIEdgeInsetsZero
        topic.contentInset = UIEdgeInsetsZero
        topic.scrollEnabled = false
        topic.textContainer.lineFragmentPadding = 0
    }
}

class AttachmentCell: UICollectionViewCell {
    var content: UIView? {
        willSet (newContent) {
            for v in contentView.subviews {
                v.removeFromSuperview()
            }
            if let content = newContent {
                contentView.addSubview(content)
            }
        }
    }
 
    override func layoutSubviews() {
        super.layoutSubviews()
        content?.frame = contentView.frame
    }
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = Palette.CORNER_RADIUS
        layer.masksToBounds = true
        backgroundColor = UIColor.clearColor()
        backgroundView = nil
        translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError()
    }
}


class MessageChatCell: ChatCell {
    //    @IBOutlet weak var avatar: UIImageView!
    @IBOutlet var content: UIView!
    
    var maxWidth: CGFloat {
        return frame.width - 48
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        content.layer.cornerRadius = Palette.CORNER_RADIUS
        content.clipsToBounds = true
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
    }

    class func height(contentHeight height: CGFloat) -> CGFloat {
        return height + 8;
    }
}

class TaskInProgressCell: SimpleChatCell {
    @IBOutlet weak var patternsView: UICollectionView!
    @IBOutlet weak var pagesCount: UILabel!
    @IBOutlet weak var callsCount: UILabel!
    @IBOutlet weak var topicIcon: UIImageView!
    
    var pages: Int {
        get {
            let parts = pagesCount.text!.componentsSeparatedByString(" ")
            return Int(parts.last!)!
        }
        set(pages) {
            var parts = pagesCount.text!.componentsSeparatedByString(" ")
            parts[parts.count - 1] = String(pages)
            pagesCount.text = parts.joinWithSeparator(" ")
        }
    }

    var calls: Int {
        get {
            let parts = callsCount.text!.componentsSeparatedByString(" ")
            return Int(parts.last!)!
        }
        set(calls) {
            var parts = callsCount.text!.componentsSeparatedByString(" ")
            parts[parts.count - 1] = String(calls)
            callsCount.text = parts.joinWithSeparator(" ")
        }
    }
    
    private static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return TaskInProgressCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.ERROR
        TaskInProgressCell.heightFromNib = frame.height
        patternsView.backgroundView = nil
        patternsView.backgroundColor = UIColor.clearColor()
        patternsView.registerClass(AttachmentCell.self, forCellWithReuseIdentifier: "PatternCell")
        patternsView.translatesAutoresizingMaskIntoConstraints = false
    }
}

class LookingForExpertCell: SimpleChatCell {
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var expertsOnline: UILabel!
    @IBOutlet weak var progress: UIActivityIndicatorView!
    
    static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return LookingForExpertCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.ERROR
        LookingForExpertCell.heightFromNib = frame.height
    }
    
    var online: Int {
        get {
            let parts = expertsOnline.text!.componentsSeparatedByString(" ")
            return Int(parts[0])!
        }
        set(online) {
            var parts = expertsOnline.text!.componentsSeparatedByString(" ")
            parts[0] = String(online)
            expertsOnline.text = parts.joinWithSeparator(" ")
        }
    }
}

class AnswerReceivedCell: TaskInProgressCell {
    @IBOutlet var stars: [UIImageView]!
    var id: String?
    
    private static var heightFromNib1: CGFloat = 120

    override class var height: CGFloat {
        return AnswerReceivedCell.heightFromNib1
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.CONTROL
        self.action = {
            self.controller!.detailsView!.scrollToAnswer(true)
            self.controller!.answer.stringByEvaluatingJavaScriptFromString("document.getElementById('\(self.id!)').scrollIntoView()")
        }
        AnswerReceivedCell.heightFromNib1 = frame.height
    }
    
    var rating: Int? {
        didSet {
            for i in 0..<stars.count {
                if(rating == nil) {
                    stars[i].hidden = true
                    
                }
                else {
                    stars[i].hidden = false
                    stars[i].highlighted = rating! > i
                }
            }
        }
    }
}

class FeedbackCell: UIView {
    override func awakeFromNib() {
        super.awakeFromNib()
        //        no.layer.masksToBounds = true
//        NSLayoutConstraint.activateConstraints([
//            NSLayoutConstraint(item: self, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: frame.height)
//        ])
    }
}

class ExpertPresentation: UIView {
    static var height: CGFloat = CGFloat(50)
    @IBOutlet weak var avatar: AvatarView!
    @IBOutlet weak var name: UILabel!
    @IBOutlet weak var status: UILabel!
    
    override func awakeFromNib() {
        ExpertPresentation.height = frame.height
        frame.size.height += 12
        layer.borderColor = Palette.BORDER.CGColor
        layer.borderWidth = 2
        layer.cornerRadius = Palette.CORNER_RADIUS
        clipsToBounds = true
        backgroundColor = UIColor.whiteColor()
    }
}

class ContinueCell: UIView {
    @IBOutlet weak var no: UIButton!
    @IBOutlet weak var yes: UIButton!
    @IBAction func cancel(sender: AnyObject) {
        self.cancel?()
    }
    @IBAction func fire(sender: UIButton) {
        ok?()
    }
    
    var ok: (() -> ())?
    var cancel: (() -> ())?
    
    
    override func awakeFromNib() {
        super.awakeFromNib()
        yes.backgroundColor = UIColor.whiteColor()
        yes.layer.borderWidth = 2
        yes.layer.borderColor = Palette.CONTROL.CGColor
        yes.layer.cornerRadius = 8
//        yes.layer.masksToBounds = true
        no.backgroundColor = UIColor.whiteColor()
        no.layer.borderWidth = 2
        no.layer.borderColor = Palette.CONTROL.CGColor
        no.layer.cornerRadius = 8
//        no.layer.masksToBounds = true

        NSLayoutConstraint.activateConstraints([
            NSLayoutConstraint(item: self, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: frame.height)
        ])
    }
}

class SaveCell: UIView {
    @IBOutlet weak var no: UIButton!
    @IBOutlet weak var yes: UIButton!
    @IBAction func cancel(sender: AnyObject) {
        ok?()
    }
    @IBAction func fire(sender: UIButton) {
        cancel?()
    }
    
    var ok: (() -> ())?
    var cancel: (() -> ())?
    
    
    override func awakeFromNib() {
        super.awakeFromNib()
        yes.backgroundColor = UIColor.whiteColor()
        yes.layer.borderWidth = 2
        yes.layer.borderColor = Palette.CONTROL.CGColor
        yes.layer.cornerRadius = 8
        //        yes.layer.masksToBounds = true
        no.backgroundColor = UIColor.whiteColor()
        no.layer.borderWidth = 2
        no.layer.borderColor = Palette.CONTROL.CGColor
        no.layer.cornerRadius = 8
        //        no.layer.masksToBounds = true
        
        NSLayoutConstraint.activateConstraints([
            NSLayoutConstraint(item: self, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: frame.height)
            ])
    }
}

class SeparatorView: UIView {
    @IBOutlet weak var tagImage: UIImageView!
    override func awakeFromNib() {
        translatesAutoresizingMaskIntoConstraints = false
        tagImage.translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = Palette.CHAT_BACKGROUND
        super.awakeFromNib()
    }
}

enum CellType: Int {
    case Incoming = 0
    case Outgoing = 1
    case AnswerReceived = 2
    case LookingForExpert = 3
    case TaskInProgress = 4
    case ExpertIdle = 5
    case Feedback = 6
    case Setup = 7
    case Expert = 8
    case None = -1
}
