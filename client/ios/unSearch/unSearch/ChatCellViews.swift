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
    var controller: OrderDetailsVeiwController?
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
    
    override func awakeFromNib() {
        super.awakeFromNib()
        frame.size.height += 12
    }
    
    override func layoutSubviews() {
        contentView.frame = CGRectMake(24, 6, frame.width - 48, frame.height - 12)
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
        return CGSizeMake(frame.width - 16 - contentInsets.left - contentInsets.right, frame.height - contentInsets.top - contentInsets.bottom - 4)
    }

    override func layoutSubviews() {
        let size = CGSizeMake(
            max(content.frame.size.width + contentInsets.left + contentInsets.right, self.dynamicType.minSize.width),
            max(content.frame.size.height + contentInsets.top + contentInsets.bottom, self.dynamicType.minSize.height)
        )
        switch(align) {
        case .Left:
            contentView.frame.origin = CGPointMake(8, 4)
            break
        case .Right:
            contentView.frame.origin = CGPointMake(frame.width - size.width - 8, 4)
            break
        case .Center:
            contentView.frame.origin = CGPointMake((frame.width - size.width) / 2 + 4, 4)
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
        backgroundColor = Palette.CHAT_BACKGROUND
        SetupChatCell.labelHeight = label.frame.height
    }
}

class MessageChatCell: CompositeChatCell {
    @IBOutlet weak var bubble: UIImageView!
//    @IBOutlet weak var avatar: UIImageView!
    var incoming: Bool = true
    static var avatarWidth = CGFloat(0)
    override class func height(contentHeight height: CGFloat) -> CGFloat {
        return max(height + 12, MessageChatCell.minSize.height);
    }

    override class var minSize: CGSize {
        return CGSizeMake(MessageChatCell.avatarWidth + 32, MessageChatCell.avatarWidth + 32)
    }

    override var contentInsets: UIEdgeInsets {
        if (incoming) {
            return UIEdgeInsetsMake(6, 16 + MessageChatCell.avatarWidth, 6, 16)
        }
        else {
            return UIEdgeInsetsMake(6, 16, 6, 16 + MessageChatCell.avatarWidth)
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
//        MessageChatCell.avatarWidth = avatar.frame.width
    }
}

class ExpertInProgressCell: SimpleChatCell {
    @IBOutlet weak var expertAvatar: UIImageView!
    @IBOutlet weak var pagesCount: UILabel!
    @IBOutlet weak var callsCount: UILabel!
    @IBOutlet weak var name: UILabel!
    
    var action: (() -> Void)?
    @IBAction func cancelTask(sender: UIButton) {
        action?()
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
    @IBOutlet weak var progress: UIActivityIndicatorView!
    
    var action: (() -> Void)?
    @IBAction func cancelTask(sender: UIButton) {
        action?()
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

class AnswerReceivedCell: ExpertInProgressCell {
    @IBOutlet var stars: [UIImageView]!
    var id: String?
    
    @IBAction func fire(sender: UIButton) {
        self.controller!.detailsView!.scrollToAnswer(true)
        self.controller!.answer.stringByEvaluatingJavaScriptFromString("document.getElementById('\(self.id!)').scrollIntoView()")
    }
    
    override class var height: CGFloat {
        return AnswerReceivedCell.heightFromNib;
    }

    override func awakeFromNib() {
        super.awakeFromNib()
        AnswerReceivedCell.heightFromNib = frame.height
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
    @IBOutlet weak var buttonWidth: NSLayoutConstraint!
    @IBOutlet weak var leftBlockCenter: NSLayoutConstraint!
    @IBOutlet var stars: [UIImageView]!
    @IBOutlet weak var bottle: UIImageView!
    @IBOutlet weak var bottles: UIImageView!
    @IBAction func fire(sender: AnyObject) {
        fire?()
    }
    
    var fire: (() -> ())?
    
    var rate: Int = 0 {
        didSet {
            var index = 0
            for s in stars {
                s.highlighted = index < rate
                index++
            }
            if (rate == 5) {
                stars[3].highlighted = false
            }
        }
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        //        no.layer.masksToBounds = true
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: "handleTap:"))
        NSLayoutConstraint.activateConstraints([
            NSLayoutConstraint(item: self, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: frame.height)
        ])
    }
    
    func handleTap(recognizer: UITapGestureRecognizer) {
        let tap = recognizer.locationInView(self)
        for i in 0..<stars.count {
            let rect = stars[i].frame
            if (distance(tap, CGPointMake(rect.origin.x + rect.width / 2, rect.origin.y + rect.height / 2)) < 30) {
                rate = i + 1
            }
        }
    }
    
    func distance(p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let xDist = p2.x - p1.x
        let yDist = p2.y - p1.y
        return sqrt((xDist * xDist) + (yDist * yDist));
    }
    
    override func updateConstraints() {
        super.updateConstraints()
        let rate = CGFloat(3.3)
        let width = frame.width
        leftBlockCenter.constant = width * ((rate - 1) / rate / 2 - 1/2.0)
        buttonWidth.constant = width / rate
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

class SeparatorView: UIView {
    @IBOutlet weak var tagImage: UIImageView!
    override func awakeFromNib() {
        translatesAutoresizingMaskIntoConstraints = false
        tagImage.translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = Palette.CHAT_BACKGROUND
        super.awakeFromNib()
    }
}

class Palette {
    static let CONTROL = UIColor(red: 88/256.0, green: 135/256.0, blue: 242/256.0, alpha: 1.0)
    static let CONTROL_BACKGROUND = UIColor(red: 249/256.0, green: 249/256.0, blue: 249/256.0, alpha: 1.0)
    static let CHAT_BACKGROUND = UIColor(red: 230/256.0, green: 233/256.0, blue: 234/256.0, alpha: 1.0)
    static let ERROR = UIColor(red: 174/256.0, green: 53/256.0, blue: 53/256.0, alpha: 1.0)
    static let COMMENT = UIColor(red: 63/256.0, green: 84/256.0, blue: 130/256.0, alpha: 1.0)
    static let BORDER = UIColor(red: 202/256.0, green: 210/256.0, blue: 227/256.0, alpha: 1.0)
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
