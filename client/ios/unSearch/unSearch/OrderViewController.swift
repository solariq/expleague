//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import XMPPFramework

import FBSDKCoreKit

import unSearchCore

class OrderViewController: UIViewController, CLLocationManagerDelegate {
    var keyboardTracker: KeyboardStateTracker!
    
    @IBOutlet weak var buttonTop: NSLayoutConstraint!
    @IBOutlet weak var buttonBottom: NSLayoutConstraint!
    @IBOutlet weak var orderDescription: UIView!
    
    @IBAction func fire(_ sender: AnyObject) {
        orderDescription.endEditing(true)
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        guard controller.orderTextDelegate!.validate() else {
            return
        }
        if (!controller.location.isLocalOrder()) {
            if let location = DataController.shared().currentLocation() {
                controller.location.location = location
            }
        }
        guard !controller.location.isLocalOrder() || controller.location.getLocation() != nil else {
            let alertView = UIAlertController(title: "Заказ", message: "На данный момент ваша геопозиция не найдена. Подождите несколько секунд, или отключите настройку \"рядом со мной\".", preferredStyle: .alert)
            alertView.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
            present(alertView, animated: true, completion: nil)
            return
        }
        guard controller.orderAttachmentsModel.completed() else {
            let alertView = UIAlertController(title: "Заказ", message: "На данный момент не все прикрепленные объекты сохранены. Подождите несколько секунд.", preferredStyle: .alert)
            alertView.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
            present(alertView, animated: true, completion: nil)
            return
        }
        
        FBSDKAppEvents.logEvent("Issue order", parameters: ["user": ExpLeagueProfile.active.jid.user, "topic": controller.orderText.text])
        ExpLeagueProfile.active.placeOrder(
            topic: controller.orderText.text,
            urgency: controller.urgency.isOn ? "asap" : "day",
            local: controller.location.isLocalOrder(),
            location: controller.location.getLocation(),
            experts: controller.experts.map{ return $0.id },
            images: controller.orderAttachmentsModel.getImagesIds()
        )
        controller.clear()
        controller.orderAttachmentsModel.clear()

        AppDelegate.instance.tabs.tabBar.isHidden = true
        AppDelegate.instance.tabs.selectedIndex = 1
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let initialBottom = self.buttonBottom.constant
        let initialTop = self.buttonTop.constant
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        keyboardTracker = KeyboardStateTracker(check: {controller.orderText.isFirstResponder}) { height in
            self.buttonBottom.constant = height > 0 ? height - self.tabBarController!.tabBar.frame.height + 2 : initialBottom
            self.buttonTop.constant = height > 0 ? 2 : initialTop
            self.view.layoutIfNeeded()
        }
        self.navigationController?.navigationBar.tintColor = UIColor.white
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.white
        ]
        AppDelegate.instance.orderView = self
    }
    
    override func viewWillAppear(_ animated: Bool) {
        navigationController!.isToolbarHidden = true
        navigationController!.isNavigationBarHidden = true
        keyboardTracker.start()
    }

    override func viewWillDisappear(_ animated: Bool) {
        keyboardTracker.stop()
    }
    
    override var preferredStatusBarStyle : UIStatusBarStyle {
        return .lightContent
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

    func append(expert exp: ExpLeagueMember) {
        if (!experts.contains(exp)) {
            experts.append(exp)
            update()
        }
    }
    
    var heightDiff: CGFloat = 80 + 49
    override func viewDidLoad() {
        super.viewDidLoad()
        orderTextBGColor = orderTextBackground.backgroundColor

        (view as! UITableView).allowsSelection = true
        (view as! UITableView).delegate = self
        
        orderTextDelegate = OrderTextDelegate(height: orderTextHeight, parent: self)
        orderText.delegate = orderTextDelegate
        orderText.textContainerInset = UIEdgeInsetsMake(8, 4, 8, 4);
    }
    
    override func viewWillLayoutSubviews() {
        adjustSizes(UIScreen.main.bounds.height)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        update()
    }
    
    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        coordinator.animate(alongsideTransition: { (UIViewControllerTransitionCoordinatorContext) -> Void in
            if (self.view.window != nil) {
                self.adjustSizes(size.height)
            }
        }, completion: nil)
    }
    
    @IBOutlet weak var unSearchY: NSLayoutConstraint!
    @IBOutlet weak var owlY: NSLayoutConstraint!
    
    internal func sizeOfInput(_ height: CGFloat) -> CGFloat {
        return max(CGFloat(82), height - CGFloat(4 * rowHeight) - heightDiff);
    }

    internal func adjustSizes(_ height: CGFloat) {
        let inputHeight = sizeOfInput(height)
        if (inputHeight > 130) {
            unSearchLabel.isHidden = false
            owlIcon.isHidden = false
            owlHeight.constant = min(max((inputHeight - 71.0) / 2.0, 50.0), 100)
            owlWidth.constant = owlHeight.constant * 160.0/168.0
            owlY.constant = (inputHeight - owlHeight.constant)/2.0 - 8
        }
        else if (inputHeight > 100) {
            unSearchLabel.isHidden = false
            owlIcon.isHidden = true
            unSearchY.constant = (inputHeight)/2.0 - 8 - unSearchLabel.frame.height
        }
        else {
            owlIcon.isHidden = true
            unSearchLabel.isHidden = true
        }
        orderTextDelegate!.total = inputHeight
    }
    
    internal func clear() {
        urgency.isOn = false
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
        urgencyType.text = urgency.isOn ? "Срочно" : "В течение дня"
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
        case .noLocation:
            locationDescription.text = "Не выбрано"
        case .currentLocation:
            locationDescription.text = "Рядом со мной"
        case .customLocation:
            locationDescription.text = "Выбрано на карте"
        }
    }

    @IBOutlet weak var urgencyType: UILabel!
    @IBAction func onUnrgency(_ sender: AnyObject) {
        update()
    }
    @IBOutlet weak var imagesCaption: UILabel!
    
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        if ((indexPath as NSIndexPath).item == 0) {
            return sizeOfInput(UIScreen.main.bounds.height)
        }
        return CGFloat(rowHeight);
    }
    
    override func tableView(_ tableView: UITableView, shouldHighlightRowAt indexPath: IndexPath) -> Bool {
        return 2...5 ~= (indexPath as NSIndexPath).item
    }
    
    override func tableView(_ tableView: UITableView, willSelectRowAt indexPath: IndexPath) -> IndexPath? {
        return 2...5 ~= (indexPath as NSIndexPath).item ? indexPath : nil
    }
    
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        if ((indexPath as NSIndexPath).item == 4) {
            FBSDKAppEvents.logEvent("Order attach", parameters: ["user": ExpLeagueProfile.active.jid.user])
            if (orderAttachmentsModel.attachmentsArray.isEmpty) {
                showAlertMenu(AddAttachmentAlertController(filter: orderAttachmentsModel.attachmentsArray.map({$0.imageId})) { imageId in
                    self.orderAttachmentsModel.addAttachment(imageId)
                    self.parent!.navigationController!.pushViewController(
                        OrderAttachmentsController(orderAttachmentsModel: self.orderAttachmentsModel),
                        animated: true
                    )
                })
            }
            else {
                parent!.navigationController!.pushViewController(
                    OrderAttachmentsController(orderAttachmentsModel: orderAttachmentsModel),
                    animated: true
                )
            }
        }
        else if ((indexPath as NSIndexPath).item == 3) {
            FBSDKAppEvents.logEvent("Order experts filter", parameters: ["user": ExpLeagueProfile.active.jid.user])
            parent!.navigationController!.pushViewController(
                ChooseExpertViewController(owner: self),
                animated: true
            )
        }
        else if ((indexPath as NSIndexPath).item == 2) {
            FBSDKAppEvents.logEvent("Order location", parameters: ["user": ExpLeagueProfile.active.jid.user])
            UINavigationBar.appearance().setBackgroundImage(UIImage(named: "experts_background"), for: .default)
            UINavigationBar.appearance().titleTextAttributes = [NSForegroundColorAttributeName : UIColor.white]
            UINavigationBar.appearance().tintColor = UIColor.white
//            UINavigationBar.appearance().barTintColor = UIColor.whiteColor()
            UISearchBar.appearance().barStyle = .black

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
            GMSPlacePicker(config: config).pickPlace(){ (placeOrNil, _) in
                guard let place = placeOrNil else {
                    self.location.clearLocation()
                    return
                }
                self.location.setLocation(place.coordinate)
                if let deviceLocation = DataController.shared().currentLocation() {
                    let point1 = MKMapPointForCoordinate(place.coordinate)
                    let point2 = MKMapPointForCoordinate(deviceLocation)
                    let distance = MKMetersBetweenMapPoints(point1, point2)
                    if (distance < 100) {
                        self.location.locationType = .currentLocation
                    }
                    else {
                        self.location.locationType = .customLocation
                    }
                }
                self.update()
            }
        }
        tableView.deselectRow(at: indexPath, animated: false)
    }


    func showAlertMenu(_ alert: UIViewController) {
        alert.modalPresentationStyle = .overFullScreen
        self.providesPresentationContextTransitionStyle = true;
        self.definesPresentationContext = true;
    
        parent?.present(alert, animated: true, completion: nil)
    }
}

