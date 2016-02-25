//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import JSCustomBadge
import XMPPFramework

class HistoryViewController: UITableViewController {
    var ongoing: [ExpLeagueOrder] = []
    var finished: [ExpLeagueOrder] = []
    var archived: [ExpLeagueOrder] = []
    var tracker: XMPPTracker?
    var cellHeight = CGFloat(0.0)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        let cell = (view as! UITableView).dequeueReusableCellWithIdentifier("OngoingOrder") as! OngoingOrderStateCell
        cellHeight = cell.frame.height
        AppDelegate.instance.historyView = self
        AppDelegate.instance.split.delegate = self
        self.navigationItem.rightBarButtonItem = self.editButtonItem()
        tracker = XMPPTracker(onMessage: {(message: XMPPMessage) -> Void in
            if (message.from() != AppDelegate.instance.activeProfile!.jid) {
                self.populate()
                (self.view as! UITableView).reloadData()
            }
        })
        (view as! UITableView).registerClass(UITableViewCell.self, forCellReuseIdentifier: "Empty")
    }
    
    func populate() {
        ongoing.removeAll()
        finished.removeAll()
        archived.removeAll()
        let orders = AppDelegate.instance.activeProfile?.orders
        for orderO in orders! {
            let order = orderO as! ExpLeagueOrder
            if (order.isActive) {
                ongoing.append(order)
            }
            else if (order.status == .Archived){
                archived.append(order)
            }
            else {
                finished.append(order)
            }
        }
        
        ongoing.sortInPlace(comparator)
        finished.sortInPlace(comparator)
        archived.sortInPlace(comparator)
    }
    
    func indexOf(order: ExpLeagueOrder) -> NSIndexPath? {
        if let index = ongoing.indexOf(order) {
            return NSIndexPath(forRow: index, inSection: 0)
        }
        else if let index = finished.indexOf(order) {
            return NSIndexPath(forRow: index, inSection: 1)
        }
        return nil
    }
    
    let comparator = {(lhs: ExpLeagueOrder, rhs: ExpLeagueOrder) -> Bool in
        return lhs.started > rhs.started ? true : false;
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        populate()
        let table = (self.view as! UITableView)
        table.reloadData()
        table.editing = false
        if let order = AppDelegate.instance.activeProfile!.selected, let index = indexOf(order) {
            table.selectRowAtIndexPath(index, animated: false, scrollPosition: .Top)
            self.tableView(table, didSelectRowAtIndexPath: index)
        }
        AppDelegate.instance.tabs.tabBar.hidden = false
        AppDelegate.instance.activeProfile!.track(tracker!)
    }

    override func tableView(tableView: UITableView, canEditRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return !(indexPath.section == 0 && indexPath.row == 0 && ongoing.isEmpty)
    }
    
    override func tableView(tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch(section) {
        case 0:
            return "ТЕКУЩИЕ"
        case 1:
            return "ВЫПОЛНЕННЫЕ"
        default:
            return nil
        }
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        switch(indexPath.section) {
        case 0 where ongoing.isEmpty:
            let cell = tableView.dequeueReusableCellWithIdentifier("Empty", forIndexPath: indexPath)
            cell.textLabel!.text = "Нет заказов"
            cell.textLabel!.textAlignment = .Center
            cell.textLabel!.textColor = UIColor.lightGrayColor()
            return cell
        case 0 where !ongoing.isEmpty:
            let o = ongoing[indexPath.row]
            let cell = tableView.dequeueReusableCellWithIdentifier("OngoingOrder", forIndexPath: indexPath) as! OngoingOrderStateCell
            cell.title.text = o.text
            if (o.count > 0 && o.message(o.count - 1).type == .Answer) {
                cell.status.textColor = OngoingOrderStateCell.GREEN_COLOR
                cell.status.text = "ОТВЕТ ГОТОВ"
            }
            else if (o.status == .Overtime) {
                let formatter = NSDateFormatter()
                formatter.timeStyle = .ShortStyle
                formatter.timeZone = NSTimeZone(forSecondsFromGMT: 0)
                formatter.dateFormat = "H'ч 'mm'м'"
                
                cell.status.textColor = OngoingOrderStateCell.ERROR_COLOR
                cell.status.text = "ПРОСРОЧЕН НА \(formatter.stringFromDate(NSDate(timeIntervalSince1970: -o.timeLeft)))"
            }
            else if (o.status == .ExpertSearch) {
                cell.status.textColor = OngoingOrderStateCell.OK_COLOR
                cell.status.text = "ИЩЕМ ЭКСПЕРТА"
            }
            else if (o.status == .Open) {
                cell.status.textColor = OngoingOrderStateCell.OK_COLOR
                cell.status.text = "В РАБОТЕ: \(o.expert!)"
            }
            let formatter = NSDateFormatter()
            formatter.dateStyle = .ShortStyle;
            formatter.timeStyle = .ShortStyle;
            
            formatter.doesRelativeDateFormatting = true
            cell.date.text = formatter.stringFromDate(NSDate(timeIntervalSinceReferenceDate: o.started))
            for view in cell.contentView.subviews {
                if (view is JSCustomBadge) {
                    view.removeFromSuperview()
                }
            }
            if (o.unreadCount > 0) {
                let badge = JSCustomBadge(string: "\(o.unreadCount)")
                let size = badge.frame.size
                badge.frame = CGRectMake(cell.frame.maxX - size.width - 4, 4, size.width, size.height)
                cell.contentView.addSubview(badge)
            }
            return cell
        case 1:
            let o = finished[indexPath.row]
            let cell = tableView.dequeueReusableCellWithIdentifier("FinishedOrder", forIndexPath: indexPath) as! FinishedOrderStateCell
            cell.title.text = o.text
            cell.shortAnswer.text = (o.status == .Canceled) ? "ОТМЕНЕН" : o.shortAnswer
            let formatter = NSDateFormatter()
            formatter.dateStyle = .ShortStyle;
            formatter.timeStyle = .ShortStyle;
            formatter.doesRelativeDateFormatting = true
            
            cell.date.text = formatter.stringFromDate(NSDate(timeIntervalSinceReferenceDate: o.started))
            return cell
        default:
            return UITableViewCell()
        }
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return cellHeight;
    }
    
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch(section) {
        case 0:
            return max(ongoing.count, 1)
        case 1:
            return finished.count
        default:
            return 0
        }
    }
    
    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return 2
    }

    private var models: [String: ChatMessagesModel] = [:]
    
    func model(order: ExpLeagueOrder) -> ChatMessagesModel{
        var model = models[order.jid.user]
        if (model == nil) {
            model = ChatMessagesModel(order: order)
            models[order.jid.user] = model
        }
        return model!
    }
    
    override func tableView(tableView: UITableView, didSelectRowAtIndexPath indexPath: NSIndexPath) {
        let o: ExpLeagueOrder
        switch(indexPath.section) {
        case 0 where ongoing.isEmpty:
            return;
        case 0 where !ongoing.isEmpty:
            o = ongoing[indexPath.row]
        case 1:
            o = finished[indexPath.row]
        default:
            return
        }
        AppDelegate.instance.tabs.tabBar.hidden = true;

        AppDelegate.instance.activeProfile!.selected = o
        let messagesView = MessagesVeiwController()
        messagesView.data = model(o)
        splitViewController!.showDetailViewController(messagesView, sender: nil)
    }
    
    override func tableView(tableView: UITableView, willDisplayHeaderView view: UIView, forSection section: Int) {
        (view as! UITableViewHeaderFooterView).textLabel!.textColor = UIColor.lightGrayColor()
    }

    override func tableView(tableView: UITableView, canMoveRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }

    override func tableView(tableView: UITableView, commitEditingStyle editingStyle: UITableViewCellEditingStyle, forRowAtIndexPath indexPath: NSIndexPath) {
        if (editingStyle == .Delete) {
            if (indexPath.section == 0) {
                ongoing.removeAtIndex(indexPath.row).archive()
                if (ongoing.isEmpty) {
                    tableView.reloadRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
                }
                else {
                    tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
                }
            }
            else if (indexPath.section == 1) {
                finished.removeAtIndex(indexPath.row).archive()
                tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
            }
        }
    }
    
    override func tableView(tableView: UITableView, shouldHighlightRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        if (indexPath.section == 0 && indexPath.row == 0) {
            return !ongoing.isEmpty
        }
        return true
    }
}

