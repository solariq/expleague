//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit

class OrderViewController: UIViewController, CLLocationManagerDelegate {
    @IBOutlet weak var orderDescription: UIView!
    @IBAction func fire(sender: AnyObject) {
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        if (controller.orderText.text.isEmpty) {
            controller.orderTextBackground.backgroundColor = controller.error_color
            return
        }
        if (!AppDelegate.instance.stream.isConnected()) {
            let alertView = UIAlertController(title: "Experts League", message: "Connecting to server.\n\n", preferredStyle: .Alert)
            let completion = {
                //  Add your progressbar after alert is shown (and measured)
                let progressController = AppDelegate.instance.connectionProgressView
                let rect = CGRectMake(0, 54.0, alertView.view.frame.width, 50)
                progressController.completion = {
                    self.fire(self)
                }
                progressController.view.frame = rect
                progressController.view.backgroundColor = alertView.view.backgroundColor
                alertView.view.addSubview(progressController.view)
                progressController.alert = alertView
                AppDelegate.instance.connect()
            }
            alertView.addAction(UIAlertAction(title: "Retry", style: .Default, handler: {(x: UIAlertAction) -> Void in
                AppDelegate.instance.disconnect()
                self.fire(self)
            }))
            alertView.addAction(UIAlertAction(title: "Cancel", style: .Cancel, handler: nil))
            presentViewController(alertView, animated: true, completion: completion)
            return
        }
        if (controller.isLocal.on && location == nil) {
            let alertView = UIAlertController(title: "Заказ", message: "На данный момент ваша геопозиция не найдена. Подождите несколько секунд, или отключите настройку \"рядом со мной\".", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            presentViewController(alertView, animated: true, completion: nil)
            return
        }
        
        AppDelegate.instance.activeProfile!.placeOrder(
                topic: controller.orderText.text,
                urgency: Urgency.find(controller.urgency.value).type,
                local: controller.isLocal.on,
                attachments: controller.attachments.ids,
                location: self.location,
                prof: controller.needExpert.on
        );
        controller.clear()
        controller.attachments.clear()

        AppDelegate.instance.tabs.selectedIndex = 1
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        self.locationManager.requestWhenInUseAuthorization()
        if CLLocationManager.locationServicesEnabled() {
            locationManager.delegate = self
            locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            locationManager.startUpdatingLocation()
        }
    }
    
    let locationManager = CLLocationManager()
    var location: CLLocationCoordinate2D?
    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        location = manager.location?.coordinate
    }
}
class OrderDescriptionViewController: UITableViewController {
    let error_color = UIColor(red: 1.0, green: 0.0, blue: 0.0, alpha: 0.1)
    let rowHeight = 44;
    @IBOutlet weak var isLocal: UISwitch!
    @IBOutlet weak var needExpert: UISwitch!
    @IBOutlet weak var urgency: UISlider!
    @IBOutlet weak var urgencyLabel: UILabel!
    @IBAction func urgencyChanged(sender: UISlider) {
        let type = Urgency.find(sender.value);
        urgencyLabel.text = type.caption
        sender.value = type.value
    }
    var picker: ImagePickerDelegate?

    @IBAction func attach(sender: UIButton) {
        let picker = UIImagePickerController()
        self.picker = ImagePickerDelegate(attachments: attachments, picker: picker)
        picker.delegate = self.picker
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary
        self.presentViewController(picker, animated: true, completion: nil)
    }

    @IBOutlet weak var orderText: UITextView!
    @IBOutlet weak var orderTextBackground: UIView!
    @IBOutlet weak var attachmentsView: UICollectionView!
    let attachments = AttachmentsViewDelegate()
    var orderTextBGColor: UIColor?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        orderTextBGColor = orderTextBackground.backgroundColor
        urgencyChanged(urgency)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
        attachmentsView.delegate = attachments
        attachmentsView.dataSource = attachments
        attachmentsView.userInteractionEnabled = true
        attachmentsView.allowsSelection = true
        attachmentsView.backgroundColor = UIColor.whiteColor()
        attachmentsView.backgroundView = UIView(frame: CGRectZero);
        attachmentsView.layoutMargins = UIEdgeInsetsZero
        attachments.view = attachmentsView
        attachments.parent = self
    }
    
    func dismissKeyboard() {
        orderTextBackground.backgroundColor = orderText.text.isEmpty ? error_color : orderTextBGColor
        
        view.endEditing(true)
    }
    
    internal func clear() {
        isLocal.on = false
        needExpert.on = false
        urgency.value = Urgency.DURING_THE_DAY.value
        orderText.text = ""
    }

    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        if (indexPath.item == 0 && indexPath.section == 0) {
            let sectionsHeight = 28 * 2 * 2;
            return max(CGFloat(50), view.frame.height - CGFloat(5 * rowHeight + sectionsHeight));
        }
        if (indexPath.item == 0 && indexPath.section == 1) {
            return 64
        }
        return CGFloat(rowHeight);
    }
}

class ImageAttachment: UICollectionViewCell {
    @IBOutlet weak var image: UIImageView!
    @IBOutlet weak var progress: UIProgressView!
}

class AttachmentsViewDelegate: NSObject, UICollectionViewDelegate, UICollectionViewDataSource {
    var view: UICollectionView?
    var cells: [UIImage] = []
    var progress: [(UIProgressView)->Void] = []
    var ids: [String] = []
    
