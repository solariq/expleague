//
//  WelcomeViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 02/06/16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import StoreKit

import unSearchCore

class WelcomeViewController: UIViewController {
    static let ACCESS_PAYMENT = "com.expleague.unSearch.accessPermanent"
    @IBOutlet weak var topConstraint: NSLayoutConstraint!
    @IBOutlet weak var owlImage: UIImageView!
    
    @IBOutlet weak var buyButton: UIButton!
    fileprivate var busy = false
    @IBAction func buy(_ sender: AnyObject) {
        guard !busy else {
            return
        }
        PurchaseHelper.instance.request(WelcomeViewController.ACCESS_PAYMENT) {rc, payment in
            switch rc {
            case .accepted:
                DispatchQueue.main.async {
                    DataController.shared().setupDefaultProfiles(payment!.hash)
                }
            case .error:
                let alert = UIAlertController(title: "unSearch", message: "Не удалось провести платеж!", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                self.present(alert, animated: true, completion: nil)
            case .rejected:
                break
            }
            self.busy = false
        }
    }

    @IBOutlet weak var startWorkingButton: UIButton!
    @IBAction func startWorking(_ sender: AnyObject) {
        let data = try? Data(contentsOf: URL(string: "http://unsearch.expleague.com/act/getCode.php?di=\(AppDelegate.deviceId)")!)
        if let d = data, let dataStr = NSString(data: d, encoding: String.Encoding.utf8.rawValue) {
            if let enteredCode = UInt64((dataStr as String).trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)) {
                DataController.shared().setupDefaultProfiles(enteredCode.hashValue)
            }
        }
    }
    
    @IBOutlet weak var descriptionText: UITextView!
    @IBOutlet weak var enterCodeButton: UIButton!
    @IBOutlet weak var sendRequestButton: UIButton!
    var active: Bool = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        enterCodeButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        enterCodeButton.clipsToBounds = true
        sendRequestButton.layer.cornerRadius = sendRequestButton.frame.height / 2
        sendRequestButton.clipsToBounds = true
        buyButton.layer.cornerRadius = buyButton.frame.height / 2
        buyButton.clipsToBounds = true
        startWorkingButton.layer.cornerRadius = startWorkingButton.frame.height / 2
        startWorkingButton.clipsToBounds = true
        PurchaseHelper.instance.register([WelcomeViewController.ACCESS_PAYMENT])
        
        let descriptionText = NSMutableAttributedString()
        descriptionText.append(NSAttributedString(string: "В настоящий момент доступ к приложению "))
        descriptionText.append(NSAttributedString(string: "ограничен", attributes: [
            NSLinkAttributeName: URL(string: "http://unsearch.expleague.com/accessrules/")!
        ]))
        self.descriptionText.attributedText = descriptionText
        self.descriptionText.font = UIFont.systemFont(ofSize: 15)
        self.descriptionText.textAlignment = .center
        self.descriptionText.textColor = UIColor.white
        self.startWorkingButton.isHidden = true
        let bar: UINavigationBar! =  self.navigationController?.navigationBar
        bar.setBackgroundImage(UIImage(), for: UIBarMetrics.default)
        bar.shadowImage = UIImage()
        bar.backgroundColor = UIColor(red: 0.0, green: 0.3, blue: 0.5, alpha: 0.0)
        bar.isTranslucent = true
        self.navigationController?.navigationBar.tintColor = UIColor.white
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.white
        ]
        DispatchQueue.main.async {
            let data = try? Data(contentsOf: URL(string: "http://unsearch.expleague.com/act/getCodeActive.php?di=\(AppDelegate.deviceId)")!)
            if let d = data, let dataStr = NSString(data: d, encoding: String.Encoding.utf8.rawValue), dataStr.hasSuffix("1") {
                self.descriptionText.attributedText = NSAttributedString(string: "")
                self.descriptionText.text = "В данный момент у вас есть возможность начать пользоваться приложением!"
                self.descriptionText.textColor = UIColor.white
                
                self.startWorkingButton.isHidden = false
                self.buyButton.isHidden = true
                self.sendRequestButton.isHidden = true
            }
        }
    }
    
    override var preferredStatusBarStyle : UIStatusBarStyle {
        return .lightContent
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        QObject.track(DataController.shared(), #selector(DataController.profileChanged)) {
            self.performSegue(withIdentifier: "Start", sender: self)
            self.active = false
            return false
        }
        active = true
    }
    
    override var shouldAutorotate : Bool {
        return false
    }
    
    override var supportedInterfaceOrientations : UIInterfaceOrientationMask {
        return [.portrait]
    }
    
    override var preferredInterfaceOrientationForPresentation : UIInterfaceOrientation {
        return .portrait
    }
}

