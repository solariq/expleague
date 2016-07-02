//
//  ImageAttachmentPreview.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 11.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import Photos

class AddAttachmentAlertController: UIViewController {
    @IBOutlet weak var addPhotoButton: UIButton!
    @IBOutlet weak var capturePhotoButton: UIButton!
    @IBOutlet weak var cancelButton: UIButton!
    @IBOutlet weak var imageCollection: UICollectionView!
    
    private let attachments: ImageCollectionPreviewDelegate
    let orderAttachmentsController = OrderAttachmentsController()

    let picker = UIImagePickerController()
    var pickerDelegate: ImagePickerDelegate?
    var parent: UIViewController?
    
    init(parent: UIViewController?) {
        attachments = ImageCollectionPreviewDelegate()
        self.parent = parent
        super.init(nibName: "AddAttachmentAlert", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        attachments.fetchPhotoAtIndexFromEnd(0)
        attachments.controller = self

        imageCollection.registerClass(ImagePreview.self, forCellWithReuseIdentifier: "ImagePreview")
        
        imageCollection.delegate = attachments
        imageCollection.dataSource = attachments
        imageCollection.userInteractionEnabled = true
        attachments.view = imageCollection
        imageCollection.reloadData()

        let imageSenderQueue = ImageSenderQueueImpl()
        pickerDelegate = ImagePickerDelegate(queue: imageSenderQueue, picker: picker)
        picker.delegate = pickerDelegate
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary

        addPhotoButton.layer.cornerRadius = Palette.CORNER_RADIUS
        addPhotoButton.clipsToBounds = true
        capturePhotoButton.layer.cornerRadius = Palette.CORNER_RADIUS
        capturePhotoButton.clipsToBounds = true
        cancelButton.layer.cornerRadius = Palette.CORNER_RADIUS
        cancelButton.clipsToBounds = true
        imageCollection.layer.cornerRadius = Palette.CORNER_RADIUS
        imageCollection.clipsToBounds = true
    }
    
    @IBAction func onCancel(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func onAddPhoto(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
        self.parent?.presentViewController(self.picker, animated: true, completion: nil)
    }
    
    @IBAction func onCapturePhoto(sender: AnyObject) {
        let navigation = UINavigationController(rootViewController: CameraCaptureController())
        self.dismissViewControllerAnimated(true, completion: nil)
        self.parent?.presentViewController(navigation, animated: true, completion: nil)
    }
}

class ImageCollectionPreviewDelegate: NSObject, UICollectionViewDelegate, UICollectionViewDataSource {
    var view: UICollectionView?
    var cells: [UIImage] = []
    var controller: AddAttachmentAlertController?
    
    var totalImageCountNeeded = 10
    var count: Int {
        return cells.count
    }
    
    func append(image: UIImage) {
        cells.append(image)
    }
    
    func remove(index: Int) {
        cells.removeAtIndex(index)
        view?.reloadData()
    }
    
    func clear() {
        cells.removeAll()
        view?.reloadData()
    }
    
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        let num = section > 0 ? 0 : cells.count
        print("numberOfItemsInSection is \(num)")
        return num
    }
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("ImagePreview", forIndexPath: indexPath) as! ImagePreview
        cell.image.image = cells[indexPath.item]

        cell.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(imageTapped)))

        return cell
    }

    func imageTapped(sender: UITapGestureRecognizer) {
        let indexPath = (view?.indexPathForItemAtPoint(sender.locationInView(view)))!
        print(indexPath)
        if let imagePreview = view?.cellForItemAtIndexPath(indexPath) as! ImagePreview? {
            print(imagePreview)
            AttachmentUploader(callback: nil).uploadImage(imagePreview.image.image!)
            self.controller?.dismissViewControllerAnimated(true, completion: nil)
            let navigation = UINavigationController(rootViewController: (self.controller?.orderAttachmentsController)!)
            self.controller?.parent?.presentViewController(navigation, animated: true, completion: nil)
        }
    }
    
    func fetchPhotoAtIndexFromEnd(index:Int) {
        
        let imgManager = PHImageManager.defaultManager()
        
        // Note that if the request is not set to synchronous
        // the requestImageForAsset will return both the image
        // and thumbnail; by setting synchronous to true it
        // will return just the thumbnail
        let requestOptions = PHImageRequestOptions()
        requestOptions.synchronous = true
        
        // Sort the images by creation date
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key:"creationDate", ascending: true)]
        
        if let fetchResult: PHFetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaType.Image, options: fetchOptions) {
            
            // If the fetch result isn't empty,
            // proceed with the image request
            if fetchResult.count > 0 {
                // Perform the image request
                imgManager.requestImageForAsset(fetchResult.objectAtIndex(fetchResult.count - 1 - index) as! PHAsset, targetSize: CGSize(width: 75, height: 75), contentMode: PHImageContentMode.AspectFill, options: requestOptions, resultHandler: { (image, _) in
                    
                    // Add the returned image to your array
                    self.append(image!)
                    
                    // If you haven't already reached the first
                    // index of the fetch result and if you haven't
                    // already stored all of the images you need,
                    // perform the fetch request again with an
                    // incremented index
                    if index + 1 < fetchResult.count && self.count < self.totalImageCountNeeded {
                        self.fetchPhotoAtIndexFromEnd(index + 1)
                    }
                })
            }
        }
    }
}

class ImagePreview: UICollectionViewCell {
    var image: UIImageView!
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        self.image = UIImageView(frame: CGRectMake(0, 0, 75, 75))
        self.image.contentMode = .ScaleAspectFill
        self.image.clipsToBounds = true
        self.contentView.addSubview(self.image)

        layer.cornerRadius = Palette.CORNER_RADIUS
        layer.masksToBounds = true
        translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

class ImageSenderQueueImpl: ImageSenderQueue {
    func append(id: String, image: UIImage, progress: (UIProgressView)->Void) {
    }
    
    func report(id: String, status: Bool) {
    }
}


