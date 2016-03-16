//
// Created by Игорь Кураленок on 10.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework
import CloudKit

class AboutViewController: UIViewController {
    @IBOutlet var stars: [UIImageView]!
    
    @IBOutlet weak var navigationBar: UINavigationBar!
    @IBOutlet weak var build: UILabel!
    @IBOutlet weak var inviteButton: UIButton!
    @IBAction func invite(sender: AnyObject) {
        let alert = UIAlertController(title: "Оставьте заявку", message: "С целью сохранения высокого качества работы экспертов и отсутствия очередей, доступ к приложению в данный момент ограничен. Оставьте e-mail Вашего друга и мы свяжемся с ним как только появится возможность.", preferredStyle: .Alert)
        alert.addTextFieldWithConfigurationHandler { (text: UITextField) -> Void in
            text.placeholder = "Введите адрес"
            text.keyboardType = .EmailAddress
            text.delegate = self
        }
        alert.addAction(UIAlertAction(title: "Отослать", style: .Default, handler: { (action: UIAlertAction) -> Void in
            let application = DDXMLElement(name: "application", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            application.setStringValue(self.friend)
            AppDelegate.instance.stream.sendElement(application)
        }))
        alert.addAction(UIAlertAction(title: "Отмена", style: .Cancel, handler: nil))
        self.presentViewController(alert, animated: true, completion: nil)
    }
    
    @IBAction func showSettings(sender: AnyObject) {
        let storyboard = UIStoryboard(name: "Main", bundle:nil)
        let settings = storyboard.instantiateViewControllerWithIdentifier("SettingsViewController")
        
        navigationController!.pushViewController(settings, animated: true)
    }
    
    var friend: String?
    
    override func viewDidLoad() {
        inviteButton.layer.cornerRadius = inviteButton.frame.height / 2
        inviteButton.layer.borderColor = Palette.CONTROL.CGColor
        inviteButton.layer.borderWidth = 2
        inviteButton.clipsToBounds = true
        let system = NSBundle.mainBundle().infoDictionary!
        build.text = "Version \(system["CFBundleShortVersionString"]!) build \(system["CFBundleVersion"]!)\n\(system["BuildDate"]!)"
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), forBarMetrics: .Default)
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
        navigationController!.navigationBar.tintColor = UIColor.whiteColor()
    }
}

extension AboutViewController: UITextFieldDelegate {
    func textFieldDidEndEditing(textField: UITextField) {
        friend = textField.text
    }
}

class SettingsViewController: UIViewController, UITextFieldDelegate, UITextViewDelegate {
    @IBOutlet weak var hostField: UITextField!
    @IBOutlet weak var userField: UITextField!
    @IBOutlet weak var passwdField: UITextField!
    
    @IBOutlet weak var profileSelector: UISegmentedControl!
    @IBOutlet weak var logView: UITextView!

    let stream = XMPPStream()
    var testing: ExpLeagueProfile?
    var profiles: [ExpLeagueProfile] {
        return AppDelegate.instance.profiles!
    }
    
    @IBAction func verifyButton(sender: AnyObject) {
        stream.disconnect();
        
        stream.hostName = testing!.domain
        stream.hostPort = UInt16(testing!.port)
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.myJID = testing!.jid;
        do {
            try stream.connectWithTimeout(XMPPStreamTimeoutNone);
        }
        catch {
            log(String(error));
        }
    }

    @IBAction func changeConfigType(sender: AnyObject) {
        if let control = sender as? UISegmentedControl {
            testing = profiles[control.selectedSegmentIndex]
        }
        hostField.text = testing!.domain + ":" + String(testing!.port)
        userField.text = testing!.login
        passwdField.text = testing!.passwd
    }
    
    @IBAction func clearLog(sender: AnyObject) {
        logView.text.removeAll()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        stream.addDelegate(self, delegateQueue: dispatch_get_main_queue())
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    override func viewWillAppear(animated: Bool) {
        self.testing = AppDelegate.instance.activeProfile
        profileSelector.selectedSegmentIndex = profiles.indexOf(testing!)!
        changeConfigType(self)
    }
    override func viewDidDisappear(animated: Bool) {
        super.viewDidDisappear(animated)
        AppDelegate.instance.activate(testing!)
    }


    func textFieldDidEndEditing(textField: UITextField) {
        let hostParts = hostField.text?.componentsSeparatedByString(":")
        testing!.domain = hostParts![0]
        testing!.login = userField.text!
        testing!.passwd = passwdField.text!
        testing!.port = hostParts!.count > 1 ? Int16(hostParts![1])! : Int16(5222)
        do {
            try testing!.managedObjectContext!.save()
        }
        catch {
            self.log("Unable to save profile \(testing!.name) because of \(error)!")
        }
    }
    
    var timer: NSTimer?
    func log(msg: String) {
        logView.text = logView.text.stringByAppendingString(msg + "\n")
        timer?.invalidate()
        timer = NSTimer.scheduledTimerWithTimeInterval(0.1, target: self, selector: "scrollToBottom", userInfo: nil, repeats: false)
    }
    func scrollToBottom() {
        logView.scrollRangeToVisible(NSRange(location: logView.text.characters.count, length: 0))
    }
}

extension SettingsViewController: XMPPStreamDelegate {
    func xmppStreamDidConnect(sender: XMPPStream!) {
        log("Connected")
        do {
            try sender.authenticateWithPassword(testing!.passwd);
        }
        catch {
            log(String(error))
        }
    }

    func xmppStreamConnectDidTimeout(sender: XMPPStream!) {
        log("Timedout");
    }

    func xmppStreamDidDisconnect(sender: XMPPStream!, withError error: NSError!) {
        log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""));
    }

    func xmppStreamDidStartNegotiation(sender: XMPPStream!) {
        log("Starting negotiations")
    }

    func xmppStream(sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        log("Socket opened");
    }

    func xmppStream(sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        log("Configuring");
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
//        settings.setValue(true, forKey: String(kCFStreamSSLValidatesCertificateChain))
//        settings.setValue(<#T##value: AnyObject?##AnyObject?#>, forKey: <#T##String#>)
    }

    func xmppStream(sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elementsForName("text");
        if (texts.count > 0) {
            if let txt = texts[0] as? DDXMLElement {
                let text = txt.stringValue()
                if ("No such user" == String(text)) {
                    do {
                        log("No such user, trying to register a new one.")
                        try sender.registerWithPassword(testing!.passwd)
                    }
                    catch {
                        log("\(error)")
                    }
                    return
                }
            }
        }
        log("Not authenticate \(error)")
    }

    func xmppStreamDidRegister(sender: XMPPStream!) {
        log("The new user has been registered! Restarting the xmpp stream.")
        do {
            sender.disconnect()
            try sender.connectWithTimeout(XMPPStreamTimeoutNone)
        }
        catch {
            log(String(error))
        }
    }


    func xmppStreamDidAuthenticate(sender: XMPPStream!) {
        log("Success!");
        sender.disconnect()
    }

    func xmppStream(sender: XMPPStream!, didReceiveTrust trust: SecTrustRef, completionHandler: (Bool) -> ()) {
        completionHandler(true)
    }
}
