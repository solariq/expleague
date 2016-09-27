//
//  LocationProvider.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 28.05.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import CoreData
import MapKit

class LocationProvider: NSManagedObject, CLLocationManagerDelegate {
    let locationManager = CLLocationManager()
    var deviceLocation: CLLocationCoordinate2D?
    
    func setUpLocationProvider() {
        locationManager.requestWhenInUseAuthorization()
        if CLLocationManager.locationServicesEnabled() {
            locationManager.delegate = self
            locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            locationManager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        self.deviceLocation = manager.location?.coordinate
    }
}
