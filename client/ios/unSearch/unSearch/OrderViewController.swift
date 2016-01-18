//
// Created by Игорь Кураленок on 11.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

class OrderViewController: UIViewController {
    @IBOutlet weak var orderDescription: UIView!
    @IBAction func fire(sender: AnyObject) {
        let controller = self.childViewControllers[0] as! OrderDescriptionViewController;
        if (controller.orderText.text.isEmpty) {
            controller.orderTextBackground.backgroundColor = controller.error_color
            return
        }
        if (!AppDelegate.instance.stream.isConnected()) {
            let alertView = UIAlertController(title: "Experts League", message: "Connecting to server.\n\n", preferredStyle: .Alert)
            let completion = {
                //  Add your progressbar after alert is shown (and measured)
                let progressController = AppDelegate.instance.connectionProgressView
                let rect = CGRectMake(0, 54.0, alertView.view.frame.width, 50)
                progressController.completion = {
                    self.fire(self)
                }
                progressController.view.frame = rect
                progressController.view.backgroundColor = alertView.view.backgroundColor
                alertView.view.addSubview(progressController.view)
                progressController.alert = alertView
                AppDelegate.instance.connect()
                //                progressController.alert = alertView
            }
            alertView.addAction(UIAlertAction(title: "Retry", style: .Default, handler: {(x: UIAlertAction) -> Void in
                AppDelegate.instance.disconnect()
                self.fire(self)
            }))
            alertView.addAction(UIAlertAction(title: "Cancel", style: .Cancel, handler: nil))
            
            //  Show it to your users
    
            presentViewController(alertView, animated: true, completion: completion)
            return
        }
        
        let order = AppDelegate.instance.activeProfile!.placeOrder(
                topic: controller.orderText.text,
                urgency: Urgency.find(controller.urgency.value).type,
                local: controller.isLocal.on,
                prof: controller.needExpert.on
        );
        controller.clear();

        let delegate = UIApplication.sharedApplication().delegate as! AppDelegate

        if delegate.navigation.viewControllers.count > 0 {
            while (delegate.navigation.viewControllers.count > 1) {
                delegate.navigation.popViewControllerAnimated(false)
            }
            delegate.messagesView!.order = order
            delegate.navigation.pushViewController(delegate.messagesView!, animated: false)
        }
        delegate.tabs.selectedIndex = 1
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        self.childViewControllers[0]
    }
}

class OrderDescriptionViewController: UITableViewController {
    let error_color = UIColor(red: 1.0, green: 0.0, blue: 0.0, alpha: 0.1)
    let rowHeight = 44;
    @IBOutlet weak var isLocal: UISwitch!
    @IBOutlet weak var needExpert: UISwitch!
    @IBOutlet weak var urgency: UISlider!
    @IBOutlet weak var urgencyLabel: UILabel!
    @IBAction func urgencyChanged(sender: UISlider) {
        let type = Urgency.find(sender.value);
        urgencyLabel.text = type.caption
        sender.value = type.value
    }

    @IBOutlet weak var orderText: UITextView!
    @IBOutlet weak var orderTextBackground: UIView!

    var orderTextBGColor: UIColor?
    override func viewDidLoad() {
        super.viewDidLoad()
        orderTextBGColor = orderTextBackground.backgroundColor
        urgencyChanged(urgency)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: "dismissKeyboard"))
    }
    
    func dismissKeyboard() {
        orderTextBackground.backgroundColor = orderText.text.isEmpty ? error_color : orderTextBGColor
        
        view.endEditing(true)
    }
    
    internal func clear() {
        isLocal.on = false
        needExpert.on = false
        urgency.value = Urgency.DURING_THE_DAY.value
        orderText.text = ""
    }

    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        if (indexPath.item == 0 && indexPath.section == 0) {
            let sectionsHeight = 28 * 2 * 2;
            return max(CGFloat(50), view.frame.height - CGFloat(5 * rowHeight + sectionsHeight));
        }
        if (indexPath.item == 0 && indexPath.section == 1) {
            return 64
        }
        return CGFloat(rowHeight);
    }
}

struct Urgency {
    var caption: String;
    var value: Float;
    var type: String;

    static let ASAP = Urgency(caption: "срочно", value: 1.0, type: "asap")
    static let DURING_THE_DAY = Urgency(caption: "в течение дня", value: 0.5, type: "day")
    static let DURING_THE_WEEK = Urgency(caption: "в течение недели", value: 0.0, type: "week")

    static func find(value: Float) -> Urgency {
        if (value < 0.25) {
            return DURING_THE_WEEK
        }
        else if value < 0.75 {
            return DURING_THE_DAY
        }
        return ASAP
    }
}
