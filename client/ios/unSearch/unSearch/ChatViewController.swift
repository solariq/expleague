//
//  ChatViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 11.07.17.
//  Copyright © 2017 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import StoreKit
import XMPPFramework
import FBSDKCoreKit
import QuickLook
import MobileCoreServices

import unSearchCore


class ChatViewController: UIViewController {
    @IBOutlet weak var bottomConstraint: NSLayoutConstraint!
    @IBOutlet weak var messages: UITableView!
    @IBOutlet weak var input: UIView!
    @IBOutlet weak var inputHeight: NSLayoutConstraint!
    var keyboardTracker: KeyboardStateTracker?
    
    var data: ChatModel!
    let orderAttachmentsModel = OrderAttachmentsModel()
    
    var inputVC: ChatInputViewController {
        return childViewControllers[0] as! ChatInputViewController
    }
    
    func scrollToLastMessage() {
        if let index = data.lastIndex {
            messages.scrollToRow(at: index as IndexPath, at: .top, animated: true)
        }
        data.markAsRead()
    }
    
    func dismissKeyboard() {
        view.endEditing(true)
    }
    
    override func viewDidLoad() {
        messages.register(UINib(nibName: "IncomingMessage", bundle: nil), forCellReuseIdentifier: String(describing: CellType.incoming))
        messages.register(UINib(nibName: "OutgoingMessage", bundle: nil), forCellReuseIdentifier: String(describing: CellType.outgoing))
        messages.register(UINib(nibName: "LookingForExpert", bundle: nil), forCellReuseIdentifier: String(describing: CellType.lookingForExpert))
        messages.register(UINib(nibName: "AnswerReceived", bundle: nil), forCellReuseIdentifier: String(describing: CellType.answerReceived))
        messages.register(UINib(nibName: "TaskInProgress", bundle: nil), forCellReuseIdentifier: String(describing: CellType.taskInProgress))
        messages.register(UINib(nibName: "Setup", bundle: nil), forCellReuseIdentifier: String(describing: CellType.setup))
        messages.separatorStyle = .none
        messages.backgroundColor = Palette.CHAT_BACKGROUND
        messages.bounces = false
        keyboardTracker = KeyboardStateTracker(check: {self.inputVC.text.isFirstResponder}) { (height: CGFloat) -> () in
            self.bottomConstraint.constant = height
            self.view.layoutIfNeeded()
        }
        inputVC.delegate = self
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard)))
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        keyboardTracker?.stop()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        keyboardTracker?.start()
        (parent as! OrderDetailsViewController).stateChanged()
    }
}

extension ChatViewController: ChatInputDelegate {
    func attach(_ input: ChatInputViewController) {
        let addAttachmentAlert = AddAttachmentAlertController(filter: nil) { imageId in
            let alert = UIAlertController(title: "unSearch", message: "Отправить выбранную фотографию эксперту?", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Да", style: .default, handler: {action in
                let attachment = OrderAttachment(imageId: imageId)
                AppDelegate.instance.uploader.upload(attachment)
                QObject.track(attachment, #selector(OrderAttachment.progressChanged)) {
                    if(attachment.progress! < 0) { // error
                        input.progress.tintColor = Palette.ERROR
                        input.progress.progress = 1.0
                        let error = attachment.error != nil ? " : \(attachment.error!)" : "."
                        let warning = UIAlertController(title: "unSearch", message: "Не удалось отослать изображение\(error)", preferredStyle: .alert)
                        warning.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                        self.present(warning, animated: true, completion: nil)
                        return false
                    }
                    else if (attachment.progress! < 1) {
                        input.progress.tintColor = Palette.CONTROL
                        input.progress.progress = attachment.progress!
                        return true
                    }
                    else {
                        input.progress.tintColor = Palette.OK
                        input.progress.progress = attachment.progress!
                        let img = DDXMLElement(name: "image", xmlns: ExpLeagueMessage.EXP_LEAGUE_SCHEME)!
                        img.stringValue = attachment.url.absoluteString
                        self.data.order.send(xml: img, type: "groupchat")
                        
                        return false
                    }
                }
            }))
            alert.addAction(UIAlertAction(title: "Нет", style: .cancel, handler: nil))
            self.present(alert, animated: true, completion: nil)
        }
        addAttachmentAlert.modalPresentationStyle = .overCurrentContext
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
        
        present(addAttachmentAlert, animated: true, completion: nil)
        input.progress.tintColor = UIColor.blue
    }
    
    func chatInput(_ chatInput: ChatInputViewController, didSend text: String) -> Bool {
        let txt = text.trimmingCharacters(in: .whitespaces)
        guard !txt.isEmpty else {
            return false
        }
        data.order.send(text: text)
        return true
    }
    
    func startEditing() {
        scrollToLastMessage()
    }
}

extension ChatViewController: ImageSenderQueue {
    func append(_ id: String, image: UIImage, progress: (UIProgressView) -> Void) {
        progress(inputVC.progress)
    }
    
    func report(_ id: String, status: Bool) {
        inputVC.progress.tintColor = status ? UIColor.green : UIColor.red
    }
}
