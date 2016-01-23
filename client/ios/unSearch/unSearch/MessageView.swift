//
//  Cells.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 20/01/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class MessageView: UITableViewCell {
    @IBOutlet private var bubble: UIImageView!
    @IBOutlet private var avatar: UIImageView!
    @IBOutlet var content: UIView!
    var contentWidth: CGFloat {
        return self.frame.width - avatar.frame.width - 18 - 24
    }
    
    let extraHeight = CGFloat(24)
    var contentSize: CGSize = CGSizeMake(70, 50)
    var incoming: Bool = true
    
    override init(style: UITableViewCellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
    }

    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        incoming = avatar.frame.minX < frame.width / 2
        if (incoming) {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 30, 30, 10))
        }
        else {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 10, 30, 30))
        }
        backgroundColor = UIColor.clearColor()
        selectionStyle = .None
    }
    
    override func drawRect(rect: CGRect) {
        super.drawRect(rect)
    }
    
    override func layoutSubviews() {
//        super.layoutSubviews()
        let size = CGSizeMake(contentSize.width + avatar.frame.width + 18 + 8, max(35 + 8, contentSize.height + extraHeight) - 8)
        if (incoming) {
            contentView.frame.origin = CGPointMake(8, 8)
            contentView.frame.size = size
        }
        else {
            contentView.frame.origin = CGPointMake(frame.width - 8 - size.width, 8)
            contentView.frame.size = size
        }
//        contentView.layoutSubviews()
    }
}