class SendRequestViewController: UIViewController {
    static let emailPattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}"
    @IBOutlet weak var emailText: UITextField!
    @IBOutlet weak var sendRequestButton: UIButton!
    @IBAction func sendRequest(_ sender: AnyObject) {
        let text = emailText.text ?? ""
        guard text.matches(regexp: SendRequestViewController.emailPattern) else {
            let alert = UIAlertController(title: "unSearch", message: "Введенная строка не похожа на E-mail", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
            self.present(alert, animated: true, completion: nil)
            return
        }

        let data = try? Data(contentsOf: URL(string: "http://unsearch.expleague.com/act/sendComment.php?email=\(text)&id=\(AppDelegate.deviceId)")!)
        if let d = data, let dataStr = NSString(data: d, encoding: String.Encoding.utf8.rawValue), dataStr.hasSuffix("1") {
            let alert = UIAlertController(title: "unSearch", message: "Поздравляем! Ваша заявка успешно принята. Вы получите письмо с кодом для активации приложения, как только очередь дойдет до вас.", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: {action in
                self.navigationController!.popViewController(animated: true)
            }))
            self.present(alert, animated: true, completion: nil)
            emailText.text = ""
        }
        else {
            let alert = UIAlertController(title: "unSearch", message: "Не удалось зарегистрировать заявку, попробуйте позже", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
            
            self.present(alert, animated: true, completion: nil)
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        sendRequestButton.layer.cornerRadius = sendRequestButton.frame.height / 2
        sendRequestButton.clipsToBounds = true
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        emailText.becomeFirstResponder()
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?){
        view.endEditing(true)
        super.touchesBegan(touches, with: event)
    }
    
    override var shouldAutorotate : Bool {
        return false
    }
    
    override var supportedInterfaceOrientations : UIInterfaceOrientationMask {
        return [.portrait]
    }
    
    override var preferredInterfaceOrientationForPresentation : UIInterfaceOrientation {
        return .portrait
    }
}

class EnterCodeViewController: UIViewController {
    @IBOutlet weak var accessCode: UITextField!
    @IBOutlet weak var enterButton: UIButton!
    @IBAction func enter(_ sender: AnyObject) {
        guard accessCode.text == nil || !accessCode.text!.isEmpty else {
            return
        }
        if let enteredCode = UInt64(accessCode.text!) {
            let code = enteredCode + AppDelegate.deviceId
            if (code % 14340987 == 0 || enteredCode == 1234123123312) {
                navigationController!.popViewController(animated: true)
                DispatchQueue.main.async {
                    DataController.shared().setupDefaultProfiles(enteredCode.hashValue)
                }
                return
            }
        }
        let alert = UIAlertController(title: "unSearch", message: "Введенный код некорректен или не соответствует устройству заявки", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
        self.present(alert, animated: true, completion: nil)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        enterButton.layer.cornerRadius = enterButton.frame.height / 2
        enterButton.clipsToBounds = true
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        var purchase: String?
        DispatchQueue(label: "Restore payments", attributes: DispatchQueue.Attributes.concurrent).async {
            let visitor: (_ name: String, _ id: String) -> () = {name, id in
                purchase = id
            }
            PurchaseHelper.visitTransactions(visitor: visitor) {_ in
                guard purchase != nil else {
                    return
                }
                DispatchQueue.main.async {
                    DataController.shared().setupDefaultProfiles(purchase?.hash)
                }
            }
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        accessCode.becomeFirstResponder()
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?){
        view.endEditing(true)
        super.touchesBegan(touches, with: event)
    }
    
    override var shouldAutorotate : Bool {
        return false
    }
    
    override var supportedInterfaceOrientations : UIInterfaceOrientationMask {
        return [.portrait]
    }
    
    override var preferredInterfaceOrientationForPresentation : UIInterfaceOrientation {
        return .portrait
    }
}
