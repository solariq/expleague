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

    func setCurrentLocation(_ locationProvider: LocationProvider) {
        self.locationType = .currentLocation
        self.location = locationProvider.deviceLocation
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
