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
        let conn = ELConnection.instance
        if (controller.orderText.text.isEmpty) {
            controller.orderTextBackground.backgroundColor = controller.error_color
        }
        else if (!conn.isConnected()) {
            let controller = UIAlertController(
            title: "Experts League",
                    message: "No connection to the server: \(conn.settings.host())!",
                    preferredStyle: UIAlertControllerStyle.Alert)
            controller.addAction(UIAlertAction(title: "Dismiss", style: UIAlertActionStyle.Default, handler: nil))
            presentViewController(controller, animated: true, completion: nil)
        } else {
            let order = conn.placeOrder(
                    topic: controller.orderText.text,
                    urgency: Urgency.find(controller.urgency.value).type,
                    local: controller.isLocal.on,
                    prof: controller.needExpert.on
            );
            controller.clear();
            let delegate = UIApplication.sharedApplication().delegate as! AppDelegate
            delegate.showOrder(order)
        }
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

    static let ASAP = Urgency(caption: "срочно", value: 1.0, type: "urgent")
    static let DURING_THE_DAY = Urgency(caption: "в течении дня", value: 0.5, type: "day")
    static let DURING_THE_WEEK = Urgency(caption: "в течении недели", value: 0.0, type: "week")

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
