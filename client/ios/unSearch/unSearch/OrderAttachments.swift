//
//  OrderAttachments.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 25.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import Photos
import UIKit

class OrderAttachmentsController: UIViewController {
    @IBOutlet weak var errorDescription: UITextView!
    @IBOutlet weak var attachmentsCollection: UICollectionView!
    @IBOutlet weak var preview: UIImageView!
    
    let orderAttachmentsModel: OrderAttachmentsModel
    var selected: OrderAttachment?

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), forBarMetrics: .Default)
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
        navigationItem.title = "Приложения к запросу"
        edgesForExtendedLayout = .None
        
        attachmentsCollection.registerClass(AttachedImageCell.self, forCellWithReuseIdentifier: "ImageCell")
        attachmentsCollection.delegate = self
        attachmentsCollection.dataSource = self
        attachmentsCollection.backgroundColor = UIColor.clearColor()
        attachmentsCollection.backgroundView = UIView(frame: CGRectZero)
        errorDescription.hidden = true
        QObject.connect(orderAttachmentsModel, signal: #selector(OrderAttachmentsModel.selectionChanged), receiver: self, slot: #selector(onSelectionChanged))
        QObject.track(orderAttachmentsModel, #selector(OrderAttachmentsModel.attachmentsChanged)) {
            self.attachmentsCollection.reloadData()
            if (self.selected != nil && self.orderAttachmentsModel.get(self.selected!.imageId) == nil) {
                self.selected = nil
                self.preview.image = nil
                self.errorDescription.text = ""
                self.errorDescription.hidden = true
            }
            return true
        }
        onSelectionChanged()
    }
    
    func onSelectionChanged() {
        if (orderAttachmentsModel.selection.isEmpty) {
            let button = UIBarButtonItem(barButtonSystemItem: .Add, target: self, action: #selector(append))
            button.tintColor = UIColor.whiteColor()
            navigationItem.setRightBarButtonItem(button, animated: false)
            automaticallyAdjustsScrollViewInsets = true
        }
        else {
            let button = UIBarButtonItem(barButtonSystemItem: .Trash, target: self, action: #selector(deleteSelection))
            button.tintColor = UIColor.whiteColor()
            navigationItem.setRightBarButtonItem(button, animated: false)
            automaticallyAdjustsScrollViewInsets = true
        }
    }
    
    func deleteSelection() {
        orderAttachmentsModel.selection.forEach(){ attachment in
            orderAttachmentsModel.removeAttachment(attachment)
        }
    }
    
    func append() {
        let addAttachmentAlert = AddAttachmentAlertController(parent: self, filter: orderAttachmentsModel.attachmentsArray.map({$0.imageId})) { imageId in
            self.orderAttachmentsModel.addAttachment(imageId)
        }
        
        addAttachmentAlert.modalPresentationStyle = .OverFullScreen
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
        presentViewController(addAttachmentAlert, animated: true, completion: nil)
    }
    
    init(orderAttachmentsModel: OrderAttachmentsModel) {
        self.orderAttachmentsModel = orderAttachmentsModel
        super.init(nibName: "OrderAttachmentsView", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

extension OrderAttachmentsController: UICollectionViewDelegate, UICollectionViewDataSource {
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : orderAttachmentsModel.attachmentsArray.count
    }
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let attachment = orderAttachmentsModel.attachmentsArray[indexPath.item]
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("ImageCell", forIndexPath: indexPath) as! AttachedImageCell
        cell.attachment = attachment
        cell.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(imageTapped)))
        return cell
    }
    
    func imageTapped(sender: UITapGestureRecognizer) {
        if let indexPath = (attachmentsCollection?.indexPathForItemAtPoint(sender.locationInView(attachmentsCollection))),
            let imagePreview = attachmentsCollection?.cellForItemAtIndexPath(indexPath) as! AttachedImageCell? {
            attachmentsCollection.indexPathsForSelectedItems()?.forEach {
                attachmentsCollection.deselectItemAtIndexPath($0, animated: true)
            }
            attachmentsCollection.selectItemAtIndexPath(indexPath, animated: true, scrollPosition: .None)

            if (selected != imagePreview.attachment) {
                selected = imagePreview.attachment
                if let error = selected?.error {
                    errorDescription.hidden = false
                    errorDescription.text = error
                }
                else {
                    errorDescription.hidden = true
                    let fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers([imagePreview.attachment!.imageId], options: nil)
                    if let asset = fetchResult.objectAtIndex(0) as? PHAsset {
                        PHImageManager.defaultManager().requestImageForAsset(
                            asset,
                            targetSize: self.preview.frame.size,
                            contentMode: PHImageContentMode.AspectFill,
                            options: nil
                        ) { (image, _) in
                            self.preview.image = image
                        }
                    }
                }
            }
            else {
                selected?.selected = !(selected?.selected ?? false)
            }
        }
    }
}

