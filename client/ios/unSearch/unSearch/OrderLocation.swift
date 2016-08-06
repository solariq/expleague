//
//  OrderLocationType.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 28.05.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import MapKit

enum OrderLocationType {
    case NoLocation
    case CurrentLocation
    case CustomLocation
}

class OrderLocation {
    var locationType: OrderLocationType!
    var location: CLLocationCoordinate2D?
    
    init() {
        self.locationType = .NoLocation
    }
    
    init(location: CLLocationCoordinate2D!) {
        setLocation(location)
    }

    func setCurrentLocation(locationProvider: LocationProvider) {
        self.locationType = .CurrentLocation
        self.location = locationProvider.deviceLocation
    }
    
    func setLocation(location: CLLocationCoordinate2D!) {
        self.locationType = .CustomLocation
        self.location = location
    }
    
    func clearLocation() {
        locationType = .NoLocation
        location = nil
    }
    
    func getLocation() -> CLLocationCoordinate2D? {
        return self.location
    }
    
    func isLocalOrder() -> Bool {
        return self.locationType != .NoLocation
    }
}
