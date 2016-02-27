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
        if (!controller.orderTextDelegate!.validate()) {
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
        if (!controller.attachments.complete()) {
            let alertView = UIAlertController(title: "Заказ", message: "На данный момент не все прикрепленные объекты сохранены. Подождите несколько секунд.", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            presentViewController(alertView, animated: true, completion: nil)
            return
        }
        
        AppDelegate.instance.activeProfile!.placeOrder(
                topic: controller.orderText.text,
            urgency: (controller.urgency.on ? Urgency.ASAP : Urgency.DURING_THE_DAY).type,
                local: controller.isLocal.on,
                attachments: controller.attachments.ids,
                location: self.location,
                prof: true
        );
        controller.clear()
        controller.attachments.clear()

        AppDelegate.instance.tabs.tabBar.hidden = true
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
    let rowHeight = 62;
    @IBOutlet weak var lupa: UIImageView!

    @IBOutlet weak var isLocal: UISwitch!
    @IBOutlet weak var urgency: UISwitch!
    @IBOutlet weak var urgencyLabel: UILabel!
    @IBOutlet weak var owlIcon: UIImageView!
    @IBOutlet weak var owlHeight: NSLayoutConstraint!
    @IBOutlet weak var owlWidth: NSLayoutConstraint!
    @IBOutlet weak var unSearchLabel: UILabel!

    @IBOutlet weak var orderTextHeight: NSLayoutConstraint!
    @IBOutlet weak var orderText: UITextView!
    @IBOutlet weak var orderTextBackground: UIView!
    @IBOutlet weak var attachmentsView: UICollectionView!
    let attachments = AttachmentsViewDelegate()
    var orderTextBGColor: UIColor?
    let picker = UIImagePickerController()
    var pickerDelegate: ImagePickerDelegate?
    var orderTextDelegate: OrderTextDelegate?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        orderTextBGColor = orderTextBackground.backgroundColor

        (view as! UITableView).allowsSelection = true
        (view as! UITableView).delegate = self
        attachmentsView.delegate = attachments
        attachmentsView.dataSource = attachments
        attachmentsView.userInteractionEnabled = true
        attachmentsView.allowsSelection = true
        attachmentsView.backgroundColor = UIColor.whiteColor()
        attachmentsView.backgroundView = UIView(frame: CGRectZero);
        attachmentsView.layoutMargins = UIEdgeInsetsZero
        attachments.view = attachmentsView
        attachments.parent = self
        pickerDelegate = ImagePickerDelegate(queue: attachments, picker: picker)
        picker.delegate = pickerDelegate
        picker.sourceType = UIImagePickerControllerSourceType.PhotoLibrary
        
        orderTextDelegate = OrderTextDelegate(height: orderTextHeight, parent: self)
        orderText.delegate = orderTextDelegate
    }
    
    override func viewDidAppear(animated: Bool) {
        adjustSizes(view.frame.height)
    }
    
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animateAlongsideTransition({ (UIViewControllerTransitionCoordinatorContext) -> Void in
            self.adjustSizes(size.height - self.view.window!.frame.height + self.view.frame.height)
        }, completion: nil)
    }
    
    @IBOutlet weak var unSearchY: NSLayoutConstraint!
    @IBOutlet weak var owlY: NSLayoutConstraint!
    
    internal func adjustSizes(height: CGFloat) {
        print("\(height), \(sizeOfInput(height))")
        let inputHeight = sizeOfInput(height)
        if (inputHeight > 130) {
            unSearchLabel.hidden = false
            owlIcon.hidden = false
            owlHeight.constant = max((inputHeight - 71.0) / 2.0, 50.0)
            owlWidth.constant = owlHeight.constant * 160.0/168.0
            owlY.constant = (inputHeight - owlHeight.constant)/2.0 - 8
        }
        else if (inputHeight > 100) {
            unSearchLabel.hidden = false
            owlIcon.hidden = true
            unSearchY.constant = (inputHeight)/2.0 - 8 - unSearchLabel.frame.height
        }
        else {
            owlIcon.hidden = true
            unSearchLabel.hidden = true
        }
        orderTextDelegate!.total = inputHeight
    }
    
    internal func clear() {
        isLocal.on = false
        urgency.on = false
        orderTextDelegate!.clear(orderText)
        attachments.clear()
    }

    @IBOutlet weak var urgencyType: UILabel!
    @IBAction func onUnrgency(sender: AnyObject) {
        urgencyType.text = (urgency.on ? Urgency.ASAP : Urgency.DURING_THE_DAY).caption
    }
    @IBOutlet weak var imagesCaption: UILabel!

    internal func sizeOfInput(height: CGFloat) -> CGFloat {
        return max(CGFloat(50), height - CGFloat(5 * rowHeight));
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        if (indexPath.item == 0 && indexPath.section == 0) {
            return sizeOfInput(view.frame.height)
        }
        return CGFloat(rowHeight);
    }
    
    override func tableView(tableView: UITableView, shouldHighlightRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return 4...5 ~= indexPath.item
    }
    
    override func tableView(tableView: UITableView, willSelectRowAtIndexPath indexPath: NSIndexPath) -> NSIndexPath? {
        return 4...5 ~= indexPath.item ? indexPath : nil
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        if (indexPath.item == 4) {
            self.presentViewController(picker, animated: true, completion: nil)
        }
    }
}

