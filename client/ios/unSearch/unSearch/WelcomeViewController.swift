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

class WelcomeViewController: UIViewController {
    @IBOutlet weak var topConstraint: NSLayoutConstraint!
    @IBOutlet weak var owlImage: UIImageView!
    
    @IBOutlet weak var buyButton: UIButton!
    @IBAction func buy(sender: AnyObject) {
        let request = SKProductsRequest(productIdentifiers: ["com.expleague.unSearch.access"])
        request.delegate = self
        request.start()
    }

    @IBOutlet weak var enterCodeButton: UIButton!
    @IBOutlet weak var sendRequestButton: UIButton!
    var active: Bool = false
    override func viewDidLoad() {
        super.viewDidLoad()
        enterCodeButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        enterCodeButton.clipsToBounds = true
        sendRequestButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        sendRequestButton.clipsToBounds = true
        buyButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        buyButton.clipsToBounds = true
        let bar:UINavigationBar! =  self.navigationController?.navigationBar
        
        bar.setBackgroundImage(UIImage(), forBarMetrics: UIBarMetrics.Default)
        bar.shadowImage = UIImage()
        bar.backgroundColor = UIColor(red: 0.0, green: 0.3, blue: 0.5, alpha: 0.0)
        bar.translucent = true
        self.navigationController?.navigationBar.tintColor = UIColor.whiteColor()
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.whiteColor()
        ]
    }
    
    override func preferredStatusBarStyle() -> UIStatusBarStyle {
        return .LightContent
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        guard AppDelegate.instance.activeProfile == nil else {
            performSegueWithIdentifier("Start", sender: self)
            return
        }
        QObject.track(AppDelegate.instance, #selector(AppDelegate.activate(_:))) {
            self.performSegueWithIdentifier("Start", sender: self)
            self.active = false
            return false
        }
        active = true
    }
    
    override func shouldAutorotate() -> Bool {
        return false
    }
    
    override func supportedInterfaceOrientations() -> UIInterfaceOrientationMask {
        return [.Portrait]
    }
    
    override func preferredInterfaceOrientationForPresentation() -> UIInterfaceOrientation {
        return .Portrait
    }
}

extension WelcomeViewController: SKProductsRequestDelegate {
    func productsRequest(request: SKProductsRequest, didReceiveResponse response: SKProductsResponse) {
        guard response.products.count == 1 else {
            let alert = UIAlertController(title: "unSearch", message: "Не удалось запросить платеж", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            self.presentViewController(alert, animated: true, completion: nil)
            return
        }
        let payment = SKPayment(product: response.products[0])
        SKPaymentQueue.defaultQueue().addPayment(payment)
    }
}

extension WelcomeViewController: SKPaymentTransactionObserver {
    func paymentQueue(queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction:AnyObject in transactions {
            if let trans:SKPaymentTransaction = transaction as? SKPaymentTransaction{
                switch trans.transactionState {
                case .Purchased:
                    AppDelegate.instance.setupDefaultProfiles()
                    SKPaymentQueue.defaultQueue().finishTransaction(transaction as! SKPaymentTransaction)
                    break;
                case .Failed:
                    SKPaymentQueue.defaultQueue().finishTransaction(transaction as! SKPaymentTransaction)
                    break;
                default:
                    break;
                }
            }
        }
    }
}

class SendRequestViewController: UIViewController {
    static let emailPattern = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}"
    @IBOutlet weak var emailText: UITextField!
    @IBOutlet weak var sendRequestButton: UIButton!
    @IBAction func sendRequest(sender: AnyObject) {
        let text = emailText.text ?? ""
        guard text.matches(regexp: SendRequestViewController.emailPattern) else {
            let alert = UIAlertController(title: "unSearch", message: "Введенная строка не похожа на E-mail", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            self.presentViewController(alert, animated: true, completion: nil)
            return
        }

        let data = NSData(contentsOfURL: NSURL(string: "https://www.expleague.com/act/sendComment.php?email=\(text)&id=\(abs(UIDevice.currentDevice().identifierForVendor!.UUIDString.hashValue))")!)
        if let d = data, let dataStr = NSString(data: d, encoding: NSUTF8StringEncoding) where dataStr.hasSuffix("1") {
            let alert = UIAlertController(title: "unSearch", message: "Ваша заявка успешно зарегистрирована", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: {action in
                self.navigationController!.popViewControllerAnimated(true)
            }))
            self.presentViewController(alert, animated: true, completion: nil)
            emailText.text = ""
        }
        else {
            let alert = UIAlertController(title: "unSearch", message: "Не удалось зарегистрировать заявку, попробуйте позже", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            
            self.presentViewController(alert, animated: true, completion: nil)
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        sendRequestButton.layer.cornerRadius = sendRequestButton.frame.height / 2
        sendRequestButton.clipsToBounds = true
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        emailText.becomeFirstResponder()
    }
    
    override func shouldAutorotate() -> Bool {
        return false
    }
    
    override func supportedInterfaceOrientations() -> UIInterfaceOrientationMask {
        return [.Portrait]
    }
    
    override func preferredInterfaceOrientationForPresentation() -> UIInterfaceOrientation {
        return .Portrait
    }
}

class EnterCodeViewController: UIViewController {
    @IBOutlet weak var accessCode: UITextField!
    @IBOutlet weak var enterButton: UIButton!
    @IBAction func enter(sender: AnyObject) {
        guard accessCode.text == nil || !accessCode.text!.isEmpty else {
            return
        }
        if let enteredCode = UInt64(accessCode.text!) {
            let deviceId = UInt64(abs(UIDevice.currentDevice().identifierForVendor!.UUIDString.hashValue))
            let code = enteredCode + deviceId
            if (code % 14340987 == 0) {
                navigationController!.popViewControllerAnimated(true)
                dispatch_async(dispatch_get_main_queue()) {
                    AppDelegate.instance.setupDefaultProfiles()
                }
                return
            }
        }
        let alert = UIAlertController(title: "unSearch", message: "Введенный код некорректен или не соответствует устройству заявки", preferredStyle: .Alert)
        alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
        self.presentViewController(alert, animated: true, completion: nil)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        enterButton.layer.cornerRadius = enterButton.frame.height / 2
        enterButton.clipsToBounds = true
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        accessCode.becomeFirstResponder()
    }

    override func shouldAutorotate() -> Bool {
        return false
    }
    
    override func supportedInterfaceOrientations() -> UIInterfaceOrientationMask {
        return [.Portrait]
    }
    
    override func preferredInterfaceOrientationForPresentation() -> UIInterfaceOrientation {
        return .Portrait
    }
}