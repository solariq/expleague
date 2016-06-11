//
//  ImageAttachmentPreview.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 11.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import Photos

class ImageCollectionPreviewDelegate: NSObject, UICollectionViewDelegateFlowLayout, UICollectionViewDataSource {
    var view: UICollectionView?
    var cells: [UIImage] = []
    
    var totalImageCountNeeded = 3
    var count: Int {
        return cells.count
    }
    
    func append(image: UIImage) {
        cells.append(image)
        view?.reloadData()
        view?.performBatchUpdates(nil, completion: nil)
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
        cell.backgroundColor = UIColor.blueColor()
        cell.addGestureRecognizer(UITapGestureRecognizer(trailingClosure: {
            return
        }))
        return cell
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
                imgManager.requestImageForAsset(fetchResult.objectAtIndex(fetchResult.count - 1 - index) as! PHAsset, targetSize: view!.frame.size, contentMode: PHImageContentMode.AspectFill, options: requestOptions, resultHandler: { (image, _) in
                    
                    // Add the returned image to your array
                    self.append(image!)
                    
                    // If you haven't already reached the first
                    // index of the fetch result and if you haven't
                    // already stored all of the images you need,
                    // perform the fetch request again with an
                    // incremented index
                    if index + 1 < fetchResult.count && self.count < self.totalImageCountNeeded {
                        self.fetchPhotoAtIndexFromEnd(index + 1)
                    } else {
                        // Else you have completed creating your array
                        print("Completed array of \(self.count) items")
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
        self.image = UIImageView(frame: CGRectMake(0, 0, 80, 80))
        self.image.contentMode = .ScaleAspectFill
        self.image.clipsToBounds = true
        self.contentView.addSubview(self.image)
        //self.viewForBaselineLayout().addSubview(self.image)
        print("Image preview is created")
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}


