//
// Created by Игорь Кураленок on 10.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework
import CloudKit

class SettingsViewController: UIViewController, UITextFieldDelegate {
    @IBOutlet weak var hostField: UITextField!
    @IBOutlet weak var userField: UITextField!
    @IBOutlet weak var passwdField: UITextField!
    
    @IBOutlet weak var profileSelector: UISegmentedControl!
    @IBOutlet weak var logView: UITextView!

    let stream = XMPPStream();
    
    @IBAction func verifyButton(sender: AnyObject) {
        stream.disconnect();
        
        stream.hostName = settings.host();
        stream.hostPort = settings.port()
        stream.startTLSPolicy = XMPPStreamStartTLSPolicy.Required
        stream.myJID = XMPPJID.jidWithString(settings.user() + "@" + settings.host() + "/settings");
        do {
            try stream.connectWithTimeout(XMPPStreamTimeoutNone);
        }
        catch {
            log(String(error));
        }
    }

    @IBAction func changeConfigType(sender: AnyObject) {
        if let control = sender as? UISegmentedControl {
            settings = SettingsSet.load(control.selectedSegmentIndex)
        }
        hostField.text = settings.host()
        userField.text = settings.user()
        passwdField.text = settings.passwd()
    }
    
    @IBAction func clearLog(sender: AnyObject) {
        logView.text.removeAll()
    }
    var settings: SettingsSet = SettingsSet.active()

    override func viewDidLoad() {
        super.viewDidLoad()
        profileSelector.selectedSegmentIndex = settings.profile
        stream.addDelegate(self, delegateQueue: dispatch_get_main_queue())
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))

        changeConfigType(self)
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
    }

    override func viewDidDisappear(animated: Bool) {
        super.viewDidDisappear(animated)
        ELConnection.instance.reset(settings)
    }


    func textFieldDidEndEditing(textField: UITextField) {
        settings.h = hostField.text!
        settings.u = userField.text!
        settings.p = passwdField.text!
        settings.save()
    }
    
    func log(msg: String) {
        logView.text = logView.text.stringByAppendingString(msg + "\n")
//        let bottomOffset = CGPointMake(0, logView.contentSize.height - 1);
//        print("\(bottomOffset)")
//        logView.setContentOffset(bottomOffset, animated:false);
        let text = NSString(string: logView.text)
        logView.scrollRangeToVisible(NSRange(location: text.length, length: 0))
    }
}

extension SettingsViewController: XMPPStreamDelegate {
    func xmppStreamDidConnect(sender: XMPPStream!) {
        log("Connected")
        do {
            let passwd = settings.passwd()
            try sender.authenticateWithPassword(passwd);
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
                        try sender.registerWithPassword(settings.passwd())
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
