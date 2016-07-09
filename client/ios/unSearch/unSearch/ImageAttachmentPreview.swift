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
    @IBOutlet weak var capturePhotoButton: UIButton!
    @IBOutlet weak var cancelButton: UIButton!
    @IBOutlet weak var imageCollection: UICollectionView!
    
    private let attachments: ImageCollectionPreviewDelegate
    let imageAttachmentCallback: ImageAttachmentCallback

    var parent: UIViewController?
    
    init(parent: UIViewController?, imageAttachmentCallback: ImageAttachmentCallback!) {
        attachments = ImageCollectionPreviewDelegate()
        self.imageAttachmentCallback = imageAttachmentCallback
        self.parent = parent
        super.init(nibName: "AddAttachmentAlert", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        attachments.controller = self

        imageCollection.registerClass(ImagePreview.self, forCellWithReuseIdentifier: "ImagePreview")
        
        imageCollection.delegate = attachments
        imageCollection.dataSource = attachments
        imageCollection.userInteractionEnabled = true
        attachments.view = imageCollection
        imageCollection.reloadData()

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

    @IBAction func onCapturePhoto(sender: AnyObject) {
        let navigation = UINavigationController(rootViewController: CameraCaptureController())
        self.dismissViewControllerAnimated(true, completion: nil)
        self.parent?.presentViewController(navigation, animated: true, completion: nil)
    }
}

class ImageCollectionPreviewDelegate: NSObject, UICollectionViewDelegate, UICollectionViewDataSource {
    var view: UICollectionView?
    var controller: AddAttachmentAlertController?
    
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : getNumberOfImagesInCollection()
    }
    
    //func numberOfSectionsInCollectionView(collectionView: UICollectionView) -> Int {
    //    return 1
    //}
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("ImagePreview", forIndexPath: indexPath) as! ImagePreview
        let requestOptions = PHImageRequestOptions()
        requestOptions.synchronous = true
        
        let imgManager = PHImageManager.defaultManager()
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        if let fetchResult: PHFetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaType.Image, options: fetchOptions) {
            let asset = fetchResult.objectAtIndex(fetchResult.count - 1 - indexPath.item) as! PHAsset
            imgManager.requestImageForAsset(asset, targetSize: CGSize(width: 75, height: 75), contentMode: PHImageContentMode.AspectFill, options: requestOptions, resultHandler: { (image, _) in
                cell.image.image = image
                cell.imageLocalIdentifier = asset.localIdentifier
            })
        }

        cell.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(imageTapped)))

        return cell
    }

    func imageTapped(sender: UITapGestureRecognizer) {
        let indexPath = (view?.indexPathForItemAtPoint(sender.locationInView(view)))!
        if let imagePreview = view?.cellForItemAtIndexPath(indexPath) as! ImagePreview? {
            self.controller?.dismissViewControllerAnimated(true, completion: nil)
            self.controller?.imageAttachmentCallback.onAttach(imagePreview.image.image!, imageId: imagePreview.imageLocalIdentifier!)
        }
    }
    
    func getNumberOfImagesInCollection() -> Int {
        let requestOptions = PHImageRequestOptions()
        requestOptions.synchronous = true
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        if let fetchResult: PHFetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaType.Image, options: fetchOptions) {
            print("fetchResult.count \(fetchResult.count)")
            return fetchResult.count
        }
        else {
            return 0
        }
    }
}

class ImagePreview: UICollectionViewCell {
    var image: UIImageView!
    var imageLocalIdentifier: String?
    
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

protocol ImageAttachmentCallback {
    func onAttach(image: UIImage, imageId: String)
}
