//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import XMPPFramework
import SDCAlertView

class OrderViewController: UIViewController, CLLocationManagerDelegate {
    var keyboardTracker: KeyboardStateTracker!
    
    @IBOutlet weak var buttonTop: NSLayoutConstraint!
    @IBOutlet weak var buttonBottom: NSLayoutConstraint!
    @IBOutlet weak var orderDescription: UIView!
    
    @IBAction func fire(sender: AnyObject) {
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        if (controller.location.isLocalOrder() && controller.location.getLocation() == nil) {
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
            urgency: controller.urgency.on ? "asap" : "day",
            local: controller.location.isLocalOrder(),
            location: controller.location.getLocation(),
            experts: controller.experts.map{ return $0.id },
            images: controller.attachments.ids
        )
        controller.clear()
        controller.attachments.clear()

        AppDelegate.instance.tabs.tabBar.hidden = true
        AppDelegate.instance.tabs.selectedIndex = 1
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let initialBottom = self.buttonBottom.constant
        let initialTop = self.buttonTop.constant
        keyboardTracker = KeyboardStateTracker() { height in
            self.buttonBottom.constant = height > 0 ? height - self.tabBarController!.tabBar.frame.height + 2 : initialBottom
            self.buttonTop.constant = height > 0 ? 2 : initialTop
            self.view.layoutIfNeeded()
        }
        AppDelegate.instance.orderView = self
    }
    
    override func viewWillAppear(animated: Bool) {
        keyboardTracker.start()
    }

    override func viewWillDisappear(animated: Bool) {
        keyboardTracker.stop()
    }
    
    override func preferredStatusBarStyle() -> UIStatusBarStyle {
        return .Default
    }

    var descriptionController: OrderDescriptionViewController {
        return self.childViewControllers[0] as! OrderDescriptionViewController
    }
}

class OrderDescriptionViewController: UITableViewController {
    let error_color = UIColor(red: 1.0, green: 0.0, blue: 0.0, alpha: 0.1)
    let rowHeight = 62;
    @IBOutlet weak var lupa: UIImageView!

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
    @IBOutlet weak var expertsDescription: UILabel!
    @IBOutlet weak var locationDescription: UILabel!

    let attachments = AttachmentsViewDelegate()
    var orderTextBGColor: UIColor?
    let picker = UIImagePickerController()
    var pickerDelegate: ImagePickerDelegate?
    var orderTextDelegate: OrderTextDelegate?
    var experts: [ExpLeagueMember] = []
    var location: OrderLocation! = OrderLocation()

    let locationProvider = LocationProvider()

    func append(expert exp: ExpLeagueMember) {
        if (!experts.contains(exp)) {
            experts.append(exp)
            update()
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.locationProvider.setUpLocationProvider()
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
        orderText.textContainerInset = UIEdgeInsetsMake(8, 4, 8, 4);
    }
    
    override func viewDidAppear(animated: Bool) {
        adjustSizes(view.frame.height)
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        update()
    }
    
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animateAlongsideTransition({ (UIViewControllerTransitionCoordinatorContext) -> Void in
            if (self.view.window != nil) {
                self.adjustSizes(size.height - self.view.window!.frame.height + self.view.frame.height)
            }
        }, completion: nil)
    }
    
    @IBOutlet weak var unSearchY: NSLayoutConstraint!
    @IBOutlet weak var owlY: NSLayoutConstraint!
    
    internal func adjustSizes(height: CGFloat) {
//        print("\(height), \(sizeOfInput(height))")
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
        urgency.on = false
        orderTextDelegate!.clear(orderText)
        attachments.clear()
        experts.removeAll()
        update()
    }
    
    internal func update() {
        let count = attachments.count
        if (count > 4) {
            imagesCaption.text = "\(count) приложений"
        }
        else if (count > 1) {
            imagesCaption.text = "\(count) приложения"
        }
        else if (count > 0) {
            imagesCaption.text = "\(count) приложениe"
        }
        else {
            imagesCaption.text = "Не выбрано"
        }
        urgencyType.text = urgency.on ? "Срочно" : "В течение дня"
        if experts.count > 0 {
            expertsDescription.text = ""
            for i in 0..<experts.count {
                if (i > 0) {
                    expertsDescription.text! += ", "
                }
                expertsDescription.text! += experts[i].name
            }
        }
        else {
            expertsDescription.text = "Автоматически"
        }
        switch self.location.locationType! {
        case .NoLocation:
            locationDescription.text = "Не выбрано"
        case .CurrentLocation:
            locationDescription.text = "Рядом со мной"
        case .CustomLocation:
            locationDescription.text = "Выбрано на карте"
        }
    }

    @IBOutlet weak var urgencyType: UILabel!
    @IBAction func onUnrgency(sender: AnyObject) {
        update()
    }
    @IBOutlet weak var imagesCaption: UILabel!

    internal func sizeOfInput(height: CGFloat) -> CGFloat {
        return max(CGFloat(82), height - CGFloat(5 * rowHeight));
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        if (indexPath.item == 0) {
            return sizeOfInput(view.frame.height)
        }
//        else if (indexPath.item == 4) {
//            return CGFloat(82)
//        }
        return CGFloat(rowHeight);
    }
    
    override func tableView(tableView: UITableView, shouldHighlightRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return 2...5 ~= indexPath.item
    }
    
    override func tableView(tableView: UITableView, willSelectRowAtIndexPath indexPath: NSIndexPath) -> NSIndexPath? {
        return 2...5 ~= indexPath.item ? indexPath : nil
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        if (indexPath.item == 4) {
            showAttachmentChoiceAlert();
        }
        else if (indexPath.item == 3) {
            showExpertChoiceView();
        }
        else if (indexPath.item == 2) {
            showLocationChoiceAlert();
        }
    }
    
