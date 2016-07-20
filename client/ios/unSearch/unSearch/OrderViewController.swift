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
        orderDescription.endEditing(true)
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        guard controller.orderTextDelegate!.validate() else {
            return
        }
        if (!controller.location.isLocalOrder()) {
            controller.location.location = controller.locationProvider.deviceLocation
        }
        guard !controller.location.isLocalOrder() || controller.location.getLocation() != nil else {
            let alertView = UIAlertController(title: "Заказ", message: "На данный момент ваша геопозиция не найдена. Подождите несколько секунд, или отключите настройку \"рядом со мной\".", preferredStyle: .Alert)
            alertView.addAction(UIAlertAction(title: "Ok", style: .Default, handler: nil))
            presentViewController(alertView, animated: true, completion: nil)
            return
        }
        guard controller.orderAttachmentsModel.completed() else {
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
            images: controller.orderAttachmentsModel.getImagesIds()
        )
        controller.clear()
        controller.orderAttachmentsModel.clear()

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
        self.navigationController?.navigationBar.tintColor = UIColor.whiteColor()
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.whiteColor()
        ]
        AppDelegate.instance.orderView = self
    }
    
    override func viewWillAppear(animated: Bool) {
        navigationController!.toolbarHidden = true
        navigationController!.navigationBarHidden = true
        keyboardTracker.start()
    }

    override func viewWillDisappear(animated: Bool) {
        keyboardTracker.stop()
    }
    
    override func preferredStatusBarStyle() -> UIStatusBarStyle {
        return .LightContent
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

    let orderAttachmentsModel = OrderAttachmentsModel()
    var orderTextBGColor: UIColor?
    var orderTextDelegate: OrderTextDelegate?
    var experts: [ExpLeagueMember] = []
    var location: OrderLocation = OrderLocation()

    let locationProvider = LocationProvider()

    func append(expert exp: ExpLeagueMember) {
        if (!experts.contains(exp)) {
            experts.append(exp)
            update()
        }
    }
    
    var heightDiff: CGFloat = 80 + 49
    override func viewDidLoad() {
        super.viewDidLoad()
        self.locationProvider.setUpLocationProvider()
        orderTextBGColor = orderTextBackground.backgroundColor

        (view as! UITableView).allowsSelection = true
        (view as! UITableView).delegate = self
        
        orderTextDelegate = OrderTextDelegate(height: orderTextHeight, parent: self)
        orderText.delegate = orderTextDelegate
        orderText.textContainerInset = UIEdgeInsetsMake(8, 4, 8, 4);
    }
    
    override func viewWillLayoutSubviews() {
        adjustSizes(UIScreen.mainScreen().bounds.height)
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        update()
    }
    
    override func viewWillTransitionToSize(size: CGSize, withTransitionCoordinator coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animateAlongsideTransition({ (UIViewControllerTransitionCoordinatorContext) -> Void in
            if (self.view.window != nil) {
                self.adjustSizes(size.height)
            }
        }, completion: nil)
    }
    
    @IBOutlet weak var unSearchY: NSLayoutConstraint!
    @IBOutlet weak var owlY: NSLayoutConstraint!
    
    internal func sizeOfInput(height: CGFloat) -> CGFloat {
        return max(CGFloat(82), height - CGFloat(4 * rowHeight) - heightDiff);
    }

    internal func adjustSizes(height: CGFloat) {
        let inputHeight = sizeOfInput(height)
        if (inputHeight > 130) {
            unSearchLabel.hidden = false
            owlIcon.hidden = false
            owlHeight.constant = min(max((inputHeight - 71.0) / 2.0, 50.0), 100)
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
        orderAttachmentsModel.clear()
        experts.removeAll()
        location.clearLocation()
        update()
    }
    
    internal func update() {
        let count = orderAttachmentsModel.count
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
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        if (indexPath.item == 0) {
            return sizeOfInput(UIScreen.mainScreen().bounds.height)
        }
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
            if (orderAttachmentsModel.attachmentsArray.isEmpty) {
                showAlertMenu(AddAttachmentAlertController(parent: parentViewController, filter: orderAttachmentsModel.attachmentsArray.map({$0.imageId})) { imageId in
                    self.orderAttachmentsModel.addAttachment(imageId)
                    self.parentViewController!.navigationController!.pushViewController(
                        OrderAttachmentsController(orderAttachmentsModel: self.orderAttachmentsModel),
                        animated: true
                    )
                })
            }
            else {
                parentViewController!.navigationController!.pushViewController(
                    OrderAttachmentsController(orderAttachmentsModel: orderAttachmentsModel),
                    animated: true
                )
            }
        }
        else if (indexPath.item == 3) {
            parentViewController!.navigationController!.pushViewController(
                ChooseExpertViewController(parent: self),
                animated: true
            )
        }
        else if (indexPath.item == 2) {
            
            UINavigationBar.appearance().setBackgroundImage(UIImage(named: "experts_background"), forBarMetrics: .Default)
            UINavigationBar.appearance().titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
            UINavigationBar.appearance().tintColor = UIColor.whiteColor()
//            UINavigationBar.appearance().barTintColor = UIColor.whiteColor()
            UISearchBar.appearance().barStyle = .Black

//            navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
//            navigationItem.title = "Укажите местоположение"
            var vp: GMSCoordinateBounds? = nil
            if let location = self.location.location {
                vp = GMSCoordinateBounds(coordinate: CLLocationCoordinate2DMake(location.latitude - 0.005, location.longitude - 0.005),
                                         coordinate: CLLocationCoordinate2DMake(location.latitude + 0.005, location.longitude + 0.005))
            }
            self.location.clearLocation()
            self.update()

            let config = GMSPlacePickerConfig(viewport: vp)
            GMSPlacePicker(config: config).pickPlaceWithCallback(){ (placeOrNil, _) in
                guard let place = placeOrNil else {
                    self.location.clearLocation()
                    return
                }
                self.location.setLocation(place.coordinate)
                if let deviceLocation = self.locationProvider.deviceLocation {
                    let point1 = MKMapPointForCoordinate(place.coordinate)
                    let point2 = MKMapPointForCoordinate(deviceLocation)
                    let distance = MKMetersBetweenMapPoints(point1, point2)
                    if (distance < 100) {
                        self.location.locationType = .CurrentLocation
                    }
                    else {
                        self.location.locationType = .CustomLocation
                    }
                }
                self.update()
            }
        }
        tableView.deselectRowAtIndexPath(indexPath, animated: false)
    }


    func showAlertMenu(alert: UIViewController) {
        alert.modalPresentationStyle = .OverFullScreen
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
    
        parentViewController?.presentViewController(alert, animated: true, completion: nil)
    }
}

protocol ImageSenderQueue {
    func append(id: String, image: UIImage, progress: (UIProgressView) -> Void)
    func report(id: String, status: Bool);
}

class OrderTextDelegate: NSObject, UITextViewDelegate {
    static let textHeight = CGFloat(35.0)
    static let placeholder = "Найдем для вас что угодно!"
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
    
    func textView(textView: UITextView, shouldChangeTextInRange range: NSRange, replacementText text: String) -> Bool {
        guard text == "\n" else {
            return true
        }
        textView.endEditing(true)
        (parent.parentViewController as! OrderViewController).fire(textView)
        return false
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
        parent.orderText.text = parent.orderText.text.stringByTrimmingCharactersInSet(
            NSCharacterSet.whitespaceAndNewlineCharacterSet()
        )
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