class ImageAttachment: UICollectionViewCell {
    @IBOutlet weak var image: UIImageView!
    @IBOutlet weak var progress: UIProgressView!
}

class AttachmentsViewDelegate: NSObject, UICollectionViewDelegate, UICollectionViewDataSource, ImageSenderQueue {
    var view: UICollectionView?
    var cells: [UIImage] = []
    var progress: [(UIProgressView)->Void] = []
    var ids: [String] = []
    var status: [Bool] = []
    
    func append(id: String, image: UIImage, progress: (UIProgressView)->Void) {
        status.append(false)
        cells.append(image)
        ids.append(id)
        self.progress.append(progress)
        view?.reloadData()
    }

    func remove(index: Int) {
        status.removeAtIndex(index)
        cells.removeAtIndex(index)
        ids.removeAtIndex(index)
        let _ = progress.removeAtIndex(index)
        view?.reloadData()
    }
    
    func clear() {
        cells.removeAll()
        progress.removeAll()
        ids.removeAll()
        status.removeAll()
        view?.reloadData()
    }
    
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : cells.count
    }
    
    func complete() -> Bool {
        for s in status {
            if (!s) {
                return false
            }
        }
        return true
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
    
    func report(id: String, status: Bool) {
        let index = ids.indexOf(id)
        if (index != nil) {
            self.status[index!] = status
        }
        if (cells.count > 4) {
            parent!.imagesCaption.text = "\(cells.count) приложений"
        }
        else if (cells.count > 1) {
            parent!.imagesCaption.text = "\(cells.count) приложения"
        }
        else if (cells.count > 0) {
            parent!.imagesCaption.text = "\(cells.count) приложениe"
        }
        else {
            parent!.imagesCaption.text = "Не выбрано"
        }
    }
    
    var parent: OrderDescriptionViewController?
}

protocol ImageSenderQueue {
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void)
    func report(id: String, status: Bool);
}

class ImagePickerDelegate: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, NSURLSessionDelegate, NSURLSessionTaskDelegate, NSURLSessionDataDelegate {
    weak var progressView: UIProgressView?
    let queue: ImageSenderQueue
    let picker: UIImagePickerController
    
    var image: UIImage?
    var imageId: String?
    var imageData: NSData?
    var imageUrl: NSURL?
    
