//
//  LocationChoiceController.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 09.07.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit

class LocationChoiceController: UIViewController, MKMapViewDelegate, UIGestureRecognizerDelegate {
    let mapView: MKMapView
    
    var location: CLLocationCoordinate2D?
    var deviceLocation: CLLocationCoordinate2D?

    let locationChoiceCallback: LocationChoiceCallback
    let locationProvider: LocationProvider
    
    var annotation: MKPointAnnotation?
    
    init(callback: LocationChoiceCallback, locationProvider: LocationProvider) {
        self.locationChoiceCallback = callback
        self.locationProvider = locationProvider
        
        self.mapView = MKMapView()
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        self.mapView.delegate = self
        self.mapView.userTrackingMode = MKUserTrackingMode.Follow
        
        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(LocationChoiceController.handleTap(_:)))
        tapRecognizer.numberOfTapsRequired = 1
        tapRecognizer.numberOfTouchesRequired = 1
        tapRecognizer.delegate = self
        
        self.mapView.addGestureRecognizer(tapRecognizer)
        
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
        let doneButton = UIBarButtonItem(title: "Искать здесь", style: .Done, target: self, action: #selector(LocationChoiceController.approve))
        doneButton.tintColor = UIColor.blackColor()
        navigationItem.setRightBarButtonItem(doneButton, animated: false)
        
        let cancelButton = UIBarButtonItem(title: "Отменить", style: .Done, target: self, action: #selector(LocationChoiceController.cancel))
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
        if (recognizer.state == UIGestureRecognizerState.Ended) {
            let touchPoint = recognizer.locationInView(self.mapView)
            updateLocation(self.mapView.convertPoint(touchPoint, toCoordinateFromView: self.mapView))
        }
    }
    
    func mapView(mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
        let touchPoint = CGPoint(x: mapView.center.x, y:mapView.center.y)
        let coordinate = self.mapView.convertPoint(touchPoint, toCoordinateFromView: self.mapView)
        self.updateLocation(coordinate)
    }
    
    func approve() {
        locationChoiceCallback.onLocationChoice(OrderLocation(location: self.location))
        dismissViewControllerAnimated(true, completion: nil)
    }
    
    func cancel() {
        dismissViewControllerAnimated(true, completion: nil)
    }
    
    func updateLocation(optCoordinate: CLLocationCoordinate2D?) {
        if let coordinate = optCoordinate {
            if (self.annotation == nil) {
                self.annotation = MKPointAnnotation()
                self.mapView.addAnnotation(self.annotation!)
                self.mapView.selectAnnotation(self.annotation!, animated: false)
            }
            self.annotation!.coordinate = coordinate
            self.location = coordinate
        }
    }
}
