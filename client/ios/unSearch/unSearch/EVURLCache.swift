//
//  EVURLCache.swift
//  EVURLCache
//
//  Created by Edwin Vermeer on 11/7/15.
//  Copyright © 2015 evict. All rights reserved.
//

import Foundation
import ReachabilitySwift

#if os(iOS)
    import MobileCoreServices
fileprivate func < <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l < r
  case (nil, _?):
    return true
  default:
    return false
  }
}

fileprivate func > <T : Comparable>(lhs: T?, rhs: T?) -> Bool {
  switch (lhs, rhs) {
  case let (l?, r?):
    return l > r
  default:
    return rhs < lhs
  }
}

#elseif os(OSX)
    import CoreServices
#endif

open class EVURLCache : URLCache {
    open static var URLCACHE_CACHE_KEY = "MobileAppCacheKey" // Add this header variable to the response if you want to save the response using this key as the filename.
    open static var URLCACHE_EXPIRATION_AGE_KEY = "MobileAppExpirationAgeKey" // Add this header variable to the response to set the expiration age.
    open static var MAX_AGE = "604800000" // The default maximum age of a cached file in miliseconds. (1 weeks)
    open static var PRE_CACHE_FOLDER = "PreCache"  // The folder in your app with the prefilled cache content
    open static var CACHE_FOLDER = "Cache" // The folder in the Documents folder where cached files will be saved
    open static var MAX_FILE_SIZE = 24 // The maximum file size that will be cached (2^24 = 16MB)
    open static var MAX_CACHE_SIZE = 31 // The maximum file size that will be cached (2^30 = 256MB)
    open static var LOGGING = true // Set this to true to see all caching action in the output log
    open static var FORCE_LOWERCASE = true // Set this to false if you want to use case insensitive filename compare
    open static var _cacheDirectory: String!
    open static var _preCacheDirectory: String!
    open static var RECREATE_CACHE_RESPONSE = true // There is a difrence between unarchiving and recreating. I have to find out what.
    
    // Activate EVURLCache
    open class func activate() {
        // set caching paths
        _cacheDirectory = URL(fileURLWithPath: NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.documentDirectory, FileManager.SearchPathDomainMask.userDomainMask, true)[0]).appendingPathComponent(CACHE_FOLDER).absoluteString
        _preCacheDirectory = URL(fileURLWithPath: Bundle.main.resourcePath!).appendingPathComponent(PRE_CACHE_FOLDER).absoluteString
        
        let urlCache = EVURLCache(memoryCapacity: 1<<MAX_FILE_SIZE, diskCapacity: 1<<MAX_CACHE_SIZE, diskPath: _cacheDirectory)
        
