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
        navigationController!.navigationBar.hidden = true
        enterCodeButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        enterCodeButton.clipsToBounds = true
        sendRequestButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        sendRequestButton.clipsToBounds = true
        buyButton.layer.cornerRadius = enterCodeButton.frame.height / 2
        buyButton.clipsToBounds = true
    }
    
    override func viewWillAppear(animated: Bool) {
        navigationController!.navigationBar.hidden = true
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
    @IBOutlet weak var emailText: UITextField!
    @IBOutlet weak var sendRequestButton: UIButton!
    @IBAction func sendRequest(sender: AnyObject) {
        let data = NSData(contentsOfURL: NSURL(string: "https://www.expleague.com/act/sendComment.php?email=\(emailText.text)&id=\(UIDevice.currentDevice().identifierForVendor!.hashValue)")!)
        if let d = data, let dataStr = NSString(data: d, encoding: NSUTF8StringEncoding) where dataStr.hasSuffix("1") {
            let alert = UIAlertController(title: "unSearch", message: "Ваша заявка успешно зарегистрирована", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            self.presentViewController(alert, animated: true, completion: nil)
        }
        else {
            let alert = UIAlertController(title: "unSearch", message: "Не удалось зарегистрировать заявку, попробуйте позже", preferredStyle: .Alert)
            alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            
            self.presentViewController(alert, animated: true, completion: nil)
        }
    }

    override func viewWillAppear(animated: Bool) {
        navigationController!.navigationBar.hidden = false
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        emailText.becomeFirstResponder()
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
            let code = enteredCode + UInt64(UIDevice.currentDevice().identifierForVendor!.hashValue)
            guard code % 14340987 != 0 else {
                dismissViewControllerAnimated(true) {
                    AppDelegate.instance.setupDefaultProfiles()
                }
                return
            }
        }
        let alert = UIAlertController(title: "unSearch", message: "Введенный код некорректен или не соответствует устройству заявки", preferredStyle: .Alert)
        alert.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
        self.presentViewController(alert, animated: true, completion: nil)
    }

    override func viewWillAppear(animated: Bool) {
        navigationController!.navigationBar.hidden = false
    }
    
    override func viewDidAppear(animated: Bool) {
        super.viewDidAppear(animated)
        accessCode.becomeFirstResponder()
    }
}
