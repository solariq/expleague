//
// Created by Игорь Кураленок on 10.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework
import CloudKit
import FBSDKCoreKit

import unSearchCore

class AboutViewController: UIViewController {
    var initialized = false
    
    @IBOutlet weak var topConstraint: NSLayoutConstraint!
    @IBOutlet weak var slogan: UILabel!
    @IBOutlet weak var build: UILabel!
    @IBOutlet weak var instructionsButton: UIButton!
    @IBOutlet weak var rateUsButton: UIButton!
    @IBOutlet weak var inviteButton: UIButton!

    @IBAction func invite(_ sender: AnyObject) {
        FBSDKAppEvents.logEvent("Invite", parameters: ["user": ExpLeagueProfile.active.jid.user])

        let alert = UIAlertController(title: "Оставьте заявку", message: "С целью сохранения высокого качества работы экспертов и отсутствия очередей, доступ к приложению в данный момент ограничен. Оставьте e-mail вашего друга, и мы свяжемся с ним как только появится возможность.", preferredStyle: .alert)
        alert.addTextField { (text: UITextField) -> Void in
            text.placeholder = "Введите адрес"
            text.keyboardType = .emailAddress
            text.delegate = self
        }
        alert.addAction(UIAlertAction(title: "Отослать", style: .default, handler: { (action: UIAlertAction) -> Void in
            guard self.friend != nil else {
                return
            }
            ExpLeagueProfile.active.application(email: self.friend!)
        }))
        alert.addAction(UIAlertAction(title: "Отмена", style: .cancel, handler: nil))
        self.present(alert, animated: true, completion: nil)
    }
    
    @IBAction func showSettings(_ sender: AnyObject) {
        let storyboard = UIStoryboard(name: "Main", bundle:nil)
        let settings = storyboard.instantiateViewController(withIdentifier: "SettingsViewController")
        
        navigationController!.pushViewController(settings, animated: true)
    }
    
    @IBAction func instructions(_ sender: AnyObject) {
        UIApplication.shared.openURL(URL(string: "http://unsearch.expleague.com/help/")!)
    }
    
    @IBAction func rateUs(_ sender: AnyObject) {
        UIApplication.shared.openURL(URL(string: "itms-apps://itunes.apple.com/app/id1080695101")!)
    }
    
    var friend: String?
    
    func updateSize(_ size: CGSize) {
        guard initialized else {
            return
        }
        let isLandscape = size.height < size.width
        build.isHidden = isLandscape
        instructionsButton.isHidden = isLandscape
        rateUsButton.isHidden = isLandscape
        topConstraint.constant = size.height * 0.1
    }
    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        updateSize(size)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        initialized = true
        inviteButton.layer.cornerRadius = inviteButton.frame.height / 2
        inviteButton.layer.borderColor = Palette.CONTROL.cgColor
        inviteButton.layer.borderWidth = 2
        inviteButton.clipsToBounds = true
        topConstraint.constant = view.frame.height * 0.1
        let system = Bundle.main.infoDictionary!
        let date: String = system["BuildDate"] as? String ?? ""
        build.text = "Version \(AppDelegate.versionName())\n\(date)"
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), for: .default)
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.white]
        navigationController!.navigationBar.tintColor = UIColor.white
    }
    
    override func viewWillAppear(_ animated: Bool) {
        updateSize(UIScreen.main.bounds.size)
        FBSDKAppEvents.logEvent("About tab active")
    }
}

extension AboutViewController: UITextFieldDelegate {
    func textFieldDidEndEditing(_ textField: UITextField) {
        friend = textField.text
    }
}