        URLCache.shared = urlCache
    }
    
    // Log a message with info if enabled
    open static func debugLog<T>(_ object: T, filename: String = #file, line: Int = #line, funcname: String = #function) {
        if LOGGING {
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "MM/dd/yyyy HH:mm:ss:SSS"
            let process = ProcessInfo.processInfo
            let threadId = "." //NSThread.currentThread().threadDictionary
            NSLog("\(dateFormatter.string(from: Date())) \(process.processName))[\(process.processIdentifier):\(threadId)] \((filename as NSString).lastPathComponent)(\(line)) \(funcname):\r\t\(object)\n")
        }
    }
    
    // Will be called by a NSURLConnection when it's wants to know if there is something in the cache.
    open override func cachedResponse(for req: URLRequest) -> CachedURLResponse? {
        guard req.url != nil else {
            EVURLCache.debugLog("CACHE not allowed for nil URLs");
            return nil
        }

        let request = URLRequest(url: URL(string: (req.url!.absoluteString.replacingOccurrences(of: "&amp;", with: "&")))!, cachePolicy: req.cachePolicy, timeoutInterval: req.timeoutInterval)
        let url = request.url!
        if url.absoluteString.isEmpty {
            EVURLCache.debugLog("CACHE not allowed for empty URLs");
            return nil;
        }
        
        // is caching allowed
        if ((request.cachePolicy == .reloadIgnoringCacheData || url.absoluteString.hasPrefix("file:/") || url.absoluteString.hasPrefix("data:")) && EVURLCache.networkAvailable()) {
            EVURLCache.debugLog("CACHE not allowed for \(url)");
            return nil;
        }
        
        // Is the file in the cache? If not, is the file in the PreCache?
        var storagePath: String = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._cacheDirectory)
        if !FileManager.default.fileExists(atPath: storagePath) {
            storagePath  = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._preCacheDirectory)
            if !FileManager.default.fileExists(atPath: storagePath) {
                EVURLCache.debugLog("CACHE not found \(storagePath)")
                return nil;
            }
        }
        
        // Check file status only if we have network, otherwise return it anyway.
        if EVURLCache.networkAvailable() {
            // Max cache age for request
            let maxAge:String = request.value(forHTTPHeaderField: EVURLCache.URLCACHE_EXPIRATION_AGE_KEY) ?? EVURLCache.MAX_AGE
            
            do {
                let attributes = try FileManager.default.attributesOfItem(atPath: storagePath)
                if let modDate:Date = attributes[FileAttributeKey.modificationDate] as? Date {
                    // Test if the file is older than the max age
                    if let threshold: TimeInterval = Double(maxAge) {
                        let modificationTimeSinceNow:TimeInterval? = -modDate.timeIntervalSinceNow
                        if modificationTimeSinceNow > threshold {
                            EVURLCache.debugLog("CACHE item older than \(maxAge) maxAgeHours");
                            return nil
                        }
                    }
                }
            } catch {}
        }
        
        // Read object from file
        if let response = NSKeyedUnarchiver.unarchiveObject(withFile: storagePath + ".answer") as? URLResponse {
            EVURLCache.debugLog("Returning cached data from \(storagePath)");
            
            let data = try! Data(contentsOf: URL(fileURLWithPath: storagePath))
            let userInfo: [AnyHashable : Any]? = FileManager.default.fileExists(atPath: storagePath + ".user") ?
                NSKeyedUnarchiver.unarchiveObject(withFile: storagePath + ".user") as? [AnyHashable : Any] :
                nil
            let r = URLResponse(url: response.url!, mimeType: response.mimeType, expectedContentLength: data.count, textEncodingName: response.textEncodingName)
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 301, let location = httpResponse.allHeaderFields["Location"] as? String {
                    return cachedResponse(for: URLRequest(url: URL(string: location)!, cachePolicy: req.cachePolicy, timeoutInterval: req.timeoutInterval))
                }
                else if (httpResponse.statusCode != 200) {
                    return nil
                }
                
            }
            return CachedURLResponse(response: r, data: data, userInfo: userInfo, storagePolicy: .allowed)
        } else {
            EVURLCache.debugLog("The file is probably not put in the local path using NSKeyedArchiver \(storagePath)");
        }
        return nil
    }
    
    open override func storeCachedResponse(_ cachedResponse: CachedURLResponse, for request: URLRequest) {
        EVURLCache.storeCachedResponse(cachedResponse, forRequest: request)
    }
    
    // Will be called by NSURLConnection when a request is complete.
    open static func storeCachedResponse(_ cachedResponse: CachedURLResponse, forRequest req: URLRequest) {
        guard req.url != nil else {
            EVURLCache.debugLog("CACHE not allowed for nil URLs");
            return
        }
        
        let request = URLRequest(url: URL(string: (req.url!.absoluteString.replacingOccurrences(of: "&amp;", with: "&")))!, cachePolicy: req.cachePolicy, timeoutInterval: req.timeoutInterval)

        if let httpResponse = cachedResponse.response as? HTTPURLResponse {
            if httpResponse.statusCode >= 400 {
                EVURLCache.debugLog("CACHE Do not cache error \(httpResponse.statusCode) page for : \(request.url) \(httpResponse.debugDescription)");
                return
            }
        }
        
        // check if caching is allowed
        if request.cachePolicy == NSURLRequest.CachePolicy.reloadIgnoringCacheData {
            // If the file is in the PreCache folder, then we do want to save a copy in case we are without internet connection
            let storagePath: String = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._preCacheDirectory)
            if !FileManager.default.fileExists(atPath: storagePath) {
                EVURLCache.debugLog("CACHE not storing file, it's not allowed by the cachePolicy : \(request.url)")
                return
            }
            EVURLCache.debugLog("CACHE file in PreCache folder, overriding cachePolicy : \(request.url)");
        }
        
        // create storrage folder
        let storagePath: String = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._cacheDirectory)
        if var storageDirectory: String = URL(fileURLWithPath: "\(storagePath)").deletingLastPathComponent().absoluteString.removingPercentEncoding {
            do {
                if storageDirectory.hasPrefix("file:") {
                    storageDirectory = storageDirectory.substring(from: storageDirectory.characters.index(storageDirectory.startIndex, offsetBy: 5))
                }
                try FileManager.default.createDirectory(atPath: storageDirectory, withIntermediateDirectories: true, attributes: nil)
            } catch let error as NSError {
                EVURLCache.debugLog("Error creating cache directory \(storageDirectory)");
                EVURLCache.debugLog("Error \(error.debugDescription)");
            }
        }
        
        // save file
        EVURLCache.debugLog("Writing data to \(storagePath)");
        do {
            try cachedResponse.data.write(to: URL(fileURLWithPath: storagePath))
            if !NSKeyedArchiver.archiveRootObject(cachedResponse.response, toFile: storagePath + ".answer") {
                throw NSError()
            }
            if (cachedResponse.userInfo != nil && !NSKeyedArchiver.archiveRootObject(cachedResponse.userInfo!, toFile: storagePath + ".user")) {
                throw NSError()
            }
        }
        catch let error as NSError {
            EVURLCache.debugLog("Error \(error.debugDescription)");
            // prevent iCloud backup
            if !EVURLCache.addSkipBackupAttributeToItemAtURL(URL(fileURLWithPath: storagePath)) {
                EVURLCache.debugLog("Write cached result");
            }
        }
    }
    
    
    open static func storagePath(url: URL) -> String? {
        return storagePathForRequest(URLRequest(url: url, cachePolicy: .returnCacheDataDontLoad, timeoutInterval: 10))
    }
    
    // return the path if the file for the request is in the PreCache or Cache.
    open static func storagePathForRequest(_ request: URLRequest) -> String? {
        var storagePath = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._cacheDirectory)
        if !FileManager.default.fileExists(atPath: storagePath) {
            storagePath = EVURLCache.storagePathForRequest(request, rootPath: EVURLCache._preCacheDirectory)
        }
        return FileManager.default.fileExists(atPath: storagePath) ? storagePath : nil;
    }
    
    static func md5(string: String) -> String {
        var digest = [UInt8](repeating: 0, count: Int(CC_MD5_DIGEST_LENGTH))
        if let data = string.data(using: String.Encoding.utf8) {
            CC_MD5((data as NSData).bytes, CC_LONG(data.count), &digest)
        }
        
        var digestHex = ""
        for index in 0..<Int(CC_MD5_DIGEST_LENGTH) {
            digestHex += String(format: "%02x", digest[index])
        }
        
        return digestHex
    }
    
    // build up the complete storrage path for a request plus root folder.
    open static func storagePathForRequest(_ request: URLRequest, rootPath: String) -> String {
        var localUrl: String!
        let host: String = request.url!.host ?? "default"
        
        // The filename could be forced by the remote server. This could be used to force multiple url's to the same cache file
        localUrl = "\(host)/\(md5(string: request.url?.absoluteString ?? ""))"
        // Without an extension it's treated as a folder and the file will be called index.html
        if let storageFile: String = request.url?.absoluteString.components(separatedBy: "/").last?.components(separatedBy: "?").first, storageFile.contains(".")  {
            if let fileExtension = storageFile.components(separatedBy: ".").last {
                localUrl = "\(localUrl!).\(fileExtension)"
            }
        }
        
        localUrl = "\(rootPath)/\(localUrl!)"
        
        // Cleanup
        if localUrl.hasPrefix("file:") {
            localUrl = localUrl.substring(from: localUrl.index(localUrl.startIndex, offsetBy: 5))
        }
        localUrl = localUrl.replacingOccurrences(of: "//", with: "/")
        localUrl = localUrl.replacingOccurrences(of: "//", with: "/")
        // print("storing \(request.url!) as  \(localUrl!)")
        return localUrl!
    }
    
    open static func addSkipBackupAttributeToItemAtURL(_ url: URL) -> Bool {
        do {
            try (url as NSURL).setResourceValue(NSNumber(value: true as Bool), forKey: URLResourceKey.isExcludedFromBackupKey)
            return true
        } catch _ as NSError {
            debugLog("ERROR: Could not set 'exclude from backup' attribute for file \(url.absoluteString)")
        }
        return false
    }
    
    fileprivate static let reachibility = Reachability()
    // Check if we have a network connection
    fileprivate static func networkAvailable() -> Bool {
        return reachibility?.isReachable ?? true
    }
}
