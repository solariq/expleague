//
//  SearchLocationController.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 14.05.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import MapKit

class SearchLocationController: UIViewController, MKMapViewDelegate, UIGestureRecognizerDelegate {
    let parent: OrderDescriptionViewController
    let mapView: MKMapView
 
    var location: CLLocationCoordinate2D?
    var deviceLocation: CLLocationCoordinate2D?
    var locationProvider: LocationProvider!
    
    var annotation: MKPointAnnotation?

    init(parent: OrderDescriptionViewController, locationProvider: LocationProvider!) {
        self.parent = parent
        self.location = parent.location.getLocation()
        self.locationProvider = locationProvider
        
        self.mapView = MKMapView()
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        print("Map dialog loaded")
        
        self.mapView.delegate = self
        self.mapView.userTrackingMode = MKUserTrackingMode.Follow
        
        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(SearchLocationController.handleTap(_:)))
        tapRecognizer.numberOfTapsRequired = 1
        tapRecognizer.numberOfTouchesRequired = 1
        tapRecognizer.delegate = self

        let panRecognizer = UIPanGestureRecognizer(target: self, action: #selector(SearchLocationController.handlePan(_:)))
        panRecognizer.minimumNumberOfTouches = 1
        panRecognizer.maximumNumberOfTouches = 1
        panRecognizer.delegate = self

        self.mapView.addGestureRecognizer(tapRecognizer)
        self.mapView.addGestureRecognizer(panRecognizer)
 
        if (self.location == nil) {
            if let currentLocation = self.locationProvider.locationManager.location {
                self.updateLocation(currentLocation.coordinate)
            }
        }
        else {
            self.updateLocation(location)
        }
        
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.blackColor()]
        navigationItem.title = nil
        let doneButton = UIBarButtonItem(title: "Искать здесь", style: .Done, target: self, action: #selector(SearchLocationController.approve))
        doneButton.tintColor = UIColor.blackColor()
        navigationItem.setRightBarButtonItem(doneButton, animated: false)
        
        let cancelButton = UIBarButtonItem(title: "Отменить", style: .Done, target: self, action: #selector(SearchLocationController.cancel))
        cancelButton.tintColor = UIColor.blackColor()
        navigationItem.setLeftBarButtonItem(cancelButton, animated: false)
    }
    
    override func loadView() {
        view = self.mapView
    }
    
    func gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWithGestureRecognizer otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }

    func handleTap(recognizer: UITapGestureRecognizer) {
        if (recognizer.state != UIGestureRecognizerState.Ended) {
            return
        }
        
        print("Tap arrived")
        
        let touchPoint = recognizer.locationInView(self.mapView)
        updateLocation(self.mapView.convertPoint(touchPoint, toCoordinateFromView: self.mapView))
    }
    
    func handlePan(recognizer: UIPanGestureRecognizer) {
    }
    
    func mapView(mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
        let touchPoint = CGPoint(x: mapView.center.x, y:mapView.center.y)
        let coordinate = self.mapView.convertPoint(touchPoint, toCoordinateFromView: self.mapView)
        self.updateLocation(coordinate)
        print("regionDidChangeAnimated: \(coordinate)")
    }
    
    func approve() {
        parent.location.setLocation(location)
        parent.update()
        dismissViewControllerAnimated(true, completion: nil)
    }
    
    func cancel() {
        parent.location.clearLocation()
        parent.update()
        dismissViewControllerAnimated(true, completion: nil)
    }

    func updateLocation(optCoordinate: CLLocationCoordinate2D?) {
        if let coordinate = optCoordinate {
            //self.mapView.setRegion(MKCoordinateRegion(center: coordinate, span: MKCoordinateSpanMake(0, 0)), animated: false)
            if (self.annotation == nil) {
                self.annotation = MKPointAnnotation()
                self.mapView.addAnnotation(self.annotation!)
                self.mapView.selectAnnotation(self.annotation!, animated: false)
            }
            self.annotation!.coordinate = coordinate
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
        parent.update()
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func chooseLocation(sender: AnyObject) {
        parent.update()
        self.dismissViewControllerAnimated(true, completion: nil)
    }

    @IBAction func cancel(sender: AnyObject) {
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
