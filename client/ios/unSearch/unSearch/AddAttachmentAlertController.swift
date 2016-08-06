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
    
    @IBOutlet weak var chooseFromGalleryButton: UIButton!

    let parent: UIViewController?
    let imageAttachmentCallback: (String) -> ()
    let filter: [String]?
    
    init(parent: UIViewController?, filter: [String]? = nil, imageAttachmentCallback: (String) -> ()) {
        self.parent = parent
        self.filter = filter
        self.imageAttachmentCallback = imageAttachmentCallback
        super.init(nibName: "AddAttachmentAlert", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        imageCollection.registerClass(ImagePreview.self, forCellWithReuseIdentifier: "ImagePreview")
        
        imageCollection.delegate = self
        imageCollection.dataSource = self
        imageCollection.userInteractionEnabled = true
        imageCollection.reloadData()
        automaticallyAdjustsScrollViewInsets = false
        var buttons: [UIView] = []
        buttons.append(imageCollection)
        buttons.append(chooseFromGalleryButton)
        buttons.append(capturePhotoButton)
        buttons.append(cancelButton)


        for component in buttons {
            component.layer.cornerRadius = Palette.CORNER_RADIUS
            component.clipsToBounds = true
        }
    }
    
    @IBAction func onCancel(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func onCapturePhoto(sender: AnyObject) {
        let picker =  UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .Camera
        
        presentViewController(picker, animated: true, completion: nil)
    }
    
    @IBAction func onSelectFromGallery(sender: AnyObject) {
        let picker = UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .PhotoLibrary
        presentViewController(picker, animated: true, completion: nil)
    }
}

extension AddAttachmentAlertController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : AnyObject]) {
        let mediaType = info[UIImagePickerControllerMediaType] as? String ?? ""
        switch(mediaType) {
        case "public.movie": break
        case "public.image":
            if let url = info[UIImagePickerControllerReferenceURL] as? NSURL,
                let fetch = PHAsset.fetchAssetsWithALAssetURLs([url], options:nil).lastObject as? PHAsset {
                    picker.dismissViewControllerAnimated(true, completion: {
                    self.dismissViewControllerAnimated(true, completion: nil)
                    self.imageAttachmentCallback(fetch.localIdentifier)
                })
                return
            }
            else if let image = info[UIImagePickerControllerOriginalImage] as? UIImage {
                var holder: PHObjectPlaceholder?
                PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                    let request = PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                    holder = request.placeholderForCreatedAsset
                }) { (rc, error) in
                    dispatch_async(dispatch_get_main_queue()) {
                        guard !rc || holder == nil else {
                            picker.dismissViewControllerAnimated(true, completion: {
                                self.dismissViewControllerAnimated(true, completion: nil)
                            })
                            self.imageAttachmentCallback(holder!.localIdentifier)
                            return
                        }
                        let unableToSave = UIAlertController(title: "unSearch", message: "Не удалось сохранить снимок \(error != nil ? ": \(error)" : ".")", preferredStyle: .Alert)
                        unableToSave.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
                        picker.presentViewController(unableToSave, animated: true, completion: nil)
                    }
                }
                return
            }
        default: break
        }
        let unableToPick = UIAlertController(title: "unSearch", message: "Невозможно приложить выбранный результат ", preferredStyle: .Alert)
        unableToPick.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
        picker.presentViewController(unableToPick, animated: true, completion: nil)
    }
}

extension AddAttachmentAlertController:UICollectionViewDelegate, UICollectionViewDataSource {
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : getNumberOfImagesInCollection()
    }
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("ImagePreview", forIndexPath: indexPath) as! ImagePreview
        let requestOptions = PHImageRequestOptions()
        requestOptions.synchronous = true
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        if let fetchResult: PHFetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaType.Image, options: fetchOptions) {
            let asset = fetchResult.objectAtIndex(fetchResult.count - 1 - indexPath.item) as! PHAsset
            cell.imageLocalIdentifier = asset.localIdentifier
            cell.selectedMark.hidden = !(self.filter?.contains(asset.localIdentifier) ?? false)
            PHAsset.fetchSquareThumbnail(cell.image.frame.width, localId: asset.localIdentifier) { (image, _) in
                cell.image.image = image
            }
        }

        cell.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(imageTapped)))

        return cell
    }

    func imageTapped(sender: UITapGestureRecognizer) {
        if let indexPath = imageCollection.indexPathForItemAtPoint(sender.locationInView(imageCollection)),
            let imagePreview = imageCollection.cellForItemAtIndexPath(indexPath) as? ImagePreview {
            dismissViewControllerAnimated(true, completion: nil)
            imageAttachmentCallback(imagePreview.imageLocalIdentifier!)
        }
    }
    
    func getNumberOfImagesInCollection() -> Int {
        let requestOptions = PHImageRequestOptions()
        requestOptions.synchronous = true
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        if let fetchResult: PHFetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaType.Image, options: fetchOptions) {
            return fetchResult.count - (filter?.count ?? 0)
        }
        else {
            return 0
        }
    }
}

class ImagePreview: UICollectionViewCell {
    let image: UIImageView
    let selectedMark: UIImageView
    var imageLocalIdentifier: String?
    
    override init(frame: CGRect) {
        selectedMark = UIImageView(frame: CGRect(origin: CGPointMake(frame.width - 35, 5) , size: CGSizeMake(25, 25)))
        selectedMark.image = UIImage(named: "attachment_checked")
        selectedMark.layer.zPosition = 5
        selectedMark.hidden = true
        image = UIImageView(frame: CGRect(origin: CGPointZero, size: CGSizeMake(frame.size.width - 5, frame.size.height)))
        image.layer.cornerRadius = Palette.CORNER_RADIUS
        image.clipsToBounds = true
        super.init(frame: frame)

        self.contentView.addSubview(self.image)
        self.contentView.addSubview(self.selectedMark)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
