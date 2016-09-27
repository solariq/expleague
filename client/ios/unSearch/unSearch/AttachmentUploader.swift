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

class AttachmentsUploader: NSObject, URLSessionDelegate, URLSessionTaskDelegate, URLSessionDataDelegate {
    var session: Foundation.URLSession!
    var inProgress: [OrderAttachment] = []
    
    override init() {
        super.init()
        let configuration = URLSessionConfiguration.default
        session = Foundation.URLSession(configuration: configuration, delegate: self, delegateQueue: OperationQueue.main)
    }
    
    func upload(_ attachment: OrderAttachment) {
        let imageLocalId = attachment.imageId
        let imageId = attachment.globalId

        let imgManager = PHImageManager.default()
        let fetchOptions = PHFetchOptions()
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [imageLocalId], options: fetchOptions)
        guard fetchResult.count > 0 else {
            attachment.error = "Не удалось найти фотографию в библиотеке"
            return
        }
        let asset = fetchResult.object(at: 0)
        let initialRequestOptions = PHImageRequestOptions()
        initialRequestOptions.isSynchronous = true
        initialRequestOptions.resizeMode = .fast
        initialRequestOptions.deliveryMode = .fastFormat
        var img: UIImage?
        imgManager.requestImage(for: asset, targetSize: CGSize(width: 1600, height: 1200), contentMode: .aspectFit, options: initialRequestOptions) { (data, _) in
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
            CachedURLResponse(
                response: URLResponse(url: imageUrl, mimeType: "image/jpeg", expectedContentLength: imageData.count, textEncodingName: "UTF-8"),
                data: imageData
            ),
            forRequest: URLRequest(url: imageUrl)
        )

        let boundaryConstant = UUID().uuidString
        let contentType = "multipart/form-data; boundary=" + boundaryConstant
        let boundaryStart = "--\(boundaryConstant)\r\n"
        let boundaryEnd = "--\(boundaryConstant)--\r\n"
        
        let requestBodyData : NSMutableData = NSMutableData()
        requestBodyData.append(boundaryStart.data(using: String.Encoding.utf8)!)
        requestBodyData.append("Content-Disposition: form-data; name=\"id\"\r\n\r\n\(imageId)\r\n".data(using: String.Encoding.utf8)!)
        requestBodyData.append(boundaryStart.data(using: String.Encoding.utf8)!)
        requestBodyData.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(imageId)\"\r\n".data(using: String.Encoding.utf8)!)
        requestBodyData.append("Content-Type: image/jpeg\r\n\r\n".data(using: String.Encoding.utf8)!)
        requestBodyData.append(imageData)
        requestBodyData.append("\r\n".data(using: String.Encoding.utf8)!)
        requestBodyData.append(boundaryEnd.data(using: String.Encoding.utf8)!)
        requestBodyData.append("\r\n".data(using: String.Encoding.utf8)!)
        
        let request = NSMutableURLRequest(url: uploadScriptUrl as URL)
        request.httpMethod = "POST"
        request.setValue("Keep-Alive", forHTTPHeaderField: "Connection")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.httpBody = requestBodyData.copy() as? Data
        request.timeoutInterval = 10 * 60
        request.cachePolicy = .reloadIgnoringLocalCacheData
        
        attachment.progress = 0.0
        let task = session.uploadTask(withStreamedRequest: request as URLRequest)
        task.taskDescription = attachment.imageId
        inProgress.append(attachment)
        task.resume()
    }
    

    fileprivate func progress(_ task: URLSessionTask) -> OrderAttachment? {
        return inProgress.filter({$0.imageId == task.taskDescription}).first
    }

    fileprivate func finish(_ task: URLSessionTask) -> OrderAttachment? {
        guard let attachment = progress(task) else{
            return nil
        }
        _ = inProgress.removeOne(attachment)
        return attachment
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        let uploadProgress = Float(totalBytesSent) / Float(totalBytesExpectedToSend)
        progress(task)?.progress = uploadProgress
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if (error != nil) {
            finish(task)?.error = "Не удалось загрузить приложение: \(error!.localizedDescription)"
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        if let attachment = self.finish(dataTask), let httpResp = response as? HTTPURLResponse {
            if httpResp.statusCode != 200 {
                attachment.error = "Не удалось загрузить приложение: \(response)"
            }
            else {
                attachment.uploaded = true
            }
        }
        completionHandler(Foundation.URLSession.ResponseDisposition.allow)
    }
    
    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            if (challenge.protectionSpace.host == "img." + AppDelegate.instance.activeProfile!.domain) {
                completionHandler(.useCredential, URLCredential(trust: challenge.protectionSpace.serverTrust!))
            }
        }
    }
}