class CircularProgress: UIView {
    var progress: Float?
    override func drawRect(rect: CGRect) {
        let diameter = min(rect.height, rect.width) * 0.68
        let center = rect.center()
        let centralRect = CGRectMake(center.x - diameter / 4.0, center.y - diameter/4.0, diameter / 2.0, diameter / 2.0)
        
        if let p = self.progress where p >= 0 {
            let context = UIGraphicsGetCurrentContext()
            UIColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.4).setFill()
            UIRectFill(rect)
            CGContextSaveGState(context)
            CGContextSetBlendMode(context, .DestinationOut)
            let progress = CGFloat(p)
            let endAngle: CGFloat = progress * 2 * CGFloat(M_PI)
            UIColor.whiteColor().set()
            let border =  UIBezierPath(arcCenter: center,
                                       radius: diameter / 2 - 1,
                                       startAngle: 0,
                                       endAngle: 2.0 * CGFloat(M_PI),
                                       clockwise: true)
            border.lineWidth = 2
            border.stroke()
            let path =  UIBezierPath(arcCenter: center,
                                     radius: diameter / 2 - 4,
                                     startAngle: -CGFloat(M_PI)/2,
                                     endAngle: endAngle - CGFloat(M_PI)/2,
                                     clockwise: true)
            path.lineWidth = 4
            path.stroke()
            CGContextRestoreGState(context)
        }
        else if let p = self.progress where p < 0 {
            Palette.ERROR.set()
            let border =  UIBezierPath(arcCenter: center,
                                       radius: diameter / 2 - 2,
                                       startAngle: 0,
                                       endAngle: 2.0 * CGFloat(M_PI),
                                       clockwise: true)
            border.lineWidth = 6
            border.stroke()
            let paragraphStyle = NSMutableParagraphStyle()
            paragraphStyle.alignment = .Center
            let attrs = [NSFontAttributeName: UIFont.boldSystemFontOfSize(diameter/2.5), NSParagraphStyleAttributeName: paragraphStyle, NSForegroundColorAttributeName: Palette.ERROR]
            "!".drawWithRect(centralRect, options: .UsesLineFragmentOrigin, attributes: attrs, context: nil)
        }
    }
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor.clearColor()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class AttachedImageCell: UICollectionViewCell {
    var attachment: OrderAttachment? {
        willSet (newTracker) {
            QObject.disconnect(self)
        }
        didSet {
            guard attachment != nil else {
                return
            }
            PHAsset.fetchSquareThumbnail(110, localId: attachment!.imageId) { (image, _) in
                self.image.image = image
            }

            onProgressChanged()
            onSelectionChanged()

            QObject.connect(attachment!, signal: #selector(OrderAttachment.progressChanged), receiver: self, slot: #selector(onProgressChanged))
            QObject.connect(attachment!, signal: #selector(OrderAttachment.selectedChanged), receiver: self, slot: #selector(onSelectionChanged))
        }
    }
    var selectedMark: UIImageView!
    var image: UIImageView!
    var progress: CircularProgress!
    
    func onProgressChanged() {
        progress.progress = attachment?.progress ?? (attachment?.error != nil ? -1 : nil)
        progress.setNeedsDisplay()
    }

    func onSelectionChanged() {
        selectedMark.hidden = !(attachment?.selected ?? true)
        layoutIfNeeded()
    }
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        let visibleRect = CGRect(origin: CGPointZero, size: CGSizeMake(frame.size.width, frame.size.height))
        image = UIImageView(frame: visibleRect)
        image.layer.cornerRadius = Palette.CORNER_RADIUS
        image.clipsToBounds = true
        progress = CircularProgress(frame: visibleRect)
        progress.layer.zPosition = 10
        selectedMark = UIImageView(frame: CGRect(origin: CGPointMake(frame.width - 30, 5) , size: CGSizeMake(25, 25)))
        selectedMark.image = UIImage(named: "attachment_checked")
        selectedMark.layer.zPosition = 5
        selectedMark.hidden = true
        self.contentView.addSubview(image)
        self.contentView.addSubview(progress)
        self.contentView.addSubview(selectedMark)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    
    deinit {
        QObject.disconnect(self)
    }
}

class OrderAttachmentsModel {
    var attachmentsArray: [OrderAttachment] = []
    var uploader: AttachmentsUploader {
        return AppDelegate.instance.uploader
    }
    var selection: [OrderAttachment] {
        return attachmentsArray.filter({$0.selected})
    }
    
    @objc
    func attachmentsChanged() {
        QObject.notify(#selector(attachmentsChanged), self)
        selectionChanged()
    }
    
    @objc
    func selectionChanged() {
        QObject.notify(#selector(selectionChanged), self)
    }
    
    func addAttachment(imageId: String) {
        let orderAttachment = OrderAttachment(imageId: imageId)
        uploader.upload(orderAttachment)
        attachmentsArray.append(orderAttachment)
        attachmentsChanged()
        QObject.connect(orderAttachment, signal: #selector(OrderAttachment.selectedChanged), receiver: self, slot: #selector(selectionChanged))
    }
    
    func removeAttachment(orderAttachment: OrderAttachment) {
        attachmentsArray.removeOne(orderAttachment)
        attachmentsChanged()
    }
    
    func removeAttachmentAtIndex(index: Int) {
        attachmentsArray.removeAtIndex(index)
        attachmentsChanged()
    }
    
    var count: Int {
        return attachmentsArray.count
    }
    
    func get(index: Int) -> OrderAttachment {
        return attachmentsArray[index]
    }

    func get(id: String) -> OrderAttachment? {
        return attachmentsArray.filter({$0.imageId == id}).first
    }

    func getImagesIds() -> [String] {
        return attachmentsArray.map{ return "\(ExpLeagueProfile.active.jid.user)-\($0.imageId.hash).jpeg" }
    }
    
    func clear() {
        attachmentsArray.removeAll()
    }
    
    func completed() -> Bool {
        for attachment in attachmentsArray {
            if !attachment.uploaded {
                return false
            }
        }
        return true
    }
}

public class OrderAttachment: Equatable {
    let imageId: String
    let globalId: String
    
    var selected: Bool = false {
        didSet {
            selectedChanged()
        }
    }
    
    var uploaded: Bool = false {
        didSet {
            guard uploaded else {
                return
            }
            progress = nil
        }
    }
    
    var progress: Float? {
        didSet {
            progressChanged()
        }
    }
    var error: String? {
        didSet {
            if (error != nil) {
                progress = nil
            }
        }
    }
    var url: NSURL {
        return AppDelegate.instance.activeProfile!.imageUrl(globalId)
    }
    
    @objc
    func progressChanged() {
        QObject.notify(#selector(progressChanged), self)
    }
    
    @objc
    func selectedChanged() {
        QObject.notify(#selector(selectedChanged), self)
    }
    
    init(imageId: String) {
        self.imageId = imageId
        globalId = "\(ExpLeagueProfile.active.jid.user)-\(imageId.hash).jpeg"
    }
}

public func ==(lhs: OrderAttachment, rhs: OrderAttachment) -> Bool {
    return lhs.imageId == rhs.imageId
}

class OrderAttachmentTableCell: UITableViewCell {
    @IBOutlet weak var attachmentUploadProgress: UIProgressView!
    @IBOutlet weak var thumbnailView: UIImageView!
}