extension HistoryViewController: UISplitViewControllerDelegate {
    var selected: ExpLeagueOrder? {
        return AppDelegate.instance.activeProfile!.selected
    }
    
    func primaryViewControllerForCollapsingSplitViewController(splitViewController: UISplitViewController) -> UIViewController? {
        if (selected == nil) {
            AppDelegate.instance.tabs.tabBar.hidden = false
            return navigationController ?? self
        }
        else {
            if (navigationController != nil) {
                return navigationController
            }
            let mvc = MessagesVeiwController()
            mvc.data = model(selected!)
            return mvc
        }
    }

    func primaryViewControllerForExpandingSplitViewController(splitViewController: UISplitViewController) -> UIViewController? {
        return primaryViewControllerForCollapsingSplitViewController(splitViewController)
    }
    
    func splitViewController(svc: UISplitViewController, willChangeToDisplayMode displayMode: UISplitViewControllerDisplayMode) {
        if (displayMode != .AllVisible) {
            AppDelegate.instance.tabs.tabBar.hidden = false
        }
    }

    func splitViewController(splitViewController: UISplitViewController, showDetailViewController vc: UIViewController, sender: AnyObject?) -> Bool {
        let mvc = vc as! MessagesVeiwController
        if (selected != nil) {
            mvc.data = model(selected!)
        }
        return false
    }
//
//    func splitViewController(splitViewController: UISplitViewController, showViewController vc: UIViewController, sender: AnyObject?) -> Bool {
//        return true
//    }
    
    func splitViewController(splitViewController: UISplitViewController, collapseSecondaryViewController secondaryViewController: UIViewController, ontoPrimaryViewController primaryViewController: UIViewController) -> Bool {
        let mvc = secondaryViewController as! MessagesVeiwController
        if (selected != nil) {
            mvc.data = model(selected!)
            return false
        }
        return true
    }
}

class OngoingOrderStateCell: UITableViewCell {
    static let OK_COLOR = UIColor(red: 17.0/256, green: 138.0/256, blue: 222.0/256, alpha: 1.0)
    static let ERROR_COLOR = UIColor(red: 194.0/256, green: 60.0/256, blue: 60.0/256, alpha: 1.0)
    static let GREEN_COLOR = UIColor(red: 132.0/256, green: 194.0/256, blue: 11.0/256, alpha: 1.0)
    @IBOutlet weak var contentTypeIcon: UIImageView!
    @IBOutlet weak var title: UILabel!
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var date: UILabel!
}

class FinishedOrderStateCell: UITableViewCell {
    @IBOutlet weak var contentTypeIcon: UIImageView!
    @IBOutlet weak var title: UILabel!
    @IBOutlet weak var shortAnswer: UILabel!
    @IBOutlet weak var date: UILabel!
}
