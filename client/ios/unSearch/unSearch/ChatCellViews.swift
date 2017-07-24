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
    case left = 0
    case right = 1
    case center = 2
}

class ChatCell: UITableViewCell {
    static let defaultFont = UIFont(name: "Helvetica", size: 15)!
    static let topicFont = UIFont.systemFont(ofSize: 17)
    var controller: OrderDetailsViewController?
    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = UIColor.clear
        selectionStyle = .none
    }
}

class SimpleChatCell: ChatCell {
    @IBOutlet weak var separator: UIView?
    var action: (() -> Void)?
    @IBAction func fire(_ sender: UIButton) {
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
                separator?.isHidden = true
                button.backgroundColor = controlColor
                button.setTitleColor(UIColor.white, for: UIControlState())
                button.setTitleColor(UIColor.white, for: .selected)
            }
            else {
                separator?.isHidden = false
                button.backgroundColor = UIColor.white
                button.setTitleColor(controlColor, for: UIControlState())
                button.setTitleColor(controlColor, for: .selected)
            }
            button.layoutIfNeeded()
        }
    }
    
    override func awakeFromNib() {
        frame.size.height += 8
        super.awakeFromNib()
        contentView.layer.borderColor = Palette.BORDER.cgColor
        contentView.layer.borderWidth = 2
        contentView.layer.cornerRadius = Palette.CORNER_RADIUS
        contentView.clipsToBounds = true
        contentView.backgroundColor = UIColor.white
        backgroundColor = UIColor.clear
    }
    
    override func layoutSubviews() {
        autoresizesSubviews = false
        contentView.clipsToBounds = true
        contentView.backgroundColor = UIColor.white
        contentView.frame = CGRect(x: 24, y: 6, width: frame.width - 48, height: frame.height - 8)
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
    @IBOutlet weak var labelHeightConstraint: NSLayoutConstraint!
    
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
            return topicHeight.constant - topic.textContainerInset.bottom - topic.textContainerInset.top
        }
        set (h) {
            topicHeight.constant = h + topic.textContainerInset.bottom + topic.textContainerInset.top
        }
    }
    
    static var labelHeight = CGFloat(30)
    class func height(textHeight size: CGFloat, attachments: Int) -> CGFloat {
        return SetupChatCell.labelHeight + size + 4 + (attachments > 0 ? SetupChatCell.ATTACHMENTS_HEIGHT : 0);
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = Palette.CHAT_BACKGROUND
        SetupChatCell.labelHeight = labelHeightConstraint.constant
        attachmentsView.register(AttachmentCell.self, forCellWithReuseIdentifier: "AttachmentCell")
        attachmentsView.backgroundView = nil
        attachmentsView.backgroundColor = UIColor.clear
        attachmentsView.translatesAutoresizingMaskIntoConstraints = false

        topic.isEditable = false
        topic.backgroundColor = UIColor.clear
        topic.textContainerInset = UIEdgeInsets.zero
        topic.contentInset = UIEdgeInsets.zero
        topic.isScrollEnabled = false
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
                contentView.addConstraint(NSLayoutConstraint(item: contentView, attribute: .top, relatedBy: .equal, toItem: content, attribute: .top, multiplier: 1, constant: 0))
                contentView.addConstraint(NSLayoutConstraint(item: contentView, attribute: .left, relatedBy: .equal, toItem: content, attribute: .left, multiplier: 1, constant: 0))
                contentView.addConstraint(NSLayoutConstraint(item: contentView, attribute: .right, relatedBy: .equal, toItem: content, attribute: .right, multiplier: 1, constant: 0))
                contentView.addConstraint(NSLayoutConstraint(item: contentView, attribute: .bottom, relatedBy: .equal, toItem: content , attribute: .bottom, multiplier: 1, constant: 0))
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
        backgroundColor = UIColor.clear
        backgroundView = nil
        translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError()
    }
}


class MessageChatCell: ChatCell {
    @IBOutlet weak var avatar: AvatarView? = nil
    @IBOutlet weak var content: UIView!
    @IBOutlet weak var widthConstraint: NSLayoutConstraint!
    @IBOutlet weak var avatarWidth: NSLayoutConstraint?
    
