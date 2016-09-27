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

    let imageAttachmentCallback: (String) -> ()
    let filter: [String]?
    
    init(filter: [String]? = nil, imageAttachmentCallback: @escaping (String) -> ()) {
        self.filter = filter
        self.imageAttachmentCallback = imageAttachmentCallback
        super.init(nibName: "AddAttachmentAlert", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        imageCollection.register(ImagePreview.self, forCellWithReuseIdentifier: "ImagePreview")
        
        imageCollection.delegate = self
        imageCollection.dataSource = self
        imageCollection.isUserInteractionEnabled = true
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
    
    @IBAction func onCancel(_ sender: AnyObject) {
        self.dismiss(animated: true, completion: nil)
    }

    @IBAction func onCapturePhoto(_ sender: AnyObject) {
        let picker =  UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .camera
        
        present(picker, animated: true, completion: nil)
    }
    
    @IBAction func onSelectFromGallery(_ sender: AnyObject) {
        let picker = UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .photoLibrary
        present(picker, animated: true, completion: nil)
    }
}

extension AddAttachmentAlertController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : Any]) {
        let mediaType = info[UIImagePickerControllerMediaType] as? String ?? ""
        switch(mediaType) {
        case "public.movie": break
        case "public.image":
            if let url = info[UIImagePickerControllerReferenceURL] as? URL,
                let fetch = PHAsset.fetchAssets(withALAssetURLs: [url], options:nil).lastObject {
                    picker.dismiss(animated: true, completion: {
                    self.dismiss(animated: true, completion: nil)
                    self.imageAttachmentCallback(fetch.localIdentifier)
                })
                return
            }
            else if let image = info[UIImagePickerControllerOriginalImage] as? UIImage {
                var holder: PHObjectPlaceholder?
                PHPhotoLibrary.shared().performChanges({
                    let request = PHAssetChangeRequest.creationRequestForAsset(from: image)
                    holder = request.placeholderForCreatedAsset
                }) { (rc, error) in
                    DispatchQueue.main.async {
                        guard !rc || holder == nil else {
                            picker.dismiss(animated: true, completion: {
                                self.dismiss(animated: true, completion: nil)
                            })
                            self.imageAttachmentCallback(holder!.localIdentifier)
                            return
                        }
                        let unableToSave = UIAlertController(title: "unSearch", message: "Не удалось сохранить снимок \(error != nil ? ": \(error)" : ".")", preferredStyle: .alert)
                        unableToSave.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                        picker.present(unableToSave, animated: true, completion: nil)
                    }
                }
                return
            }
        default: break
        }
        let unableToPick = UIAlertController(title: "unSearch", message: "Невозможно приложить выбранный результат ", preferredStyle: .alert)
        unableToPick.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
        picker.present(unableToPick, animated: true, completion: nil)
    }
}

extension AddAttachmentAlertController:UICollectionViewDelegate, UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : getNumberOfImagesInCollection()
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "ImagePreview", for: indexPath) as! ImagePreview
        let requestOptions = PHImageRequestOptions()
        requestOptions.isSynchronous = true
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        let fetchResult: PHFetchResult = PHAsset.fetchAssets(with: PHAssetMediaType.image, options: fetchOptions)
        let asset = fetchResult.object(at: fetchResult.count - 1 - (indexPath as NSIndexPath).item)
        cell.imageLocalIdentifier = asset.localIdentifier
        cell.selectedMark.isHidden = !(self.filter?.contains(asset.localIdentifier) ?? false)
        PHAsset.fetchSquareThumbnail(cell.image.frame.width, localId: asset.localIdentifier) { (image, _) in
            cell.image.image = image
        }

        cell.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(imageTapped)))

        return cell
    }

    func imageTapped(_ sender: UITapGestureRecognizer) {
        if let indexPath = imageCollection.indexPathForItem(at: sender.location(in: imageCollection)),
            let imagePreview = imageCollection.cellForItem(at: indexPath) as? ImagePreview {
            dismiss(animated: true, completion: nil)
            imageAttachmentCallback(imagePreview.imageLocalIdentifier!)
        }
    }
    
    func getNumberOfImagesInCollection() -> Int {
        let requestOptions = PHImageRequestOptions()
        requestOptions.isSynchronous = true
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        
        let fetchResult: PHFetchResult = PHAsset.fetchAssets(with: PHAssetMediaType.image, options: fetchOptions)
        return fetchResult.count - (filter?.count ?? 0)
    }
}

class ImagePreview: UICollectionViewCell {
    let image: UIImageView
    let selectedMark: UIImageView
    var imageLocalIdentifier: String?
    
    override init(frame: CGRect) {
        selectedMark = UIImageView(frame: CGRect(origin: CGPoint(x: frame.width - 35, y: 5) , size: CGSize(width: 25, height: 25)))
        selectedMark.image = UIImage(named: "attachment_checked")
        selectedMark.layer.zPosition = 5
        selectedMark.isHidden = true
        image = UIImageView(frame: CGRect(origin: CGPoint.zero, size: CGSize(width: frame.size.width - 5, height: frame.size.height)))
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
