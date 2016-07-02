//
//  OrderAttachments.swift
//  unSearch
//
//  Created by Vitaly Pimenov on 25.06.16.
//  Copyright © 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class OrderAttachmentsController: UIViewController {
    init() {
        super.init(nibName: "OrderAttachmentsView", bundle: nil)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationController!.navigationBar.setBackgroundImage(UIImage(named: "experts_background"), forBarMetrics: .Default)
        navigationController!.navigationBarHidden = false
        navigationController!.navigationBar.titleTextAttributes = [NSForegroundColorAttributeName : UIColor.whiteColor()]
        navigationItem.title = "Приложения к запросу"
        let button = UIBarButtonItem(title: "Готово", style: .Done, target: self, action: #selector(OrderAttachmentsController.close))
        button.tintColor = UIColor.whiteColor()
        navigationItem.setRightBarButtonItem(button, animated: false)
    }
    
    func close() {
        self.dismissViewControllerAnimated(true, completion: nil)
    }
}