//
//  SettingsViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 30.10.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import XMPPFramework
import UIKit

public class SettingsViewController: UIViewController, UITextFieldDelegate, UITextViewDelegate {
    @IBOutlet weak var hostField: UITextField!
    @IBOutlet weak var userField: UITextField!
    @IBOutlet weak var passwdField: UITextField!
    
    @IBOutlet weak var profileSelector: UISegmentedControl!
    @IBOutlet weak var logView: UITextView!
    
    let stream = XMPPStream()
    var testing: ExpLeagueProfile?
    var profiles: [ExpLeagueProfile] {
        return DataController.shared().profiles
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
    
    public override func viewDidLoad() {
        super.viewDidLoad()
        stream?.addDelegate(self, delegateQueue: DispatchQueue.main)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(SettingsViewController.dismissKeyboard)))
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
    }
    
    public override func viewWillAppear(_ animated: Bool) {
        self.testing = ExpLeagueProfile.active
        profileSelector.selectedSegmentIndex = profiles.index(of: testing!)!
        changeConfigType(self)
    }
    
    public override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if (testing != nil) {
            DataController.shared().activate(testing!)
        }
    }
    
    public func textFieldDidEndEditing(_ textField: UITextField) {
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
    public func xmppStreamDidConnect(_ sender: XMPPStream!) {
        log("Connected")
        do {
            try sender.authenticate(withPassword: testing!.passwd);
        }
        catch {
            log(String(describing: error))
        }
    }
    
    public func xmppStreamConnectDidTimeout(_ sender: XMPPStream!) {
        log("Timedout");
    }
    
    public func xmppStreamDidDisconnect(_ sender: XMPPStream!, withError error: Error!) {
        log("Disconnected" + (error != nil ? " with error:\n\(error)" : ""));
    }
    
    public func xmppStreamDidStartNegotiation(_ sender: XMPPStream!) {
        log("Starting negotiations")
    }
    
    public func xmppStream(_ sender: XMPPStream!, socketDidConnect socket: GCDAsyncSocket!) {
        log("Socket opened");
    }
    
    public func xmppStream(_ sender: XMPPStream!, willSecureWithSettings settings: NSMutableDictionary!) {
        log("Configuring");
        settings.setValue(true, forKey: GCDAsyncSocketManuallyEvaluateTrust)
    }
    
    public func xmppStream(_ sender: XMPPStream!, didNotAuthenticate error: DDXMLElement!) {
        var texts = error.elements(forName: "text");
        if (texts.count > 0) {
            let txt = texts[0]
            let text = txt.stringValue ?? ""
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
    
    public func xmppStreamDidRegister(_ sender: XMPPStream!) {
        log("The new user has been registered! Restarting the xmpp stream.")
        do {
            sender.disconnect()
            try sender.connect(withTimeout: XMPPStreamTimeoutNone)
        }
        catch {
            log(String(describing: error))
        }
    }
    
    public func xmppStreamDidAuthenticate(_ sender: XMPPStream!) {
        log("Success!");
        sender.disconnect()
    }
    
    @objc
    public func xmppStream(_ sender: XMPPStream!, didReceive trust: SecTrust!, completionHandler: ((Bool) -> Swift.Void)!) {
        completionHandler(true)
    }
}
