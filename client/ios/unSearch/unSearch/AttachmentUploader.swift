//
//  AttachmentUploader.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 25.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class AttachmentUploader: NSObject, NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate {
    var callback: AttachmentUploadCallback?
    var imageId: String
    var image: UIImage
    
    init(callback: AttachmentUploadCallback?) {
        self.callback = callback
    }
    
    func uploadImageFromPicker(info: [String : AnyObject]) {
        var imageId: String
        if let referenceUrl = info[UIImagePickerControllerReferenceURL] as? NSURL {
            imageId = "\(ExpLeagueProfile.active.jid.user)-\(referenceUrl.hash).jpeg";
        }
        else {
            imageId = "\(NSUUID().UUIDString).jpeg"
        }
        if let image = info[UIImagePickerControllerOriginalImage] as? UIImage {
            self.imageId = imageId
            self.image = image
            
            self.callback?.uploadCreated(imageId, attachment: image)
            self.uploadImage()
        }
    }
    
    func uploadImage() {
        let uploadScriptUrl = AppDelegate.instance.activeProfile!.imageStorage
        let request = NSMutableURLRequest(URL: uploadScriptUrl)

        let imageUrl = AppDelegate.instance.activeProfile!.imageUrl(imageId)
        let imageData = UIImageJPEGRepresentation(image, 1)
        EVURLCache.storeCachedResponse(
            NSCachedURLResponse(
                response: NSURLResponse(URL: imageUrl, MIMEType: "image/jpeg", expectedContentLength: imageData!.length, textEncodingName: "UTF-8"),
                data: imageData!
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
        requestBodyData.appendData(imageData!)
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(boundaryEnd.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        
        request.HTTPMethod = "POST"
        request.setValue("Keep-Alive", forHTTPHeaderField: "Connection")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.HTTPBody = requestBodyData.copy() as? NSData
        request.timeoutInterval = 10 * 60
        request.cachePolicy = .ReloadIgnoringLocalCacheData
        let configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
        let session = NSURLSession(configuration: configuration, delegate: self, delegateQueue: NSOperationQueue.mainQueue())
        
        self.callback?.uploadStarted(imageId, attachment: image)
        let task = session.uploadTaskWithStreamedRequest(request)
        task.resume()
    }
    
    func URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError error: NSError?) {
        let myAlert = UIAlertView(title: "Ошибка", message: error?.localizedDescription, delegate: nil, cancelButtonTitle: "Ok")
        myAlert.show()
    }
    
    func URLSession(session: NSURLSession, task: NSURLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        let uploadProgress:Float = Float(totalBytesSent) / Float(totalBytesExpectedToSend)
        self.callback?.uploadInProgress(self.imageId, progressValue: uploadProgress)
    }
    
    func URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveResponse response: NSURLResponse, completionHandler: (NSURLSessionResponseDisposition) -> Void) {
        if let httpResp = response as? NSHTTPURLResponse {
            if httpResp.statusCode != 200 {
                self.callback?.uploadFailed(self.imageId, httpResponse: httpResp)
            }
            else {
                self.callback?.uploadCompleted(self.imageId)
            }
        }
    }
    
    func URLSession(session: NSURLSession, didReceiveChallenge challenge: NSURLAuthenticationChallenge, completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Void) {
        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            if (challenge.protectionSpace.host == "img." + AppDelegate.instance.activeProfile!.domain) {
                completionHandler(.UseCredential, NSURLCredential(forTrust: challenge.protectionSpace.serverTrust!))
            }
        }
    }
}

protocol AttachmentUploadCallback {
    func uploadCreated(attachmentId: String, attachment: Any)
    func uploadStarted(attachmentId: String, attachment: Any)
    func uploadInProgress(attachmentId: String, progressValue: Float)
    func uploadCompleted(attachmentId: String)
    func uploadFailed(attachmentId: String, httpResponse: NSHTTPURLResponse)
}