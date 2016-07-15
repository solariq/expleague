//
//  AddLocationAlert.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 09.07.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class AddLocationAlertController: UIViewController {
    @IBOutlet weak var useCurrentLocationButton: UIButton!
    @IBOutlet weak var chooseLocationOnMapButton: UIButton!
    @IBOutlet weak var doNotUseLocationButton: UIButton!
    @IBOutlet weak var cancelButton: UIButton!
    
    var parent: UIViewController?
    let locationChoiceCallback: LocationChoiceCallback
    let locationProvider: LocationProvider

    init(parent: UIViewController?, locationChoiceCallback: LocationChoiceCallback, locationProvider: LocationProvider) {
        self.parent = parent
        self.locationChoiceCallback = locationChoiceCallback
        self.locationProvider = locationProvider
        super.init(nibName: "AddLocationAlert", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        for button in [useCurrentLocationButton, chooseLocationOnMapButton, doNotUseLocationButton, cancelButton] {
            button.layer.cornerRadius = Palette.CORNER_RADIUS
            button.clipsToBounds = true
        }
    }

    @IBAction func onUseCurrentLocation(sender: AnyObject) {
        let currentLocation = OrderLocation()
        currentLocation.setCurrentLocation(locationProvider)
        locationChoiceCallback.onLocationChoice(currentLocation)
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func onChooseLocationOnMap(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
        let navigation = UINavigationController(rootViewController: LocationChoiceController(callback: locationChoiceCallback, locationProvider: locationProvider))
        parent?.presentViewController(navigation, animated: true, completion: nil)
    }

    @IBAction func onDoNotUseLocation(sender: AnyObject) {
        locationChoiceCallback.onLocationChoice(OrderLocation())
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func onCancel(sender: AnyObject) {
        self.dismissViewControllerAnimated(true, completion: nil)
    }
}
