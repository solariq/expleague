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
    static let bgColor = UIColor(red: 218.0/256.0, green: 234.0/256.0, blue: 239.0/256.0, alpha: 1.0)
    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = UIColor.clearColor()
        selectionStyle = .None
    }
}

class SimpleChatCell: ChatCell {
    class var height: CGFloat {
        return 0.0;
    }
    
    override func layoutSubviews() {
        contentView.frame = CGRectMake(16, 4, frame.width - 32, frame.height - 8)
    }
}

class CompositeChatCell: ChatCell {
    @IBOutlet var content: UIView!

    class func height(contentHeight height: CGFloat) -> CGFloat {
        return height;
    }

    class var minSize: CGSize {
        return CGSizeMake(0, 0)
    }

    var contentInsets: UIEdgeInsets {
        return UIEdgeInsetsMake(0,0,0,0)
    }

    var align: CellAlignment {
        return .Center
    }

    final var maxContentSize: CGSize {
        return CGSizeMake(frame.width - contentInsets.left - contentInsets.right, frame.height - contentInsets.top - contentInsets.bottom)
    }

    override func layoutSubviews() {
        let size = CGSizeMake(
            max(content.frame.size.width + contentInsets.left + contentInsets.right, self.dynamicType.minSize.width),
            max(content.frame.size.height + contentInsets.top + contentInsets.bottom, self.dynamicType.minSize.height)
        )
        switch(align) {
        case .Left:
            contentView.frame.origin = CGPointMake(8, 8)
            break
        case .Right:
            contentView.frame.origin = CGPointMake(frame.width - size.width - 8, 8)
            break
        case .Center:
            contentView.frame.origin = CGPointMake((frame.width - size.width) / 2, 8)
            break
        }
        contentView.frame.size = size
    }
}

class SetupChatCell: CompositeChatCell {
    @IBOutlet weak var label: UILabel!
    static var labelHeight = CGFloat(18)
    override class func height(contentHeight size: CGFloat) -> CGFloat {
        return SetupChatCell.labelHeight + 16 + size + 8;
    }

    override var align: CellAlignment {
        return .Center
    }

    override var contentInsets: UIEdgeInsets {
        return UIEdgeInsetsMake(SetupChatCell.labelHeight + 8,8,8,8)
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        backgroundColor = ChatCell.bgColor
        SetupChatCell.labelHeight = label.frame.height
    }
}

class MessageChatCell: CompositeChatCell {
    @IBOutlet weak var bubble: UIImageView!
    @IBOutlet private var avatar: UIImageView!
    var incoming: Bool = true
    static var avatarWidth = CGFloat(35)
    override class func height(contentHeight height: CGFloat) -> CGFloat {
        return max(height + 32, MessageChatCell.minSize.height);
    }

    override class var minSize: CGSize {
        return CGSizeMake(MessageChatCell.avatarWidth + 32, MessageChatCell.avatarWidth + 24)
    }

    override var contentInsets: UIEdgeInsets {
        if (incoming) {
            return UIEdgeInsetsMake(8, 8 + MessageChatCell.avatarWidth, 8, 16)
        }
        else {
            return UIEdgeInsetsMake(8, 16, 8, 8 + MessageChatCell.avatarWidth)
        }
    }

    override var align: CellAlignment {
        if (incoming) {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 30, 30, 10))
        }
        else {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 10, 30, 30))
        }
        return incoming ? .Left : .Right
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        MessageChatCell.avatarWidth = avatar.frame.width
    }
}

class ExpertInProgressCell: SimpleChatCell {
    @IBOutlet weak var expertAvatar: UIImageView!
    @IBOutlet weak var pagesCount: UILabel!
    @IBOutlet weak var callsCount: UILabel!
    @IBOutlet weak var answersCount: UILabel!
    @IBOutlet weak var answerTypeIcon: UIImageView!
    @IBOutlet weak var name: UILabel!
    
    var cancelAction: (() -> Void)?
    @IBAction func cancelTask(sender: UIButton) {
        cancelAction?()
    }

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
    
    private static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return ExpertInProgressCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        ExpertInProgressCell.heightFromNib = frame.height
    }
}


class LookingForExpertCell: SimpleChatCell {
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var expertsOnline: UILabel!
    @IBAction func cancelTask(sender: UIButton) {
    }

    static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return LookingForExpertCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
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

class ExpertIdleCell: SimpleChatCell {
    static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return ExpertIdleCell.heightFromNib;
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        ExpertIdleCell.heightFromNib = frame.height
    }
}

class AnswerReceivedCell: ExpertInProgressCell {
    @IBAction func fire(sender: UIButton) {
        action?()
    }
    
    var action: (() -> ())?

    override class var height: CGFloat {
        return AnswerReceivedCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        AnswerReceivedCell.heightFromNib = frame.height
    }
}

class FeedbackCell: SimpleChatCell {
    @IBAction func fire(sender: UIButton) {
        action?()
    }
    
    var action: (() -> ())?
    
    static var heightFromNib: CGFloat = 120;
    override class var height: CGFloat {
        return FeedbackCell.heightFromNib;
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        FeedbackCell.heightFromNib = frame.height
    }
}


enum CellType: Int {
    case Incoming = 0
    case Outgoing = 1
    case AnswerReceived = 2
    case LookingForExpert = 3
    case ExpertInProgress = 4
    case ExpertIdle = 5
    case Feedback = 6
    case Setup = 7
    case None = -1
}
