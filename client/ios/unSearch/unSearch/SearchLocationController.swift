//
//  SearchLocationController.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 14.05.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import MapKit

class SearchLocationController: UIViewController, CLLocationManagerDelegate, MKMapViewDelegate, UIGestureRecognizerDelegate {
    let parent: OrderDescriptionViewController
    let mapView: MKMapView
 
    let locationManager = CLLocationManager()
    var location: CLLocationCoordinate2D?
    var deviceLocation: CLLocationCoordinate2D?

    init(parent: OrderDescriptionViewController) {
        self.parent = parent
        self.location = parent.location
        self.mapView = MKMapView()
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        locationManager.requestWhenInUseAuthorization()
        if CLLocationManager.locationServicesEnabled() {
            locationManager.delegate = self
            locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            locationManager.startUpdatingLocation()
        }

        print("Map dialog loaded")
        
        self.mapView.delegate = self
        self.mapView.userTrackingMode = MKUserTrackingMode.Follow
        
        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(SearchLocationController.handleTap(_:)))
        tapRecognizer.numberOfTapsRequired = 1
        tapRecognizer.numberOfTouchesRequired = 1
        tapRecognizer.delegate = self
        
        self.mapView.addGestureRecognizer(tapRecognizer)
 
        if (self.location == nil) {
            if let currentLocation = self.locationManager.location {
                self.updateLocation(currentLocation.coordinate)
            }
        }
        else {
            self.updateLocation(location)
        }
        
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.blackColor()]
        navigationItem.title = "Искать рядом"
        let doneButton = UIBarButtonItem(title: "Готово", style: .Done, target: self, action: #selector(SearchLocationController.approve))
        doneButton.tintColor = UIColor.blackColor()
        navigationItem.setRightBarButtonItem(doneButton, animated: false)
        
        let cancelButton = UIBarButtonItem(title: "Отменить", style: .Done, target: self, action: #selector(SearchLocationController.cancel))
        cancelButton.tintColor = UIColor.blackColor()
        navigationItem.setLeftBarButtonItem(cancelButton, animated: false)
    }
    
    override func loadView() {
        view = self.mapView
    }
    
    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        self.deviceLocation = manager.location?.coordinate
    }
    
    func handleTap(recognizer: UITapGestureRecognizer) {
        if (recognizer.state != UIGestureRecognizerState.Ended) {
            return
        }
        
        print("Tap arrived")
        
        let touchPoint = recognizer.locationInView(self.mapView)
        updateLocation(self.mapView.convertPoint(touchPoint, toCoordinateFromView: self.mapView))
    }
    
    
    func approve() {
        parent.location = location
        parent.isLocal.setOn(true, animated: false)
        parent.update()
        dismissViewControllerAnimated(true, completion: nil)
    }
    
    func cancel() {
        parent.location = nil
        parent.isLocal.setOn(false, animated: false)
        parent.update()
        dismissViewControllerAnimated(true, completion: nil)
    }

    func updateLocation(optCoordinate: CLLocationCoordinate2D?) {
        if let coordinate = optCoordinate {
            self.mapView.setRegion(MKCoordinateRegion(center: coordinate, span: MKCoordinateSpanMake(0.05, 0.05)), animated: true)
            let point = MKPointAnnotation()
            point.coordinate = coordinate
            self.mapView.removeAnnotations(self.mapView.annotations)
            self.mapView.addAnnotation(point)
            self.location = coordinate
            print("Location update completed: \(coordinate)")
        }
    }
}

class SearchLocationDialogController: UIViewController {
    let parent: OrderDescriptionViewController
    init(parent: OrderDescriptionViewController) {
        self.parent = parent
        super.init(nibName: "SearchLocationDialog", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK
    
    
    @IBOutlet weak var useCurrentLocationButton: UIButton!
    @IBOutlet weak var chooseLocationButton: UIButton!
    @IBOutlet weak var cancelButton: UIButton!
    
    @IBAction func useCurrentLocation(sender: AnyObject) {
        parent.isLocal.setOn(true, animated: false)
        parent.update()
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func chooseLocation(sender: AnyObject) {
        parent.isLocal.setOn(true, animated: false)
        parent.update()
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func cancel(sender: AnyObject) {
        parent.isLocal.setOn(false, animated: false)
        parent.update()
        self.dismissViewControllerAnimated(true, completion: nil)
    }
    
    override func viewDidLoad() {
        useCurrentLocationButton.layer.cornerRadius = Palette.CORNER_RADIUS
        useCurrentLocationButton.clipsToBounds = true
        chooseLocationButton.layer.cornerRadius = Palette.CORNER_RADIUS
        chooseLocationButton.clipsToBounds = true
        cancelButton.layer.cornerRadius = Palette.CORNER_RADIUS
        cancelButton.clipsToBounds = true
    }
}