protocol ImageSenderQueue {
    func append(_ id: String, image: UIImage, progress: (UIProgressView) -> Void)
    func report(_ id: String, status: Bool);
}


enum OrderLocationType {
    case noLocation
    case currentLocation
    case customLocation
}

class OrderLocation {
    var locationType: OrderLocationType!
    var location: CLLocationCoordinate2D?
    
    init() {
        self.locationType = .noLocation
    }
    
    init(location: CLLocationCoordinate2D!) {
        setLocation(location)
    }
    
    func setCurrentLocation(_ location: CLLocationCoordinate2D) {
        self.locationType = .currentLocation
        self.location = location
    }
    
    func setLocation(_ location: CLLocationCoordinate2D!) {
        self.locationType = .customLocation
        self.location = location
    }
    
    func clearLocation() {
        locationType = .noLocation
        location = nil
    }
    
    func getLocation() -> CLLocationCoordinate2D? {
        return self.location
    }
    
    func isLocalOrder() -> Bool {
        return self.locationType != .noLocation
    }
}

class OrderTextDelegate: NSObject, UITextViewDelegate {
    static let textHeight = CGFloat(35.0)
    static let placeholder = "Найдем для вас что угодно!"
    static let error_placeholder = "Введите текст запроса"
    var active: Bool = false
    var tapDetector: UIGestureRecognizer?
    func textViewShouldBeginEditing(_ textView: UITextView) -> Bool {
        UIView.animate(withDuration: 0.3, animations: { () -> Void in
            if (textView.text == OrderTextDelegate.placeholder || textView.text == OrderTextDelegate.error_placeholder) {
                textView.text = ""
                textView.textAlignment = .left
                self.parent.lupa.isHidden = true
            }
            textView.textColor = UIColor.black
            self.height.constant = max(OrderTextDelegate.textHeight, self.total - 44)
            self.parent.view!.layoutIfNeeded()
        }) 
        if (tapDetector == nil) {
            tapDetector = UITapGestureRecognizer(trailingClosure: {
                textView.endEditing(true)
            })
            parent.view.addGestureRecognizer(tapDetector!)
        }
        else {
            tapDetector!.isEnabled = true
        }
        active = true
        return true
    }
    
    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
        return true
    }
    
    func textViewDidEndEditing(_ textView: UITextView) {
        textView.resignFirstResponder()
        if (textView.text == "") {
            UIView.animate(withDuration: 0.3, animations: { () -> Void in
                self.clear(textView)
                self.parent.view!.layoutIfNeeded()
            }) 
        }
        tapDetector!.isEnabled = false
    }
    
    func clear(_ textView: UITextView) {
        textView.text = OrderTextDelegate.placeholder
        textView.textColor = UIColor.lightGray
        textView.textAlignment = .center
        parent.lupa.isHidden = false
        height.constant = OrderTextDelegate.textHeight
        active = false
    }
    
    func validate() -> Bool {
        parent.orderText.text = parent.orderText.text.trimmingCharacters(
            in: CharacterSet.whitespacesAndNewlines
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