    @objc
    func imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [String : AnyObject]) {
        if let referenceUrl = info[UIImagePickerControllerReferenceURL] as? NSURL {
            imageId = "\(ExpLeagueProfile.active.jid.user)-\(referenceUrl.hash).jpeg";
        }
        else {
            imageId = "\(NSUUID().UUIDString).jpeg"
        }
        if let image = info[UIImagePickerControllerOriginalImage] as? UIImage {
            self.image = image
            imageData = UIImageJPEGRepresentation(image, 1)
            self.imageUrl = AppDelegate.instance.activeProfile!.imageUrl(imageId!)
            EVURLCache.storeCachedResponse(
                NSCachedURLResponse(
                    response: NSURLResponse(URL: imageUrl!, MIMEType: "image/jpeg", expectedContentLength: imageData!.length, textEncodingName: "UTF-8"),
                    data: self.imageData!
                ),
                forRequest: NSURLRequest(URL: imageUrl!)
            )
            
            queue.append(imageId!, image: image, progress: {
                self.progressView = $0
                self.uploadImage()
            })
            self.image = image
        }
        picker.dismissViewControllerAnimated(true, completion: nil)
    }

    func uploadImage() {
        if (image == nil || imageData == nil) {
            return
        }

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
        request.timeoutInterval = 10 * 60
        request.cachePolicy = .ReloadIgnoringLocalCacheData

        let configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
        let session = NSURLSession(configuration: configuration, delegate: self, delegateQueue: NSOperationQueue.mainQueue())

        progressView?.progress = 0.0
        progressView?.progressTintColor = UIColor.blueColor()
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
        if let httpResp = response as? NSHTTPURLResponse {
            if httpResp.statusCode != 200 {
                progressView?.progressTintColor = UIColor.redColor()
            }
            else {
                progressView?.progressTintColor = UIColor.greenColor()
                queue.report(imageId!, status: true)
            }
        }
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

    init(queue: ImageSenderQueue, picker: UIImagePickerController) {
        self.queue = queue
        self.picker = picker
    }
}

class OrderTextDelegate: NSObject, UITextViewDelegate {
    static let placeholder = "Найдем для Вас что угодно!"
    static let error_placeholder = "Введите текст запроса"
    var active: Bool = false
    var tapDetector: UIGestureRecognizer?
    func textViewShouldBeginEditing(textView: UITextView) -> Bool {
        UIView.animateWithDuration(0.3) { () -> Void in
            if (textView.text == OrderTextDelegate.placeholder || textView.text == OrderTextDelegate.error_placeholder) {
                textView.text = ""
                textView.textAlignment = .Left
                self.parent.lupa.hidden = true
            }
            textView.textColor = UIColor.blackColor()
            self.height.constant = self.total - 16.0 - 30.0
            self.parent.view!.layoutIfNeeded()
        }
        if (tapDetector == nil) {
            tapDetector = UITapGestureRecognizer(trailingClosure: {
                textView.endEditing(true)
            })
            parent.view.addGestureRecognizer(tapDetector!)
        }
        else {
            tapDetector!.enabled = true
        }
        active = true
        return true
    }
    
    func textViewDidEndEditing(textView: UITextView) {
        textView.resignFirstResponder()
        if (textView.text == "") {
            UIView.animateWithDuration(0.3) { () -> Void in
                self.clear(textView)
                self.parent.view!.layoutIfNeeded()
            }
        }
        tapDetector!.enabled = false
    }
    
    func clear(textView: UITextView) {
        textView.text = OrderTextDelegate.placeholder
        textView.textColor = UIColor.lightGrayColor()
        textView.textAlignment = .Center
        parent.lupa.hidden = false
        height.constant = 30
        active = false
    }
    
    func validate() -> Bool {
        if (parent.orderText.text.isEmpty || parent.orderText.text == OrderTextDelegate.placeholder || parent.orderText.text == OrderTextDelegate.error_placeholder) {
            parent.orderText.text = OrderTextDelegate.error_placeholder
            parent.orderText.textColor = OngoingOrderStateCell.ERROR_COLOR
            return false
        }
        return true
    }
    
    var total: CGFloat = 80 {
        didSet {
            if (active) {
                height.constant = total - 16.0 - 30.0
            }
            else {
                height.constant = 30
            }
        }
    }
    let height: NSLayoutConstraint
    let parent: OrderDescriptionViewController
    init(height: NSLayoutConstraint, parent: OrderDescriptionViewController) {
        self.height = height
        self.parent = parent
    }
}


struct Urgency {
    var caption: String;
    var value: Float;
    var type: String;

    static let ASAP = Urgency(caption: "Срочно", value: 1.0, type: "asap")
    static let DURING_THE_DAY = Urgency(caption: "В течение дня", value: 0.5, type: "day")
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
