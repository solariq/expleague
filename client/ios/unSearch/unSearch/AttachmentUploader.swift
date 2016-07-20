//
//  AttachmentUploader.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 25.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import Photos

class AttachmentsUploader: NSObject, NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate {
    var session: NSURLSession!
    var inProgress: [OrderAttachment] = []
    
    override init() {
        super.init()
        let configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
        session = NSURLSession(configuration: configuration, delegate: self, delegateQueue: NSOperationQueue.mainQueue())
    }
    
    func upload(attachment: OrderAttachment) {
        let imageLocalId = attachment.imageId
        let imageId = attachment.globalId

        let imgManager = PHImageManager.defaultManager()
        let fetchOptions = PHFetchOptions()
        let fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers([imageLocalId], options: fetchOptions)
        guard let asset = fetchResult.objectAtIndex(0) as? PHAsset else {
            attachment.error = "Не удалось найти фотографию в библиотеке"
            return
        }
        
        let initialRequestOptions = PHImageRequestOptions()
        initialRequestOptions.synchronous = true
        initialRequestOptions.resizeMode = .Fast
        initialRequestOptions.deliveryMode = .FastFormat
        var img: UIImage?
        imgManager.requestImageForAsset(asset, targetSize: CGSizeMake(1600, 1200), contentMode: .AspectFit, options: initialRequestOptions) { (data, _) in
            img = data
        }
        
        guard let image = img else {
            attachment.error = "Не удалось уменьшить фотографию"
            return
        }
        
        let uploadScriptUrl = AppDelegate.instance.activeProfile!.imageStorage
        let imageUrl = AppDelegate.instance.activeProfile!.imageUrl(imageId)
        
        
        guard let imageData = UIImageJPEGRepresentation(image, 1) else {
            attachment.error = "Невозможно получить данные изображения"
            return
        }
        
        EVURLCache.storeCachedResponse(
            NSCachedURLResponse(
                response: NSURLResponse(URL: imageUrl, MIMEType: "image/jpeg", expectedContentLength: imageData.length, textEncodingName: "UTF-8"),
                data: imageData
            ),
            forRequest: NSURLRequest(URL: imageUrl)
        )

        let boundaryConstant = NSUUID().UUIDString
        let contentType = "multipart/form-data; boundary=" + boundaryConstant
        let boundaryStart = "--\(boundaryConstant)\r\n"
        let boundaryEnd = "--\(boundaryConstant)--\r\n"
        
        let requestBodyData : NSMutableData = NSMutableData()
        requestBodyData.appendData(boundaryStart.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Disposition: form-data; name=\"id\"\r\n\r\n\(imageId)\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(boundaryStart.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Disposition: form-data; name=\"image\"; filename=\"\(imageId)\"\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Type: image/jpeg\r\n\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(imageData)
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(boundaryEnd.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        
        let request = NSMutableURLRequest(URL: uploadScriptUrl)
        request.HTTPMethod = "POST"
        request.setValue("Keep-Alive", forHTTPHeaderField: "Connection")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.HTTPBody = requestBodyData.copy() as? NSData
        request.timeoutInterval = 10 * 60
        request.cachePolicy = .ReloadIgnoringLocalCacheData
        
        attachment.progress = 0.0
        let task = session.uploadTaskWithStreamedRequest(request)
        task.taskDescription = attachment.imageId
        inProgress.append(attachment)
        task.resume()
    }
    

    private func progress(task: NSURLSessionTask) -> OrderAttachment? {
        return inProgress.filter({$0.imageId == task.taskDescription}).first
    }

    private func finish(task: NSURLSessionTask) -> OrderAttachment? {
        guard let attachment = progress(task) else{
            return nil
        }
        inProgress.removeOne(attachment)
        return attachment
    }
    
    func URLSession(session: NSURLSession, task: NSURLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        let uploadProgress = Float(totalBytesSent) / Float(totalBytesExpectedToSend)
        progress(task)?.progress = uploadProgress
    }
    
    func URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError error: NSError?) {
        if (error != nil) {
            finish(task)?.error = "Не удалось загрузить приложение: \(error!.localizedDescription)"
        }
    }

    func URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveResponse response: NSURLResponse, completionHandler: (NSURLSessionResponseDisposition) -> Void) {
        if let attachment = self.finish(dataTask), let httpResp = response as? NSHTTPURLResponse {
            if httpResp.statusCode != 200 {
                attachment.error = "Не удалось загрузить приложение: \(response)"
            }
            else {
                attachment.uploaded = true
            }
        }
        completionHandler(NSURLSessionResponseDisposition.Allow)
    }
    
    func URLSession(session: NSURLSession, didReceiveChallenge challenge: NSURLAuthenticationChallenge, completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Void) {
        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            if (challenge.protectionSpace.host == "img." + AppDelegate.instance.activeProfile!.domain) {
                completionHandler(.UseCredential, NSURLCredential(forTrust: challenge.protectionSpace.serverTrust!))
            }
        }
    }
}
