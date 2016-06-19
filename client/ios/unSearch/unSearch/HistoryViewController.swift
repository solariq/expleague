//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework

enum HistorySection {
    case Ongoing, AnswerOfTheWeek, Finished, None
}

class HistoryViewController: UITableViewController {
    var ongoing: [ExpLeagueOrder] = []
    var finished: [ExpLeagueOrder] = []
    var archived: [ExpLeagueOrder] = []
    var answerOfTheWeek: ExpLeagueOrder?
    var cellHeight = CGFloat(0.0)
    var selected: ExpLeagueOrder? {
        willSet(selected) {
            guard selected != self.selected else {
                return
            }
            let table = (self.view as! UITableView)
            if (table.indexPathForSelectedRow != nil) {
                table.deselectRowAtIndexPath(table.indexPathForSelectedRow!, animated: false)
            }
            if let sel = selected, let path = indexOf(sel) {
                table.selectRowAtIndexPath(path, animated: false, scrollPosition: .Top)
                tableView(table, didSelectRowAtIndexPath: path)
            }
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        let table = view as! UITableView
        let cell = table.dequeueReusableCellWithIdentifier("OngoingOrder") as! OngoingOrderStateCell
        table.registerClass(UITableViewCell.self, forCellReuseIdentifier: "Empty")
        cellHeight = cell.frame.height
        AppDelegate.instance.historyView = self
        self.navigationItem.rightBarButtonItem = self.editButtonItem()
        self.navigationController?.navigationBar.tintColor = UIColor.whiteColor()
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.whiteColor()
        ]

        QObject.connect(AppDelegate.instance, signal: #selector(AppDelegate.activate(_:)), receiver: self, slot: #selector(self.populate))
        populate()
    }
    
    deinit {
        QObject.disconnect(self)
    }
    
    func populate() {
        ongoing.removeAll()
        finished.removeAll()
        archived.removeAll()
        answerOfTheWeek = nil
        let orders = AppDelegate.instance.activeProfile?.orders ?? []
        for orderO in orders {
            let order = orderO as! ExpLeagueOrder
            if (order.isActive) {
                ongoing.append(order)
            }
            else if (order.status == .Archived){
                archived.append(order)
            }
            else if (order.fake) {
                answerOfTheWeek = order
            }
            else {
                finished.append(order)
            }
        }
//        print("Populate results: \(ongoing.count)/\(finished.count)/\(archived.count)")
        ongoing.sortInPlace(comparator)
        finished.sortInPlace(comparator)
        archived.sortInPlace(comparator)
        (view as! UITableView).reloadData()
    }
    
    func indexOf(order: ExpLeagueOrder) -> NSIndexPath? {
        var section: Int?
        var row: Int?
        if let index = ongoing.indexOf(order) where index >= 0 {
            row = index
            section = 0
        }
        else if order == answerOfTheWeek {
            row = 0
            section = ongoing.isEmpty ? 0 : 1
        }
        else if let index = finished.indexOf(order) where index >= 0 {
            row = index
            section = (ongoing.isEmpty ? 0 : 1) + (answerOfTheWeek == nil ? 0 : 1)
        }
        return row != nil ? NSIndexPath(forRow: row!, inSection: section!) : nil
    }
    
    let comparator = {(lhs: ExpLeagueOrder, rhs: ExpLeagueOrder) -> Bool in
        return lhs.started > rhs.started ? true : false;
    }
    
    override func viewWillAppear(animated: Bool) {
        super.viewWillAppear(animated)
        let table = (self.view as! UITableView)
        table.editing = false
        if (navigationController != nil) {
            navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), forBarMetrics: .Default)
        }
    }
    
    override func viewDidAppear(animated: Bool) {
        AppDelegate.instance.tabs.tabBar.hidden = false
    }
    
    override func viewWillDisappear(animated: Bool) {
        self.setEditing(false, animated: false)
    }
    
    private func section(index i: Int) -> HistorySection {
        var index = i
        var result: HistorySection = .None
        if (index >= 0 && !ongoing.isEmpty) {
            index -= 1
            result = .Ongoing
        }
        if (index >= 0 && answerOfTheWeek != nil) {
            index -= 1
            result = .AnswerOfTheWeek
        }
        if (index >= 0 && !finished.isEmpty) {
            index -= 1
            result = .Finished
        }
        return result
    }
    
    override func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        var result = 0
        result += ongoing.isEmpty ? 0 : 1
        result += finished.isEmpty ? 0 : 1
        result += answerOfTheWeek == nil ? 0 : 1
        return max(result, 1)
    }
    
    override func tableView(tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = UIColor.lightGrayColor()
        label.font = UIFont(name: "Helvetica", size: 14)
        label.backgroundColor = UIColor.whiteColor()
        label.frame = CGRectMake(15, 0, tableView.frame.width - 15, 38)
        let view = UIView()
        view.addSubview(label)
        switch(self.section(index: section)) {
        case .None, .Ongoing:
            label.text = "ТЕКУЩИЕ"
        case .Finished:
            label.text = "ВЫПОЛНЕНО"
        case .AnswerOfTheWeek:
            label.text = "ОТВЕТ НЕДЕЛИ"
        }
        view.frame = CGRectMake(0, 0, tableView.frame.width, 38)
        view.backgroundColor = UIColor.whiteColor()
        return view
    }
    
    override func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let cell: UITableViewCell
        switch(section(index: indexPath.section)) {
        case .None:
            cell = tableView.dequeueReusableCellWithIdentifier("Empty", forIndexPath: indexPath)
            cell.textLabel!.text = "Нет заказов"
            cell.textLabel!.textAlignment = .Center
            cell.textLabel!.textColor = UIColor.lightGrayColor()
        case .Ongoing:
            let ocell = tableView.dequeueReusableCellWithIdentifier("OngoingOrder", forIndexPath: indexPath) as! OngoingOrderStateCell
            ocell.setup(order: ongoing[indexPath.row])
            cell = ocell
        case .AnswerOfTheWeek:
            let ocell = tableView.dequeueReusableCellWithIdentifier("FinishedOrder", forIndexPath: indexPath) as! FinishedOrderStateCell
            ocell.setup(order: answerOfTheWeek!)
            cell = ocell
        case .Finished:
            let ocell = tableView.dequeueReusableCellWithIdentifier("FinishedOrder", forIndexPath: indexPath) as! FinishedOrderStateCell
            ocell.setup(order: finished[indexPath.row])
            cell = ocell
        }
        return cell
    }
    
    override func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        return cellHeight;
    }
    
    override func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch(self.section(index: section)) {
        case .None, .AnswerOfTheWeek:
            return 1
        case .Ongoing:
            return ongoing.count
        case .Finished:
            return finished.count
        }
    }
    
    override func tableView(tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 38
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
        switch(section(index: indexPath.section)) {
        case .None:
            return;
            
        case .AnswerOfTheWeek:
            o = answerOfTheWeek!
        case .Ongoing:
            o = ongoing[indexPath.row]
        case .Finished:
            o = finished[indexPath.row]
        }
        AppDelegate.instance.tabs.tabBar.hidden = true;
        let messagesView = OrderDetailsViewController(data: model(o))
        if (splitViewController!.collapsed) {
            navigationController!.popToRootViewControllerAnimated(true);
            navigationController!.pushViewController(messagesView, animated: true)
        }
        else {
            splitViewController!.showDetailViewController(messagesView, sender: self)
        }
    }
    
    override func tableView(tableView: UITableView, canMoveRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }

    override func tableView(tableView: UITableView, shouldHighlightRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return section(index: indexPath.section) != .None
    }
    
    override func tableView(tableView: UITableView, canEditRowAtIndexPath indexPath: NSIndexPath) -> Bool {
        return section(index: indexPath.section) != .None
    }

    override func tableView(tableView: UITableView, commitEditingStyle editingStyle: UITableViewCellEditingStyle, forRowAtIndexPath indexPath: NSIndexPath) {
        if (editingStyle == .Delete) {
            let order: ExpLeagueOrder
            let sections = numberOfSectionsInTableView(tableView)
            let empty: Bool
            switch section(index: indexPath.section) {
            case .None:
                return
            case .AnswerOfTheWeek:
                order = answerOfTheWeek!
                empty = true
                answerOfTheWeek = nil
            case .Ongoing:
                order = ongoing.removeAtIndex(indexPath.row)
                empty = ongoing.isEmpty
                let alertView: UIAlertController
                if (order.messages.last?.type == .Answer) {
                    alertView = UIAlertController(title: "unSearch", message: "Вы уверены, что хотите отменить задание?", preferredStyle: .Alert)
                }
                else {
                    alertView = UIAlertController(title: "unSearch", message: "Вы не поставили оценку. Действительно оставить эксперта без оценки?", preferredStyle: .Alert)
                }
                
                alertView.addAction(UIAlertAction(title: "Да", style: .Default, handler: {(x: UIAlertAction) -> Void in
                    self.delete(indexPath, sections: sections, empty: empty)
                    order.archive()
                }))
                alertView.addAction(UIAlertAction(title: "Нет", style: .Cancel, handler: nil))
                presentViewController(alertView, animated: true, completion: nil)
                return
            case .Finished:
                order = finished.removeAtIndex(indexPath.row)
                empty = finished.isEmpty
            }
            delete(indexPath, sections: sections, empty: empty)
            order.archive()
        }
    }
    
    private func delete(indexPath: NSIndexPath, sections: Int, empty: Bool) {
        tableView.beginUpdates()
        if (empty) {
            if (sections == 1) {
                tableView.reloadSections(NSIndexSet(index: indexPath.section), withRowAnimation: .Fade)
            }
            else {
                tableView.deleteSections(NSIndexSet(index: indexPath.section), withRowAnimation: .Fade)
            }
        }
        if (sections == 1 && empty) {
            tableView.reloadRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
        }
        else {
            tableView.deleteRowsAtIndexPaths([indexPath], withRowAnimation: .Fade)
        }
        
        tableView.endUpdates()
        
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
    
    func setup(order o: ExpLeagueOrder) {
        QObject.connect(o, signal: #selector(ExpLeagueOrder.notify), receiver: self, slot: #selector(OrderBadge.invalidate(_:)))
        update(order: o)
    }

    func invalidate(notification: NSNotification) {
        if let order = notification.object as? ExpLeagueOrder {
            dispatch_async(dispatch_get_main_queue()) {
                self.update(order: order)
            }
        }
    }
    
    func update(order o: ExpLeagueOrder) {
        
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
        if (o.messages.last?.type == .Answer) {
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
            status.text = "В РАБОТЕ: \(o.activeExpert?.name ?? "")"
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
