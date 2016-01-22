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
    @IBOutlet private var messageView: UILabel!
    @IBOutlet var content: UIView!
    var contentWidth: CGFloat {
        return self.frame.width - avatar.frame.width - 34
    }
    
    var size: CGSize = CGSizeMake(70, 50)
    var incoming: Bool = true
    
    override init(style: UITableViewCellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
    }

    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }
    
    var message: String {
        get {
            return self.messageView.text!
        }
        set (txt) {
            self.messageView.text = txt
//            print("width \(self.frame.width), left: \(leftTextMargin), right: \(rightTextMargin)")
            
            let size = txt.boundingRectWithSize(CGSizeMake(contentWidth, CGFloat(MAXFLOAT)), options: NSStringDrawingOptions.UsesLineFragmentOrigin, attributes: [NSFontAttributeName : self.messageView.font], context: nil)
            
            self.size = CGSizeMake(size.width + avatar.frame.width + 26, max(size.height + messageView.frame.minY + 16, 40))
            print("size: \(size.size) final size: \(self.size)")
        }
    }
    
    override func awakeFromNib() {
        super.awakeFromNib()
        print(bubble.image?.size)
        incoming = avatar.frame.minX < frame.width / 2
        if (incoming) {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 30, 30, 10))
        }
        else {
            bubble.image! = bubble.image!.resizableImageWithCapInsets(UIEdgeInsetsMake(10, 10, 30, 30))
        }
        backgroundColor = UIColor.clearColor()
    }
    
    override func drawRect(rect: CGRect) {
        super.drawRect(rect)
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        print(size)
        if (incoming) {
            contentView.frame.origin = CGPointMake(8, 8)
            contentView.frame.size = size
        }
        else {
            contentView.frame.origin = CGPointMake(frame.width - 8 - size.width, 8)
            contentView.frame.size = size
        }
//        contentView.layoutSubviews()
        print(contentView.frame)
    }
    
    override func sizeToFit() {
        super.sizeToFit()
        print("");
    }
}