    var maxWidth: CGFloat {
        return floor(avatar?.isHidden ?? true ? frame.width - 48 : frame.width - 48 - 25)
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        content.autoresizesSubviews = false
        content.autoresizingMask = UIViewAutoresizing()
        content.layer.cornerRadius = Palette.CORNER_RADIUS
        content.clipsToBounds = true
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
            let parts = pagesCount.text!.components(separatedBy: " ")
            return Int(parts.last!)!
        }
        set(pages) {
            var parts = pagesCount.text!.components(separatedBy: " ")
            parts[parts.count - 1] = String(pages)
            pagesCount.text = parts.joined(separator: " ")
        }
    }

    var calls: Int {
        get {
            let parts = callsCount.text!.components(separatedBy: " ")
            return Int(parts.last!)!
        }
        set(calls) {
            var parts = callsCount.text!.components(separatedBy: " ")
            parts[parts.count - 1] = String(calls)
            callsCount.text = parts.joined(separator: " ")
        }
    }
    
    fileprivate static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return TaskInProgressCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.ERROR
        TaskInProgressCell.heightFromNib = frame.height
        patternsView.backgroundView = nil
        patternsView.backgroundColor = UIColor.clear
        patternsView.register(AttachmentCell.self, forCellWithReuseIdentifier: "PatternCell")
        patternsView.translatesAutoresizingMaskIntoConstraints = false
    }
}

class LookingForExpertCell: SimpleChatCell {
    @IBOutlet weak var caption: UILabel!
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
}

class ReopenRoomCell: SimpleChatCell {
    static var heightFromNib: CGFloat = 35;
    override class var height: CGFloat {
        return ReopenRoomCell.heightFromNib;
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.CONTROL
        ReopenRoomCell.heightFromNib = frame.height
    }
}


class AnswerReceivedCell: TaskInProgressCell {
    @IBOutlet var stars: [UIImageView]!
    var id: String?
    
    fileprivate static var heightFromNib1: CGFloat = 120

    override class var height: CGFloat {
        return AnswerReceivedCell.heightFromNib1
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        controlColor = Palette.CONTROL
        self.action = {
            self.controller!.state = .answer
            self.controller!.answer.webView.stringByEvaluatingJavaScript(from: "document.getElementById('\(self.id!)').scrollIntoView()")
        }
        AnswerReceivedCell.heightFromNib1 = frame.height
    }
    
    var rating: Int? {
        didSet {
            for i in 0..<stars.count {
                if(rating == nil) {
                    stars[i].isHidden = true
                    
                }
                else {
                    stars[i].isHidden = false
                    stars[i].isHighlighted = rating! > i
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
        layer.borderColor = Palette.BORDER.cgColor
        layer.borderWidth = 2
        layer.cornerRadius = Palette.CORNER_RADIUS
        clipsToBounds = true
        backgroundColor = UIColor.white
    }
}

class ContinueCell: UIView {
    @IBOutlet weak var no: UIButton!
    @IBOutlet weak var yes: UIButton!
    @IBAction func cancel(_ sender: AnyObject) {
        self.cancel?()
    }
    @IBAction func fire(_ sender: UIButton) {
        ok?()
    }
    
    var ok: (() -> ())?
    var cancel: (() -> ())?
    
    
    override func awakeFromNib() {
        super.awakeFromNib()
        yes.backgroundColor = UIColor.white
        yes.layer.borderWidth = 2
        yes.layer.borderColor = Palette.CONTROL.cgColor
        yes.layer.cornerRadius = 8
//        yes.layer.masksToBounds = true
        no.backgroundColor = UIColor.white
        no.layer.borderWidth = 2
        no.layer.borderColor = Palette.CONTROL.cgColor
        no.layer.cornerRadius = 8
//        no.layer.masksToBounds = true

        NSLayoutConstraint.activate([
            NSLayoutConstraint(item: self, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: frame.height)
        ])
    }
}

class SaveCell: UIView {
    @IBOutlet weak var no: UIButton!
    @IBOutlet weak var yes: UIButton!
    @IBAction func cancel(_ sender: AnyObject) {
        ok?()
    }
    @IBAction func fire(_ sender: UIButton) {
        cancel?()
    }
    
    var ok: (() -> ())?
    var cancel: (() -> ())?
    
    
    override func awakeFromNib() {
        super.awakeFromNib()
        yes.backgroundColor = UIColor.white
        yes.layer.borderWidth = 2
        yes.layer.borderColor = Palette.CONTROL.cgColor
        yes.layer.cornerRadius = 8
        //        yes.layer.masksToBounds = true
        no.backgroundColor = UIColor.white
        no.layer.borderWidth = 2
        no.layer.borderColor = Palette.CONTROL.cgColor
        no.layer.cornerRadius = 8
        //        no.layer.masksToBounds = true
        
        NSLayoutConstraint.activate([
            NSLayoutConstraint(item: self, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: frame.height)
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
    case incoming = 0
    case outgoing = 1
    case answerReceived = 2
    case lookingForExpert = 3
    case taskInProgress = 4
    case expertIdle = 5
    case feedback = 6
    case setup = 7
    case expert = 8
    case none = -1
}
