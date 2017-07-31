//
// Created by Igor Kuralenok on 12.01.16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import XMPPFramework
import FBSDKCoreKit

import unSearchCore

enum HistorySection {
    case ongoing, answerOfTheWeek, finished, none
}

class HistoryViewController: UITableViewController {
    var ongoing: [ExpLeagueOrder] = []
    var finished: [ExpLeagueOrder] = []
    var archived: [ExpLeagueOrder] = []
    var answerOfTheWeek: ExpLeagueOrder?
    var cellHeight = CGFloat(0.0)
    var selected: ExpLeagueOrder? {
        willSet(selected) {
            guard selected != self.selected && isViewLoaded && selected != nil else {
                return
            }
            let table = (self.view as! UITableView)
            if (table.indexPathForSelectedRow != nil) {
                table.deselectRow(at: table.indexPathForSelectedRow!, animated: false)
            }
            navigationController?.popToViewController(self, animated: false)
            if let sel = selected, let path = indexOf(sel) {
                table.selectRow(at: path, animated: false, scrollPosition: .top)
                self.performSegue(withIdentifier: "orderDetails", sender: self)
            }
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        let table = view as! UITableView
        let cell = table.dequeueReusableCell(withIdentifier: "OngoingOrder") as! OngoingOrderStateCell
        table.register(UITableViewCell.self, forCellReuseIdentifier: "Empty")
        table.allowsSelection = true
        table.allowsMultipleSelection = false
        cellHeight = cell.frame.height
        AppDelegate.instance.historyView = self
        self.navigationItem.rightBarButtonItem = self.editButtonItem
        self.navigationController?.navigationBar.tintColor = UIColor.white
        self.navigationController?.navigationBar.titleTextAttributes = [
            NSForegroundColorAttributeName: UIColor.white
        ]

        QObject.connect(DataController.shared(), signal: #selector(DataController.profileChanged), receiver: self, slot: #selector(self.onProfileChanged))
        QObject.connect(ExpLeagueProfile.active, signal: #selector(ExpLeagueProfile.ordersChanged), receiver: self, slot: #selector(self.populate))
        populate()
    }
    func onProfileChanged() {
        QObject.disconnect(self)
        QObject.connect(DataController.shared(), signal: #selector(DataController.profileChanged), receiver: self, slot: #selector(self.onProfileChanged))
        QObject.connect(ExpLeagueProfile.active, signal: #selector(ExpLeagueProfile.ordersChanged), receiver: self, slot: #selector(self.populate))
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
        for order in ExpLeagueProfile.active.listOrders() {
            if (order.status == .archived){
                archived.append(order)
            }
            else if (order.fake && order.status != .archived && order.status != .closed) {
                answerOfTheWeek = order
            }
            else if (order.isActive) {
                ongoing.append(order)
            }
            else {
                finished.append(order)
            }
        }
//        print("Populate results: \(ongoing.count)/\(finished.count)/\(archived.count)")
        ongoing.sort(by: comparator)
        finished.sort(by: comparator)
        archived.sort(by: comparator)
        (view as! UITableView).reloadData()
        self.selected = ExpLeagueProfile.active.selectedOrder
    }
    
    func indexOf(_ order: ExpLeagueOrder) -> IndexPath? {
        var section: Int?
        var row: Int?
        if let index = ongoing.index(of: order) , index >= 0 {
            row = index
            section = 0
        }
        else if order == answerOfTheWeek {
            row = 0
            section = ongoing.isEmpty ? 0 : 1
        }
        else if let index = finished.index(of: order) , index >= 0 {
            row = index
            section = (ongoing.isEmpty ? 0 : 1) + (answerOfTheWeek == nil ? 0 : 1)
        }
        return row != nil ? IndexPath(row: row!, section: section!) : nil
    }
    
    let comparator = {(lhs: ExpLeagueOrder, rhs: ExpLeagueOrder) -> Bool in
        return lhs.started > rhs.started ? true : false;
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let table = (self.view as! UITableView)
        table.isEditing = false
        if (navigationController != nil) {
            navigationController!.navigationBar.setBackgroundImage(UIImage(named: "history_background"), for: .default)
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        FBSDKAppEvents.logEvent("History tab active")
        if (selected == nil) {
            let table = (self.view as! UITableView)
            if (table.indexPathForSelectedRow != nil) {
                table.deselectRow(at: table.indexPathForSelectedRow!, animated: false)
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        self.setEditing(false, animated: false)
    }
    
    fileprivate func section(index i: Int) -> HistorySection {
        var index = i
        var result: HistorySection = .none
        if (index >= 0 && !ongoing.isEmpty) {
            index -= 1
            result = .ongoing
        }
        if (index >= 0 && answerOfTheWeek != nil) {
            index -= 1
            result = .answerOfTheWeek
        }
        if (index >= 0 && !finished.isEmpty) {
            index -= 1
            result = .finished
        }
        return result
    }
    
    override func numberOfSections(in tableView: UITableView) -> Int {
        var result = 0
        result += ongoing.isEmpty ? 0 : 1
        result += finished.isEmpty ? 0 : 1
        result += answerOfTheWeek == nil ? 0 : 1
        return max(result, 1)
    }
    
    override func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = UIColor.lightGray
        label.font = UIFont(name: "Helvetica", size: 14)
        label.backgroundColor = UIColor.white
        label.frame = CGRect(x: 15, y: 0, width: tableView.frame.width - 15, height: 38)
        let view = UIView()
        view.addSubview(label)
        switch(self.section(index: section)) {
        case .none, .ongoing:
            label.text = "ТЕКУЩИЕ"
        case .finished:
            label.text = "ВЫПОЛНЕНО"
        case .answerOfTheWeek:
            label.text = "ОТВЕТ НЕДЕЛИ"
        }
        view.frame = CGRect(x: 0, y: 0, width: tableView.frame.width, height: 38)
        view.backgroundColor = UIColor.white
        return view
    }
    
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell: UITableViewCell
        switch(section(index: (indexPath as NSIndexPath).section)) {
        case .none:
            cell = tableView.dequeueReusableCell(withIdentifier: "Empty", for: indexPath)
            cell.textLabel!.text = "Нет заказов"
            cell.textLabel!.textAlignment = .center
            cell.textLabel!.textColor = UIColor.lightGray
        case .ongoing:
            let ocell = tableView.dequeueReusableCell(withIdentifier: "OngoingOrder", for: indexPath) as! OngoingOrderStateCell
            ocell.setup(order: ongoing[(indexPath as NSIndexPath).row])
            cell = ocell
        case .answerOfTheWeek:
            let ocell = tableView.dequeueReusableCell(withIdentifier: "FinishedOrder", for: indexPath) as! FinishedOrderStateCell
            ocell.setup(order: answerOfTheWeek!)
            cell = ocell
        case .finished:
            let ocell = tableView.dequeueReusableCell(withIdentifier: "FinishedOrder", for: indexPath) as! FinishedOrderStateCell
            ocell.setup(order: finished[(indexPath as NSIndexPath).row])
            cell = ocell
        }
        return cell
    }
    
    override func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return cellHeight;
    }
    
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch(self.section(index: section)) {
        case .none, .answerOfTheWeek:
            return 1
        case .ongoing:
            return ongoing.count
        case .finished:
            return finished.count
        }
    }
    
    override func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return 38
    }
    
    fileprivate var models: [String:ChatModel] = [:]
    
    func model(_ order: ExpLeagueOrder) -> ChatModel {
        var model = models[order.jid.user]
        if (model == nil) {
            model = ChatModel(order: order)
            models[order.jid.user] = model
        }
        return model!
    }
    
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        let indexPath = (view as! UITableView).indexPathForSelectedRow!
        if (segue.identifier == "orderDetails") {
            let o: ExpLeagueOrder
            switch(section(index: indexPath.section)) {
            case .none:
                return;
                
            case .answerOfTheWeek:
                o = answerOfTheWeek!
            case .ongoing:
                o = ongoing[indexPath.row]
            case .finished:
                o = finished[indexPath.row]
            }

            if let destination = segue.destination as? OrderDetailsViewController {
                destination.data = model(o)
            }
        }
    }
    
    override func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool {
        return false
    }

    override func tableView(_ tableView: UITableView, shouldHighlightRowAt indexPath: IndexPath) -> Bool {
        return section(index: (indexPath as NSIndexPath).section) != .none
    }
    
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool {
        return section(index: (indexPath as NSIndexPath).section) != .none
    }

    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCellEditingStyle, forRowAt indexPath: IndexPath) {
        if (editingStyle == .delete) {
            let order: ExpLeagueOrder
            let sections = numberOfSections(in: tableView)
            let empty: Bool
            switch section(index: (indexPath as NSIndexPath).section) {
            case .none:
                return
            case .answerOfTheWeek:
                order = answerOfTheWeek!
                empty = true
                answerOfTheWeek = nil
            case .ongoing:
                order = ongoing[(indexPath as NSIndexPath).row]
                empty = ongoing.count == 1
                let alertView: UIAlertController
                if (order.judged) {
                    alertView = UIAlertController(title: "unSearch", message: "Вы уверены, что хотите отменить задание?", preferredStyle: .alert)
                    alertView.addAction(UIAlertAction(title: "Да", style: .default, handler: {(x: UIAlertAction) -> Void in
                        _ = self.ongoing.remove(at: (indexPath as NSIndexPath).row)
                        self.delete(indexPath, sections: sections, empty: empty)
                        order.archive()
                    }))
                    alertView.addAction(UIAlertAction(title: "Нет", style: .cancel, handler: nil))
                }
                else {
                    alertView = UIAlertController(title: "unSearch", message: "Сначала оцените задание", preferredStyle: .alert)
                    alertView.addAction(UIAlertAction(title: "Хорошо", style: .default, handler: nil))
                }
                
                present(alertView, animated: true, completion: nil)
                return
            case .finished:
                order = finished.remove(at: (indexPath as NSIndexPath).row)
                empty = finished.isEmpty
            }
            delete(indexPath, sections: sections, empty: empty)
            order.archive()
        }
    }
    
    fileprivate func delete(_ indexPath: IndexPath, sections: Int, empty: Bool) {
        tableView.beginUpdates()
        if (empty) {
            if (sections == 1) {
                tableView.reloadSections(IndexSet(integer: (indexPath as NSIndexPath).section), with: .fade)
            }
            else {
                tableView.deleteSections(IndexSet(integer: (indexPath as NSIndexPath).section), with: .fade)
            }
        }
        if (sections == 1 && empty) {
            tableView.reloadRows(at: [indexPath], with: .fade)
        }
        else {
            tableView.deleteRows(at: [indexPath], with: .fade)
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
        QObject.disconnect(self)
        QObject.connect(o, signal: #selector(ExpLeagueOrder.messagesChanged), receiver: self, slot: #selector(OrderBadge.invalidate(_:)))
        QObject.connect(o, signal: #selector(ExpLeagueOrder.unreadChanged), receiver: self, slot: #selector(OrderBadge.invalidate(_:)))
        update(order: o)
    }

    func invalidate(_ notification: Notification) {
        if let order = notification.object as? ExpLeagueOrder {
            DispatchQueue.main.async {
                self.update(order: order)
            }
        }
    }
    
    func update(order o: ExpLeagueOrder) {
        contentTypeIcon.image = o.typeIcon
        title.text = o.text
        let unread = o.unread
        if (unread > 0) {
            unreadBadge.isHidden = false
            unreadBadge.text = "\(unread)"
        }
        else {
            unreadBadge.isHidden = true
        }
    }
}

class OngoingOrderStateCell: OrderBadge {
    @IBOutlet weak var status: UILabel!
    @IBOutlet weak var date: UILabel!
    
    var lastOrder: ExpLeagueOrder?
    func onOrderChanged() {
        DispatchQueue.main.async {
            guard self.lastOrder != nil else {
                return
            }
            self.update(order: self.lastOrder!)
            self.layoutIfNeeded()
        }
    }
    
    override func update(order o: ExpLeagueOrder) {
        super.update(order: o)
        if (o != lastOrder) {
            if lastOrder != nil {
                QObject.disconnect(self)
            }
            QObject.connect(o, signal: #selector(ExpLeagueOrder.messagesChanged), receiver: self, slot: #selector(onOrderChanged))
        }
//        else if (o.status == .overtime) {
//            let formatter = DateFormatter()
//            formatter.timeStyle = .short
//            formatter.timeZone = TimeZone(secondsFromGMT: 0)
//            formatter.dateFormat = "H'ч 'mm'м'"
//
//            status.textColor = Palette.ERROR
//            status.text = "ПРОСРОЧЕН НА \(formatter.string(from: Date(timeIntervalSince1970: -o.timeLeft)))"
//        }
        if (o.status == .expertSearch) {
            status.textColor = Palette.COMMENT
            status.text = "ИЩЕМ ЭКСПЕРТА"
        }
        else if (o.status == .open || o.status == .overtime) {
            status.textColor = Palette.COMMENT
            status.text = "В РАБОТЕ: \(o.activeExpert?.name ?? "")"
        }
        else if (!o.judged) {
            status.textColor = Palette.OK
            status.text = "ОЖИДАЕТ ОЦЕНКИ"
        }
        let formatter = DateFormatter()
        formatter.dateStyle = .short;
        formatter.timeStyle = .short;
        
        formatter.doesRelativeDateFormatting = true
        date.text = formatter.string(from: Date(timeIntervalSinceReferenceDate: o.started))
    }
    
    deinit {
        QObject.disconnect(self)
    }
}

class FinishedOrderStateCell: OrderBadge {
    @IBOutlet weak var shortAnswer: UILabel!
    @IBOutlet weak var date: UILabel!
    
    override func update(order o: ExpLeagueOrder) {
        super.update(order: o)
        shortAnswer.text = (o.status == .canceled) ? "ОТМЕНЕН" : o.shortAnswer
        let formatter = DateFormatter()
        formatter.dateStyle = .short;
        formatter.timeStyle = .short;
        formatter.doesRelativeDateFormatting = true
        
        date.text = formatter.string(from: Date(timeIntervalSinceReferenceDate: o.started))
    }
}
