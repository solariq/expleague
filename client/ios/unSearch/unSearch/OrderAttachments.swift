//
//  OrderAttachments.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 25.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class OrderAttachmentsController: UITableViewController {
    @IBOutlet weak var attachmentsTable: UITableView!
    let orderAttachmentsModel: OrderAttachmentsModel
    
    init(orderAttachmentsModel: OrderAttachmentsModel) {
        self.orderAttachmentsModel = orderAttachmentsModel
        super.init(nibName: "OrderAttachmentsView", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()

        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), forBarMetrics: .Default)
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
        navigationItem.title = "Приложения к запросу"
        let button = UIBarButtonItem(title: "Готово", style: .Done, target: self, action: #selector(OrderAttachmentsController.close))
        button.tintColor = UIColor.whiteColor()
        navigationItem.setRightBarButtonItem(button, animated: false)
        
        attachmentsTable.registerNib(UINib(nibName: "OrderAttachmentTableCell", bundle: NSBundle.mainBundle()), forCellReuseIdentifier: "OrderAttachmentTableCell")
        attachmentsTable.delegate = self
        attachmentsTable.dataSource = self
    }
    
    func close() {
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return 1
    }

    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return orderAttachmentsModel.count
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCellWithIdentifier("OrderAttachmentTableCell", forIndexPath: indexPath) as! OrderAttachmentTableCell
        
        let row = indexPath.row
        let orderAttachment = orderAttachmentsModel.get(row)
        cell.thumbnailView?.image = orderAttachment.image

        orderAttachment.tracker.addNotifier(
            AttachmentUploadProgressNotifier(progressView: cell.attachmentUploadProgress, progressValue: orderAttachment.tracker.progressValue)
        )
        return cell
    }

    override func tableView(tableView: UITableView, canEditRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return true
    }
    
    override func tableView(tableView: UITableView, commitEditingStyle editingStyle: UITableViewCellEditingStyle, forRowAtIndexPath indexPath: NSIndexPath) {
        if editingStyle == UITableViewCellEditingStyle.Delete {
            tableView.beginUpdates()
            orderAttachmentsModel.removeAttachmentAtIndex(indexPath.row)
            tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: UITableViewRowAnimation.Automatic)
            tableView.endUpdates()
        }
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return CGFloat(120)
    }
}

class OrderAttachmentsModel {
    var attachmentsArray: [OrderAttachment] = []
    
    func addAttachment(image: UIImage, imageId: String) {
        let callback = AttachmentUploadTracker()
        let orderAttachment = OrderAttachment(image: image, imageId: imageId, tracker: callback)
        AttachmentUploader(callback: callback).uploadImageByLocalId(orderAttachment.imageId)
        attachmentsArray.append(orderAttachment)
    }
    
    func removeAttachment(orderAttachment: OrderAttachment) {
        attachmentsArray.removeOne(orderAttachment)
    }
    
    func removeAttachmentAtIndex(index: Int) {
        attachmentsArray.removeAtIndex(index)
    }
    
    var count: Int {
        return attachmentsArray.count
    }
    
    func get(index: Int) -> OrderAttachment {
        return attachmentsArray[index]
    }
    
    func getImagesIds() -> [String] {
        return attachmentsArray.map{ return "\(ExpLeagueProfile.active.jid.user)-\($0.imageId.hash).jpeg" }
    }
    
    func clear() {
        attachmentsArray.removeAll()
    }
    
    func completed() -> Bool {
        for attachment in attachmentsArray {
            if !attachment.tracker.completed {
                return false
            }
        }
        return true
    }
}

public class OrderAttachment: Equatable {
    let image: UIImage
    let imageId: String
    let tracker: AttachmentUploadTracker
    
    init(image: UIImage, imageId: String, tracker: AttachmentUploadTracker) {
        self.image = image
        self.imageId = imageId
        self.tracker = tracker
    }
}

public class AttachmentUploadTracker: AttachmentUploadCallback {
    var progressValue: Float = 0
    var notifiers: [AttachmentUploadProgressNotifier] = []
    var completed: Bool = false
    
    func addNotifier(notifier: AttachmentUploadProgressNotifier) {
        notifiers.append(notifier)
    }
    
    func uploadCreated(attachmentId: String, attachment: Any) {}
    func uploadStarted(attachmentId: String, attachment: Any) {}
    func uploadInProgress(attachmentId: String, progressValue: Float) {
        self.progressValue = progressValue
        for notifier in notifiers {
            notifier.uploadInProgress(progressValue)
        }
    }
    func uploadCompleted(attachmentId: String) {
        uploadInProgress(attachmentId, progressValue: 1)
        completed = true
    }
    func uploadFailed(attachmentId: String, httpResponse: NSHTTPURLResponse) {}
}

public class AttachmentUploadProgressNotifier {
    var progressView: UIProgressView
    
    init(progressView: UIProgressView, progressValue: Float) {
        self.progressView = progressView
        uploadInProgress(progressValue)
    }
    
    func uploadInProgress(progressValue: Float) {
        progressView.progress = progressValue
    }
}

public func ==(lhs: OrderAttachment, rhs: OrderAttachment) -> Bool {
    return lhs.image == rhs.image
}

class OrderAttachmentTableCell: UITableViewCell {
    @IBOutlet weak var attachmentUploadProgress: UIProgressView!
    @IBOutlet weak var thumbnailView: UIImageView!
}

