//
//  ConnectionProgressController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 16.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ConnectionProgressController: UIViewController {
    @IBOutlet weak var progressBar: UIProgressView?
    @IBOutlet weak var progressLabel: UILabel?
 
    var completion: (() -> Void)?
    var alert: UIAlertController?
    
    var progress: States {
        get {
            return States(rawValue: (progressBar?.progress)!)!
        }
        set (newState) {
            progressBar?.progress = newState.rawValue
            progressBar?.progressTintColor = UIColor.blue
            progressLabel?.text = "\(newState)"
            if (newState == .connected) {
                alert?.dismiss(animated: true, completion: self.completion)
            }
        }
    }
    
    func error(_ msg: String) {
        progressBar?.progress = 1.0
        progressBar?.progressTintColor = UIColor.red
        progressLabel?.text = msg
    }
    
    enum States: Float {
        case disconnected = 0.0
        case socketOpened = 0.2
        case negotiations = 0.4
        case configuring = 0.6
        case authenticating = 0.8
        case connected = 1.0
    }
}
