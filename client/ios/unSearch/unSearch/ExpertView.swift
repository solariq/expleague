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
        topAvaDistance.constant = avatarBg.frame.height / 4
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
        super.viewWillAppear(animated)
    }
    
    override func viewWillDisappear(animated: Bool) {
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
