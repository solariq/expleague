//
//  ExpertView.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 13/03/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import CoreGraphics
import XMPPFramework

class ExpertViewController: UIViewController {
    @IBOutlet weak var avatar: AvatarView!
    @IBOutlet weak var avatarBg: UIImageView!
    @IBOutlet weak var avatarBGView: UIView!
    @IBOutlet weak var topAvaDistance: NSLayoutConstraint!
    @IBOutlet weak var name: UILabel!
    @IBOutlet weak var onlineStatus: UILabel!
    @IBOutlet weak var descriptionText: UITextView!
    @IBOutlet weak var score: UILabel!
    @IBOutlet weak var orders: UILabel!
    @IBOutlet weak var button: UIButton!
    
    @IBAction func fire(sender: UIButton) {
        AppDelegate.instance.orderView!.descriptionController.append(expert: expert)
        AppDelegate.instance.tabs.selectedIndex = 0
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        if !UIAccessibilityIsReduceTransparencyEnabled() {
            avatarBGView.backgroundColor = UIColor.clearColor()
            
            let blurEffect = UIBlurEffect(style: UIBlurEffectStyle.Dark)
            let blurEffectView = UIVisualEffectView(effect: blurEffect)
            //always fill the view
            blurEffectView.frame = avatarBGView.bounds
            blurEffectView.autoresizingMask = [.FlexibleWidth, .FlexibleHeight]
            
            avatarBGView.addSubview(blurEffectView)
        } 
        else {
            avatarBg.backgroundColor = UIColor.blackColor()
        }
        navigationController?.navigationBar.tintColor = UIColor.whiteColor()
        button.layer.cornerRadius = button.frame.height / 2
        topAvaDistance.constant = max(avatarBg.frame.height / 4, 64)
        descriptionText.textContainerInset = UIEdgeInsetsZero
        descriptionText.contentInset = UIEdgeInsetsZero
        descriptionText.textContainer.lineFragmentPadding = 0
    }
    
    override func viewWillAppear(animated: Bool) {
        let navigationBar = navigationController?.navigationBar
        navigationBar?.setBackgroundImage(UIImage(), forBarMetrics: UIBarMetrics.Default)
        navigationBar?.shadowImage = UIImage()
        navigationBar?.translucent = true
        
        avatar.image = expert.avatar
        avatarBg.image = expert.avatar
        name.text = expert.name
        if (expert.available) {
            onlineStatus.text = "Онлайн"
            onlineStatus.textColor = Palette.OK
        }
        else {
            onlineStatus.text = "Оффлайн"
            onlineStatus.textColor = Palette.ERROR
        }
        descriptionText.text = "Эксперт в областях: \(expert.tags.joinWithSeparator(", "))\nОбразование: высшее\nВыполнено заказов: \(expert.tasks)"
        score.text = "\(expert.rating) баллов (\(expert.based) оценок)"
        orders.text = "\(expert.myTasks) заказов выполнено"
        AppDelegate.instance.tabs.tabBar.hidden = true
        super.viewWillAppear(animated)
    }
    
    override func viewWillDisappear(animated: Bool) {
        AppDelegate.instance.tabs.tabBar.hidden = false
        super.viewWillDisappear(animated)
    }
    
    var expert: ExpLeagueMember!
    
    init(expert: ExpLeagueMember) {
        self.expert = expert
        super.init(nibName: "ExpertView", bundle: NSBundle.mainBundle())
    }

    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }
}

class AvatarView: UIView {
    override func awakeFromNib() {
        super.awakeFromNib()
        _imageView.clipsToBounds = true;
        addSubview(_imageView)
        _imageView.layer.borderColor = Palette.BORDER.CGColor
        _imageView.layer.borderWidth = 1
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        _imageView.layer.cornerRadius = frame.width / 2;
        _imageView.frame = CGRectMake(0, 0, frame.width, frame.height)
        let size = frame.width/5
        
        if (_onlineTag != nil) {
            _onlineTag!.frame = CGRectMake(frame.width - size - 1, frame.height - size, size, size)
        }
    }
    
    private let _imageView = UIImageView()
    var image: UIImage? {
        get {
            return _imageView.image
        }
        set (image) {
            _imageView.image = image
        }
    }
    
    private var _onlineTag: UIImageView?
    var online: Bool = false {
        didSet {
            let radius = CGFloat(20)
            let color = online ? Palette.OK : Palette.ERROR
            if (_onlineTag == nil) {
                _onlineTag = UIImageView()
                addSubview(_onlineTag!)
            }
            
            UIGraphicsBeginImageContextWithOptions(CGSizeMake(radius, radius), false, 0)
            let ctx = UIGraphicsGetCurrentContext()
            CGContextSaveGState(ctx)
            
            let rect = CGRectMake(0, 0, radius, radius)
            CGContextSetFillColorWithColor(ctx, color.CGColor)
            CGContextFillEllipseInRect(ctx, rect)
            
            CGContextRestoreGState(ctx)
            _onlineTag!.image = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
        }
    }
}
