//
// Created by Игорь Кураленок on 10.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework
import CloudKit

class AboutViewController: UIViewController {
    var initialized = false
    
    @IBOutlet weak var topConstraint: NSLayoutConstraint!
    @IBOutlet weak var slogan: UILabel!
    @IBOutlet weak var build: UILabel!
    @IBOutlet weak var instructionsButton: UIButton!
    @IBOutlet weak var rateUsButton: UIButton!
    @IBOutlet weak var inviteButton: UIButton!

    @IBAction func invite(sender: AnyObject) {
        let alert = UIAlertController(title: "Оставьте заявку", message: "С целью сохранения высокого качества работы экспертов и отсутствия очередей, доступ к приложению в данный момент ограничен. Оставьте e-mail вашего друга, и мы свяжемся с ним как только появится возможность.", preferredStyle: .Alert)
        alert.addTextFieldWithConfigurationHandler { (text: UITextField) -> Void in
            text.placeholder = "Введите адрес"
            text.keyboardType = .EmailAddress
            text.delegate = self
        }
        alert.addAction(UIAlertAction(title: "Отослать", style: .Default, handler: { (action: UIAlertAction) -> Void in
            let application = DDXMLElement(name: "application", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)
            application.setStringValue(self.friend)
            let msg = XMPPMessage(type: "normal", child: application)
            msg.addAttributeWithName("to", stringValue: AppDelegate.instance.activeProfile!.domain)
            ExpLeagueProfile.active.send(msg)
        }))
        alert.addAction(UIAlertAction(title: "Отмена", style: .Cancel, handler: nil))
        self.presentViewController(alert, animated: true, completion: nil)
    }
    
    @IBAction func showSettings(sender: AnyObject) {
        let storyboard = UIStoryboard(name: "Main", bundle:nil)
        let settings = storyboard.instantiateViewControllerWithIdentifier("SettingsViewController")
        
        navigationController!.pushViewController(settings, animated: true)
    }
    
    @IBAction func instructions(sender: AnyObject) {
        UIApplication.sharedApplication().openURL(NSURL(string: "http://unsearch.expleague.com/help/")!)
    }
    
    @IBAction func rateUs(sender: AnyObject) {
        UIApplication.sharedApplication().openURL(NSURL(string: "itms-apps://itunes.apple.com/app/id1080695101")!)
    }
    
    var friend: String?
    
    func updateSize(size: CGSize) {
        guard initialized else {
            return
        }
        let isLandscape = size.height < size.width
        build.hidden = isLandscape
        instructionsButton.hidden = isLandscape
        rateUsButton.hidden = isLandscape
        topConstraint.constant = size.height * 0.1
    }
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        updateSize(size)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        initialized = true
        inviteButton.layer.cornerRadius = inviteButton.frame.height / 2
        inviteButton.layer.borderColor = Palette.CONTROL.CGColor
        inviteButton.layer.borderWidth = 2
        inviteButton.clipsToBounds = true
        topConstraint.constant = view.frame.height * 0.1
        let system = NSBundle.mainBundle().infoDictionary!
        let date: String = system["BuildDate"] as? String ?? ""
        build.text = "Version \(AppDelegate.versionName())\n\(date)"
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), forBarMetrics: .Default)
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
        navigationController!.navigationBar.tintColor = UIColor.whiteColor()
    }
    
    override func viewWillAppear(animated: Bool) {
        updateSize(UIScreen.mainScreen().bounds.size)
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
        stream.hostPort = testing!.port.unsignedShortValue
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
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(SettingsViewController.dismissKeyboard)))
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
        testing?.update {
            self.testing!.domain = hostParts![0]
            self.testing!.login = self.userField.text!
            self.testing!.passwd = self.passwdField.text!
            self.testing!.port = hostParts!.count > 1 ? NSNumber(short: Int16(hostParts![1])!) : NSNumber(long: 5222)
        }
    }
    
    var timer: NSTimer?
    func log(msg: String) {
        logView.text = logView.text.stringByAppendingString(msg + "\n")
        timer?.invalidate()
        timer = NSTimer.scheduledTimerWithTimeInterval(0.1, target: self, selector: #selector(SettingsViewController.scrollToBottom), userInfo: nil, repeats: false)
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