    func showAttachmentChoiceAlert() {
        let showCameraCapture = { (action: AlertAction!) -> Void in
            let navigation = UINavigationController(rootViewController: CameraCaptureController())
            self.presentViewController(navigation, animated: true, completion: nil)
            self.update()
        }
        
        let showImagePicker = { (action: AlertAction!) -> Void in
            self.update()
            self.presentViewController(self.picker, animated: true, completion: nil)
        }
        
        
        let showAttachments = { (action: AlertAction!) -> Void in
            self.update()
        }
        
        let alertController = AlertController(title: "Добавить вложение", message: nil, preferredStyle: .ActionSheet)

        let layout = UICollectionViewFlowLayout()
        layout.sectionInset = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        layout.itemSize = CGSize(width: 80, height: 80)

        let attachmentsView = UICollectionView(frame: alertController.contentView.frame, collectionViewLayout: layout)
        let attachments = ImageCollectionPreviewDelegate()
        attachmentsView.delegate = attachments
        attachmentsView.dataSource = attachments
        attachmentsView.userInteractionEnabled = true
        attachmentsView.allowsSelection = true
        attachmentsView.backgroundColor = UIColor.whiteColor()
        //attachmentsView.backgroundView = UIView(frame: CGRectZero);
        attachmentsView.layoutMargins = UIEdgeInsetsZero
        attachmentsView.registerClass(ImagePreview.self, forCellWithReuseIdentifier: "ImagePreview")
        attachments.view = attachmentsView

        attachments.fetchPhotoAtIndexFromEnd(0)
        alertController.contentView.addSubview(attachmentsView)
        alertController.addAction(AlertAction(title: "Сделать снимок", style: .Default, handler: showCameraCapture))
        alertController.addAction(AlertAction(title: "Добавить фото", style: .Default, handler: showImagePicker))
        //alertController.addAction(UIAlertAction(title: "Просмотреть вложения", style: .Default, handler: showAttachments))
        alertController.addAction(AlertAction(title: "Отменить", style: .Preferred, handler: nil))
        alertController.present()
    }
    
    func showExpertChoiceView() {
        let chooseExpert = ChooseExpertViewController(parent: self)
        
        let navigation = UINavigationController(rootViewController: chooseExpert)
        self.presentViewController(navigation, animated: true, completion: nil)
    }

    func showLocationChoiceAlert() {
        let alertController = UIAlertController(title: "Связать с гео-позицией", message: nil, preferredStyle: .ActionSheet)
        
        let useCurrentLocationActionHandler = { (action: UIAlertAction!) -> Void in
            self.location.setCurrentLocation(self.locationProvider)
            self.update()
        }
        
        let showMapActionHandler = { (action: UIAlertAction!) -> Void in
            self.update()
            
            let navigation = UINavigationController(rootViewController: SearchLocationController(parent: self, locationProvider: self.locationProvider))
            self.presentViewController(navigation, animated: true, completion: nil)
        }
        
        
        let cancelActionHandler = { (action: UIAlertAction!) -> Void in
            self.location.clearLocation()
            self.update()
        }
        
        alertController.addAction(UIAlertAction(title: "Искать рядом со мной", style: .Default, handler: useCurrentLocationActionHandler))
        alertController.addAction(UIAlertAction(title: "Выбрать на карте", style: .Default, handler: showMapActionHandler))
        alertController.addAction(UIAlertAction(title: "Не использовать гео-позицию", style: .Default, handler: cancelActionHandler))
        alertController.addAction(UIAlertAction(title: "Отменить", style: .Cancel, handler: nil))
        self.presentViewController(alertController, animated: true, completion: nil)
    }
}

class ImageAttachment: UICollectionViewCell {
    @IBOutlet weak var image: UIImageView!
    @IBOutlet weak var progress: UIProgressView!
    
    override func awakeFromNib() {
        progress.progress = 0.0
    }
}

class AttachmentsViewDelegate: NSObject, UICollectionViewDelegate, UICollectionViewDataSource, ImageSenderQueue {
    var view: UICollectionView?
    var cells: [UIImage] = []
    var progress: [(UIProgressView)->Void] = []
    var ids: [String] = []
    var status: [Bool] = []
    
    var count: Int {
        var result = 0
        for i in 0..<status.count {
            result += status[i] ? 1 : 0
        }
        return result
    }
    
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
        parent?.update()
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
        parent?.update()
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
        progressView = nil
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
            })
            self.uploadImage()
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
        requestBodyData.appendData("\r\n".dataUsingEncoding(NSUTF8StringEncoding)!)
        
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
        progressView?.progressTintColor = UIColor.blueColor()
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
//        print("Loaded: " + imageId!)
//        print(response);
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
    static let textHeight = CGFloat(35.0)
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
            self.height.constant = max(OrderTextDelegate.textHeight, self.total - 44)
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
        height.constant = OrderTextDelegate.textHeight
        active = false
    }
    
    func validate() -> Bool {
        if (parent.orderText.text.isEmpty || parent.orderText.text == OrderTextDelegate.placeholder || parent.orderText.text == OrderTextDelegate.error_placeholder) {
            parent.orderText.text = OrderTextDelegate.error_placeholder
            parent.orderText.textColor = Palette.ERROR
            return false
        }
        return true
    }
    
    var total: CGFloat = 80 {
        didSet {
            if (active) {
                height.constant = max(OrderTextDelegate.textHeight, total - 44)
            }
            else {
                height.constant = OrderTextDelegate.textHeight
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
