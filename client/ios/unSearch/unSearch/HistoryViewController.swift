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
        populate()
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
        if (orders == nil) {
            return
        }
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
        let table = (self.view as! UITableView)
//        table.reloadData()
        table.editing = false
        if let order = AppDelegate.instance.activeProfile!.selected, let index = indexOf(order) {
            table.selectRowAtIndexPath(index, animated: false, scrollPosition: .Top)
            self.tableView(table, didSelectRowAtIndexPath: index)
        }
        AppDelegate.instance.activeProfile!.track(tracker!)
        if (navigationController != nil) {
            navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), forBarMetrics: .Default)
        }
    }
    
    override func viewDidAppear(animated: Bool) {
        AppDelegate.instance.tabs.tabBar.hidden = false
    }

    override func tableView(tableView: UITableView, canEditRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return !(indexPath.section == 0 && indexPath.row == 0 && ongoing.isEmpty)
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        switch(indexPath.section) {
        case 0 where ongoing.isEmpty && finished.isEmpty:
            let cell = tableView.dequeueReusableCellWithIdentifier("Empty", forIndexPath: indexPath)
            cell.textLabel!.text = "Нет заказов"
            cell.textLabel!.textAlignment = .Center
            cell.textLabel!.textColor = UIColor.lightGrayColor()
            return cell
        case 0 where !ongoing.isEmpty:
            let cell = tableView.dequeueReusableCellWithIdentifier("OngoingOrder", forIndexPath: indexPath) as! OngoingOrderStateCell
            cell.update(order: ongoing[indexPath.row])
            return cell
        case 1:
            let cell = tableView.dequeueReusableCellWithIdentifier("FinishedOrder", forIndexPath: indexPath) as! FinishedOrderStateCell
            cell.update(order: finished[indexPath.row])
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
    
    override func tableView(tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 38
    }
    override func tableView(tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = UIColor.lightGrayColor()
        label.font = UIFont(name: "Helvetica", size: 14)
        label.backgroundColor = UIColor.whiteColor()
        label.frame = CGRectMake(15, 0, tableView.frame.width - 15, 38)
        let view = UIView()
        view.addSubview(label)
        switch(section) {
        case 0:
            label.text = "ТЕКУЩИЕ ЗАКАЗЫ"
        case 1:
            label.text = "ВЫПОЛНЕНО"
        default:
            label.text = ""
        }
        view.frame = CGRectMake(0, 0, tableView.frame.width, 38)
        view.backgroundColor = UIColor.whiteColor()
        return view
    }
    
    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return 2
    }

    private var models: [String:ChatModel] = [:]
    
    func model(order: ExpLeagueOrder) -> ChatModel {
        var model = models[order.jid.user]
        if (model == nil) {
            model = ChatModel(order: order)
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
        let messagesView = OrderDetailsViewController(data: model(o))
        splitViewController!.showDetailViewController(messagesView, sender: nil)
    }
    
    override func tableView(tableView: UITableView, canMoveRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }

    override func tableView(tableView: UITableView, commitEditingStyle editingStyle: UITableViewCellEditingStyle, forRowAtIndexPath indexPath: NSIndexPath) {
        if (editingStyle == .Delete) {
            if (indexPath.section == 0) {
                let order = ongoing.removeAtIndex(indexPath.row)
                tableView.beginUpdates()
                if (ongoing.isEmpty) {
                    tableView.reloadRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
                }
                else {
                    tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
                }
                tableView.endUpdates()
                order.archive()
            }
            else if (indexPath.section == 1) {
                let order = finished.removeAtIndex(indexPath.row)
                tableView.beginUpdates()
                tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
                tableView.endUpdates()
                order.archive()
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
        return AppDelegate.instance.activeProfile?.selected
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
            let mvc = OrderDetailsViewController(data: model(selected!))
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
}

class OrderBadge: UITableViewCell {
    @IBOutlet weak var unreadBadge: UILabel!
    @IBOutlet weak var contentTypeIcon: UIImageView!
    @IBOutlet weak var title: UILabel!
    
    override func awakeFromNib() {
        unreadBadge.layer.cornerRadius = unreadBadge.frame.height / 2
        unreadBadge.clipsToBounds = true
    }
    
    func update(order o: ExpLeagueOrder) {
        o.badge = self
        contentTypeIcon.image = o.typeIcon
        title.text = o.text
        let unread = o.unreadCount
        if (unread > 0) {
            unreadBadge.hidden = false
            unreadBadge.text = "\(unread)"
        }
        else {
            unreadBadge.hidden = true
        }
    }
}

class OngoingOrderStateCell: OrderBadge {
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var date: UILabel!
    
    override func update(order o: ExpLeagueOrder) {
        super.update(order: o)
        if (o.count > 0 && o.message(o.count - 1).type == .Answer) {
            status.textColor = Palette.OK
            status.text = "ОТВЕТ ГОТОВ"
        }
        else if (o.status == .Overtime) {
            let formatter = NSDateFormatter()
            formatter.timeStyle = .ShortStyle
            formatter.timeZone = NSTimeZone(forSecondsFromGMT: 0)
            formatter.dateFormat = "H'ч 'mm'м'"
            
            status.textColor = Palette.ERROR
            status.text = "ПРОСРОЧЕН НА \(formatter.stringFromDate(NSDate(timeIntervalSince1970: -o.timeLeft)))"
        }
        else if (o.status == .ExpertSearch) {
            status.textColor = Palette.COMMENT
            status.text = "ИЩЕМ ЭКСПЕРТА"
        }
        else if (o.status == .Open) {
            status.textColor = Palette.COMMENT
            status.text = "В РАБОТЕ: \(o.expert!.name)"
        }
        let formatter = NSDateFormatter()
        formatter.dateStyle = .ShortStyle;
        formatter.timeStyle = .ShortStyle;
        
        formatter.doesRelativeDateFormatting = true
        date.text = formatter.stringFromDate(NSDate(timeIntervalSinceReferenceDate: o.started))
    }
}

class FinishedOrderStateCell: OrderBadge {
    @IBOutlet weak var shortAnswer: UILabel!
    @IBOutlet weak var date: UILabel!
    
    override func update(order o: ExpLeagueOrder) {
        super.update(order: o)
        shortAnswer.text = (o.status == .Canceled) ? "ОТМЕНЕН" : o.shortAnswer
        let formatter = NSDateFormatter()
        formatter.dateStyle = .ShortStyle;
        formatter.timeStyle = .ShortStyle;
        formatter.doesRelativeDateFormatting = true
        
        date.text = formatter.stringFromDate(NSDate(timeIntervalSinceReferenceDate: o.started))
    }
}
