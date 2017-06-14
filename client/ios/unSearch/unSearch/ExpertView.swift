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

import unSearchCore

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
    
    @IBAction func fire(_ sender: UIButton) {
        AppDelegate.instance.orderView!.descriptionController.append(expert: expert)
        AppDelegate.instance.tabs?.selectedIndex = 0
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        if !UIAccessibilityIsReduceTransparencyEnabled() {
            avatarBGView.backgroundColor = UIColor.clear
            
            let blurEffect = UIBlurEffect(style: UIBlurEffectStyle.dark)
            let blurEffectView = UIVisualEffectView(effect: blurEffect)
            //always fill the view
            blurEffectView.frame = avatarBGView.bounds
            blurEffectView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            
            avatarBGView.addSubview(blurEffectView)
        } 
        else {
            avatarBg.backgroundColor = UIColor.black
        }
        navigationController?.navigationBar.tintColor = UIColor.white
        button.layer.cornerRadius = button.frame.height / 2
        descriptionText.textContainerInset = UIEdgeInsets.zero
        descriptionText.contentInset = UIEdgeInsets.zero
        descriptionText.textContainer.lineFragmentPadding = 0
    }
    
    override func viewWillAppear(_ animated: Bool) {
        let navigationBar = navigationController?.navigationBar
        navigationBar?.setBackgroundImage(UIImage(), for: UIBarMetrics.default)
        navigationBar?.shadowImage = UIImage()
        navigationBar?.isTranslucent = true
        AppDelegate.instance.tabs?.tabBar.isHidden = true
        topAvaDistance.constant = max(UIScreen.main.bounds.height * 0.6 / 4, 64)
        update()
        super.viewWillAppear(animated)
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        AppDelegate.instance.tabs?.tabBar.isHidden = false
        super.viewWillDisappear(animated)
    }
    
    var expert: ExpLeagueMember!
    func update() {
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
        let text = NSMutableAttributedString()
        let tags = expert.tags.count > 8 ? Array(expert.tags[0..<8]) : expert.tags
        text.append(NSAttributedString(string: "Эксперт в областях: ", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
        text.append(NSAttributedString(string: tags.joined(separator: ", "), attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 15)]))
        text.append(NSAttributedString(string: "\nОбразование: ", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
        text.append(NSAttributedString(string: "высшее", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 15)]))
        text.append(NSAttributedString(string: "\nВыполнено \(expert.tasks) заказ\(Lang.rusNumEnding(expert.tasks, variants: ["", "а", "ов"])). ", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
        descriptionText.attributedText = text
        descriptionText.textColor = Palette.COMMENT
        score.text = String(format: "%.1f балл\(Lang.rusNumEnding(Int(expert.rating.rounded(.down)), variants: ["", "а", "ов"])) %d оцен\(Lang.rusNumEnding(expert.based, variants: ["ка", "и", "ок"]))", expert.rating, expert.based)
        orders.text = "\(expert.myTasks) ваш\(Lang.rusNumEnding(expert.myTasks, variants: ["", "их", "их"])) заказ\(Lang.rusNumEnding(expert.myTasks, variants: ["", "а", "ов"])) выполне\(expert.myTasks > 1 ? "н" : "но")"
        view.layoutIfNeeded()
    }
    
    func onExpertChanged() {
        DispatchQueue.main.async {
            self.update()
        }
    }
    
    init(expert: ExpLeagueMember) {
        self.expert = expert
        super.init(nibName: "ExpertView", bundle: Bundle.main)
        QObject.connect(expert, signal: #selector(ExpLeagueMember.changed), receiver: self, slot: #selector(onExpertChanged))
    }
    
    deinit {
        QObject.disconnect(self)
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
        _imageView.layer.borderColor = Palette.BORDER.cgColor
        _imageView.layer.borderWidth = 1
        _imageView.backgroundColor = UIColor.clear
        self.backgroundColor = UIColor.clear
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        _imageView.layer.cornerRadius = frame.width / 2;
        _imageView.frame = CGRect(x: 0, y: 0, width: frame.width, height: frame.height)
        let size = floor(frame.width/5)
        
        if (_onlineTag != nil) {
            _onlineTag!.frame = CGRect(x: frame.width - size - 1, y: frame.height - size, width: size, height: size)
        }
    }
    
    fileprivate let _imageView = UIImageView()
    var image: UIImage? {
        get {
            return _imageView.image
        }
        set (image) {
            _imageView.image = image
        }
    }
    
    func onExpertChanged() {
        image = self.expert?.avatar
        online = self.expert?.available ?? false
    }
    
    var expert: ExpLeagueMember? {
        willSet (newExpert) {
            guard newExpert != expert else {
                return
            }
            image = nil
            if (expert != nil) {
                QObject.disconnect(self, sender: self.expert!, signal: #selector(ExpLeagueMember.changed))
            }
            if (newExpert != nil) {
                QObject.connect(newExpert!, signal: #selector(ExpLeagueMember.changed), receiver: self, slot: #selector(self.onExpertChanged))
            }
        }
        didSet {
            if (image == nil) {
                onExpertChanged()
            }
        }
    }
    
    fileprivate var _onlineTag: UIImageView?
    var online: Bool = false {
        didSet {
            let radius = CGFloat(20)
            let color = online ? Palette.OK : Palette.ERROR
            if (_onlineTag == nil) {
                _onlineTag = UIImageView()
                addSubview(_onlineTag!)
            }
            
            UIGraphicsBeginImageContextWithOptions(CGSize(width: radius, height: radius), false, 0)
            let ctx = UIGraphicsGetCurrentContext()
            ctx?.saveGState()
            
            let rect = CGRect(x: 0, y: 0, width: radius, height: radius)
            ctx?.setFillColor(color.cgColor)
            ctx?.fillEllipse(in: rect)
            
            ctx?.restoreGState()
            _onlineTag!.image = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
        }
    }
}
