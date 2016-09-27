//
//  ChooseLocationController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 18.07.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import GoogleMaps

class ChooseLocationController: UIViewController {
    let locationManager = CLLocationManager()
    var currentLocation: CLLocationCoordinate2D?
    var deviceLocation: CLLocationCoordinate2D?
    let session = URLSession(configuration: URLSessionConfiguration.default)
    var placesTask: URLSessionTask?
    var placesLocation: CLLocationCoordinate2D?
    
    @IBOutlet weak var mapView: GMSMapView!
    @IBOutlet weak var placesTable: UITableView!
    @IBAction func choose(_ sender: AnyObject) {
    }
    override func viewDidLoad() {
        automaticallyAdjustsScrollViewInsets = true
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), for: .default)
        navigationController!.isNavigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.white]
        navigationItem.title = "Укажите местоположение"
        
        let button = UIBarButtonItem(barButtonSystemItem: .search, target: self, action: #selector(search))
        button.tintColor = UIColor.white
        navigationItem.setRightBarButton(button, animated: false)
        automaticallyAdjustsScrollViewInsets = true
        locationManager.requestWhenInUseAuthorization()
        mapView.delegate = self
        edgesForExtendedLayout = UIRectEdge()
    }
    
    func search() {
        
    }
    
    func visitPlaces(location position: CLLocationCoordinate2D, text: String?, callback: @escaping (Any) -> ()) {
        var urlString = "key=\(AppDelegate.GOOGLE_API_KEY)&location=\(position.latitude),\(position.longitude)&radius=\(1000)&rankby=prominence&sensor=true"
        if (text != nil) {
            urlString += "&name=\(text)"
        }
        
        urlString = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?"
            + urlString.addingPercentEncoding(withAllowedCharacters: .urlHostAllowed)!
        print(urlString)
        if let task = placesTask , task.state == .running {
            task.cancel()
        }
        UIApplication.shared.isNetworkActivityIndicatorVisible = true
        placesTask = session.dataTask(with: URL(string: urlString)!, completionHandler: {data, response, error in
            guard data != nil else {
                return
            }
            print("inside.")
            UIApplication.shared.isNetworkActivityIndicatorVisible = false
            if let json = try! JSONSerialization.jsonObject(with: data!, options: []) as? NSDictionary {
                if let results = json["results"] as? NSArray {
                    for rawPlace: Any in results {
                        callback(rawPlace)
                    }
                }
            }
        }) 
        placesTask?.resume()
    }
    
    init() {
        super.init(nibName: "ChooseLocationView", bundle: nil)
        locationManager.delegate = self
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

extension ChooseLocationController: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedWhenInUse {
            locationManager.startUpdatingLocation()
//            mapView.myLocationEnabled = true
            mapView.settings.myLocationButton = true
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.first {
            deviceLocation = location.coordinate
            if currentLocation == nil {
                mapView.camera = GMSCameraPosition(target: location.coordinate, zoom: 15, bearing: 0, viewingAngle: 0)
                currentLocation = location.coordinate
            }
        }
        
    }
}

extension ChooseLocationController: GMSMapViewDelegate {
    func didTapMyLocationButton(for mapView: GMSMapView) -> Bool {
        if deviceLocation != nil {
            mapView.animate(toLocation: deviceLocation!)
            return true
        }
        return false
    }
    
    func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
        let point1 = MKMapPointForCoordinate(placesLocation ?? CLLocationCoordinate2DMake(0, 0))
        let point2 = MKMapPointForCoordinate(position.target)
        let distance = MKMetersBetweenMapPoints(point1, point2)
        if distance > 1000 {
            placesLocation = position.target
            visitPlaces(location: position.target, text: nil) { place in
                print(place)
            }
        }
    }
}