    func append(id: String, image: UIImage, progress: (UIProgressView)->Void) {
        cells.append(image)
        ids.append(id)
        self.progress.append(progress)
        view?.reloadData()
    }

    func remove(index: Int) {
        cells.removeAtIndex(index)
        ids.removeAtIndex(index)
        let _ = progress.removeAtIndex(index)
        view?.reloadData()
    }
    
    func clear() {
        cells.removeAll()
        progress.removeAll()
        ids.removeAll()
        view?.reloadData()
    }
    
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : cells.count
    }
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("ImageAttachment", forIndexPath: indexPath) as! ImageAttachment
        progress[indexPath.item](cell.progress)
        cell.image.image = cells[indexPath.item]
        cell.addGestureRecognizer(UITapGestureRecognizer(trailingClosure: {
            let alertView = UIAlertController(title: "Приложения", message: "Открепить картинку от заказа?", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Да", style: .Default, handler: {(x: UIAlertAction) -> Void in
                self.remove(indexPath.item)
            }))
            alertView.addAction(UIAlertAction(title: "Нет", style: .Cancel, handler: nil))
            self.parent?.presentViewController(alertView, animated: true, completion: nil)
            return
        }))
        return cell
    }
    
    var parent: OrderDescriptionViewController?
}

class ImagePickerDelegate: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate {
    weak var progressView: UIProgressView?
    let attachments: AttachmentsViewDelegate
    let picker: UIImagePickerController
    
    var image: UIImage?
    var imageId: String?
    @objc
    func imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : AnyObject]) {
        imageId = NSUUID().UUIDString + ".jpeg";
        if let image = info[UIImagePickerControllerOriginalImage] as? UIImage {
            attachments.append(imageId!, image: image, progress: {
                self.progressView = $0
                AppDelegate.instance.activeProfile!.saveImage(self.imageId!, image: image)
                self.uploadImage()
            })
            self.image = image
        }
        picker.dismissViewControllerAnimated(true, completion: nil)
    }

    func uploadImage() {
        if (image == nil) {
            return
        }
        let imageData = UIImageJPEGRepresentation(image!, 1)
        if(imageData == nil) {
            return
        }

//        self.uploadButton.enabled = false

        let uploadScriptUrl = AppDelegate.instance.activeProfile!.imageStorage
        let request = NSMutableURLRequest(URL: uploadScriptUrl)
        
        let boundaryConstant = NSUUID().UUIDString
        let contentType = "multipart/form-data; boundary=" + boundaryConstant
        let boundaryStart = "--\(boundaryConstant)\r\n"
        let boundaryEnd = "--\(boundaryConstant)--\r\n"
        
        let requestBodyData : NSMutableData = NSMutableData()
        requestBodyData.appendData(boundaryStart.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Disposition: form-data; name=\"id\"\r\n\r\n\(imageId!)\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(boundaryStart.dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Disposition: form-data; name=\"image\"; filename=\"\(imageId!)\"\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData("Content-Type: image/jpeg\r\n\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(imageData!)
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        requestBodyData.appendData(boundaryEnd.dataUsingEncoding(NSUTF8StringEncoding)!)
        
        request.HTTPMethod = "POST"
        request.setValue("Keep-Alive", forHTTPHeaderField: "Connection")
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.HTTPBody = requestBodyData.copy() as? NSData

        let configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
        let session = NSURLSession(configuration: configuration, delegate: self, delegateQueue: NSOperationQueue.mainQueue())

        progressView?.progress = 0.0
        let task = session.uploadTaskWithStreamedRequest(request)
        task.resume()
    }

    func URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError error: NSError?) {
        let myAlert = UIAlertView(title: "Ошибка", message: error?.localizedDescription, delegate: nil, cancelButtonTitle: "Ok")
        myAlert.show()

//        self.uploadButton.enabled = true
    }
    
    func URLSession(session: NSURLSession, task: NSURLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        let uploadProgress:Float = Float(totalBytesSent) / Float(totalBytesExpectedToSend)
        progressView?.progress = uploadProgress
//        print("\(uploadProgress) \(totalBytesSent) of \(totalBytesExpectedToSend)")
    }
    
    func URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveResponse response: NSURLResponse, completionHandler: (NSURLSessionResponseDisposition) -> Void) {
        print("Loaded: " + imageId!)
        print(response);
//        self.uploadButton.enabled = true
    }
    
    func URLSession(session: NSURLSession, didReceiveChallenge challenge: NSURLAuthenticationChallenge, completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Void) {
        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            if (challenge.protectionSpace.host == "img." + AppDelegate.instance.activeProfile!.domain) {
                completionHandler(.UseCredential, NSURLCredential(forTrust: challenge.protectionSpace.serverTrust!))
            }
        }
    }

    init(attachments: AttachmentsViewDelegate, picker: UIImagePickerController) {
        self.attachments = attachments
        self.picker = picker
    }
}

struct Urgency {
    var caption: String;
    var value: Float;
    var type: String;

    static let ASAP = Urgency(caption: "срочно", value: 1.0, type: "asap")
    static let DURING_THE_DAY = Urgency(caption: "в течение дня", value: 0.5, type: "day")
    static let DURING_THE_WEEK = Urgency(caption: "в течение недели", value: 0.0, type: "week")

    static func find(value: Float) -> Urgency {
        if (value < 0.25) {
            return DURING_THE_WEEK
        }
        else if value < 0.75 {
            return DURING_THE_DAY
        }
        return ASAP
    }
}
