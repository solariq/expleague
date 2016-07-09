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
        guard controller.orderTextDelegate!.validate() else {
            return
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

    let orderAttachmentsModel = OrderAttachmentsModel()
    var orderTextBGColor: UIColor?
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
        orderAttachmentsModel.clear()
        experts.removeAll()
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
        tableView.deselectRowAtIndexPath(indexPath, animated: false)
    }

    func showAttachmentChoiceAlert() {
        let parentViewController = self.parentViewController
        let addAttachmentAlert = AddAttachmentAlertController(
            parent: parentViewController,
            imageAttachmentCallback: OrderImageAttachmentCallback(orderDescriptionViewController: self)
        )
        addAttachmentAlert.modalPresentationStyle = .OverCurrentContext
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
        
        parentViewController?.presentViewController(addAttachmentAlert, animated: true, completion: nil)
    }

    func showExpertChoiceView() {
        let chooseExpert = ChooseExpertViewController(parent: self)

        let navigation = UINavigationController(rootViewController: chooseExpert)
        self.presentViewController(navigation, animated: true, completion: nil)
    }

    func showLocationChoiceAlert() {
        let parentViewController = self.parentViewController
        let addLocationAlert = AddLocationAlertController(
            parent: parentViewController,
            locationChoiceCallback: OrderLocationChoiceCallback(orderDescriptionViewController: self),
            locationProvider: locationProvider
        )
        addLocationAlert.modalPresentationStyle = .OverCurrentContext
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
        
        parentViewController?.presentViewController(addLocationAlert, animated: true, completion: nil)
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

class OrderImageAttachmentCallback: ImageAttachmentCallback {
    let orderDescriptionViewController: OrderDescriptionViewController
    
    init(orderDescriptionViewController: OrderDescriptionViewController) {
        self.orderDescriptionViewController = orderDescriptionViewController
    }
    
    func onAttach(image: UIImage, imageId: String) {
        let model = self.orderDescriptionViewController.orderAttachmentsModel
        model.addAttachment(image, imageId: imageId)
        let navigation = UINavigationController(rootViewController: OrderAttachmentsController(orderAttachmentsModel: model))
        self.orderDescriptionViewController.parentViewController!.presentViewController(navigation, animated: true, completion: nil)
        self.orderDescriptionViewController.update()
    }
}

class OrderLocationChoiceCallback: LocationChoiceCallback {
    let orderDescriptionViewController: OrderDescriptionViewController
    
    init(orderDescriptionViewController: OrderDescriptionViewController) {
        self.orderDescriptionViewController = orderDescriptionViewController
    }

    func onLocationChoice(location: OrderLocation) {
        self.orderDescriptionViewController.location = location
        self.orderDescriptionViewController.update()
    }
}