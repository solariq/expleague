//
//  CameraCaptureView.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 11.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import AVFoundation

class CameraCaptureController: UIViewController {
    let captureSession = AVCaptureSession()
    
    // there can be no device (like in simulator)
    var captureDevice: AVCaptureDevice?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.blackColor()]
        navigationItem.title = nil
        
        let cancelButton = UIBarButtonItem(title: "Отменить", style: .Done, target: self, action: #selector(CameraCaptureController.cancel))
        cancelButton.tintColor = UIColor.blackColor()
        navigationItem.setLeftBarButtonItem(cancelButton, animated: false)

        captureSession.sessionPreset = AVCaptureSessionPreset1280x720
        tryInitCaptureDevice()
        if (captureDevice != nil) {
            startSession()
        }
        else {
            print("No capture device was detected")
        }
    }
    
    func startSession() {
        do {
            try self.captureSession.addInput(AVCaptureDeviceInput(device: self.captureDevice))
        }
        catch _ {
            print("Capture device input initialization failed")
        }
        
        let previewLayer = AVCaptureVideoPreviewLayer(session: self.captureSession)
        self.view.layer.addSublayer(previewLayer)
        previewLayer.frame = self.view.layer.frame
        captureSession.startRunning()
    }
    
    func tryInitCaptureDevice() {
        let devices = AVCaptureDevice.devices()
        
        for device in devices {
            if (device.hasMediaType(AVMediaTypeVideo)) {
                if (device.position == AVCaptureDevicePosition.Back) {
                    self.captureDevice = device as? AVCaptureDevice
                }
            }
        }
    }

    func cancel() {
        dismissViewControllerAnimated(true, completion: nil)
    }
}
