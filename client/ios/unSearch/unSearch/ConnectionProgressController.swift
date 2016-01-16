//
//  ConnectionProgressController.swift
//  unSearch
//
//  Created by Igor Kuralenok on 16.01.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class ConnectionProgressController{
    weak var progressBar: UIProgressView?
    weak var progressLabel: UILabel?
    
    var completion: (() -> Void)?
    var alert: UIAlertController?
    
    var progress: States {
        get {
            return States(rawValue: (progressBar?.progress)!)!
        }
        set (newState) {
            progressBar?.progress = newState.rawValue
            progressBar?.progressTintColor = UIColor.blueColor()
            progressLabel?.text = "\(newState)"
            if (newState == .Connected) {
                alert?.dismissViewControllerAnimated(true, completion: self.completion)
            }
        }
    }
     
    func error(msg: String) {
        progressBar?.progress = 1.0
        progressBar?.progressTintColor = UIColor.redColor()
        progressLabel?.text = msg
    }
    
    enum States: Float {
        case Disconnected = 0.0
        case SocketOpened = 0.2
        case Negotiations = 0.4
        case Configuring = 0.6
        case Authenticating = 0.8
        case Connected = 1.0
    }
}