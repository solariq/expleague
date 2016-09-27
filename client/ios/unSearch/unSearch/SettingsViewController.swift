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

    @IBAction func invite(_ sender: AnyObject) {
        let alert = UIAlertController(title: "Оставьте заявку", message: "С целью сохранения высокого качества работы экспертов и отсутствия очередей, доступ к приложению в данный момент ограничен. Оставьте e-mail вашего друга, и мы свяжемся с ним как только появится возможность.", preferredStyle: .alert)
        alert.addTextField { (text: UITextField) -> Void in
            text.placeholder = "Введите адрес"
            text.keyboardType = .emailAddress
            text.delegate = self
        }
        alert.addAction(UIAlertAction(title: "Отослать", style: .default, handler: { (action: UIAlertAction) -> Void in
            let application = DDXMLElement(name: "application", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
            application.stringValue = self.friend
            let msg = XMPPMessage(type: "normal", child: application)!
            msg.addAttribute(withName: "to", stringValue: AppDelegate.instance.activeProfile!.domain)
            ExpLeagueProfile.active.send(msg)
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
    }
}

extension AboutViewController: UITextFieldDelegate {
    func textFieldDidEndEditing(_ textField: UITextField) {
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
    
    @IBAction func verifyButton(_ sender: AnyObject) {
        stream?.disconnect();
        
        stream?.hostName = testing!.domain
        stream?.hostPort = testing!.port.uint16Value
        stream?.startTLSPolicy = XMPPStreamStartTLSPolicy.required
        stream?.myJID = testing!.jid;
        do {
            try stream?.connect(withTimeout: XMPPStreamTimeoutNone);
        }
        catch {
            log(String(describing: error));
        }
    }

    @IBAction func changeConfigType(_ sender: AnyObject) {
        if let control = sender as? UISegmentedControl {
            testing = profiles[control.selectedSegmentIndex]
        }
        hostField.text = testing!.domain + ":" + String(describing: testing!.port)
        userField.text = testing!.login
        passwdField.text = testing!.passwd
    }
    
    @IBAction func clearLog(_ sender: AnyObject) {
        logView.text.removeAll()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        stream?.addDelegate(self, delegateQueue: DispatchQueue.main)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(SettingsViewController.dismissKeyboard)))
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    override func viewWillAppear(_ animated: Bool) {
        self.testing = AppDelegate.instance.activeProfile
        profileSelector.selectedSegmentIndex = profiles.index(of: testing!)!
        changeConfigType(self)
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        AppDelegate.instance.activate(testing!)
    }


    func textFieldDidEndEditing(_ textField: UITextField) {
        let hostParts = hostField.text?.components(separatedBy: ":")
        testing?.update {
            self.testing!.domain = hostParts![0]
            self.testing!.login = self.userField.text!
            self.testing!.passwd = self.passwdField.text!
            self.testing!.port = hostParts!.count > 1 ? NSNumber(value: Int16(hostParts![1])! as Int16) : NSNumber(value: 5222 as Int)
        }
    }
    
    var timer: Timer?
    func log(_ msg: String) {
        logView.text = logView.text + (msg + "\n")
        timer?.invalidate()
        timer = Timer.scheduledTimer(timeInterval: 0.1, target: self, selector: #selector(SettingsViewController.scrollToBottom), userInfo: nil, repeats: false)
    }
    func scrollToBottom() {
        logView.scrollRangeToVisible(NSRange(location: logView.text.characters.count, length: 0))
    }
}

extension SettingsViewController: XMPPStreamDelegate {
    func xmppStreamDidConnect(_ sender: XMPPStream!) {
        log("Connected")
        do {
            try sender.authenticate(withPassword: testing!.passwd);
        }
        catch {
            log(String(describing: error))
        }
    }

    func xmppStreamConnectDidTimeout(_ sender: XMPPStream!) {
        log("Timedout");
    }

    func xmppStreamDidDisconnect(_ sender: XMPPStream!, withError error: Error!) {
        log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""));
    }

    func xmppStreamDidStartNegotiation(_ sender: XMPPStream!) {
        log("Starting negotiations")
    }

    func xmppStream(_ sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        log("Socket opened");
    }

    func xmppStream(_ sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        log("Configuring");
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
    }

    func xmppStream(_ sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elements(forName: "text");
        if (texts.count > 0) {
            let txt = texts[0]
            let text = txt.stringValue
            if ("No such user" == String(describing: text)) {
                do {
                    log("No such user, trying to register a new one.")
                    try sender.register(withPassword: testing!.passwd)
                }
                catch {
                    log("\(error)")
                }
                return
            }
        }
        log("Not authenticate \(error)")
    }

    func xmppStreamDidRegister(_ sender: XMPPStream!) {
        log("The new user has been registered! Restarting the xmpp stream.")
        do {
            sender.disconnect()
            try sender.connect(withTimeout: XMPPStreamTimeoutNone)
        }
        catch {
            log(String(describing: error))
        }
    }


    func xmppStreamDidAuthenticate(_ sender: XMPPStream!) {
        log("Success!");
        sender.disconnect()
    }

    @objc
    public func xmppStream(_ sender: XMPPStream!, didReceive trust: SecTrust!, completionHandler: ((Bool) -> Swift.Void)!) {
        completionHandler(true)
    }
}
