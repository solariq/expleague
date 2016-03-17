//
// Created by Igor E. Kuralenok on 09/03/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

@objc
class ChatModel: NSObject, UITableViewDataSource, UITableViewDelegate {
    private var lastKnownMessage: Int = 0
    let order: ExpLeagueOrder
    let lock: dispatch_queue_t

    weak var controller: OrderDetailsViewController? {
        didSet {
            if (controller != nil) {
                controller!.messages.dataSource = self
                controller!.messages.delegate = self
                controller!.answerText = answer
                controller!.state = state
            }
        }
    }
    var answer: String = "" {
        didSet {
            if let controller = self.controller {
                controller.answerText = answer
            }
        }
    }

    var state: ChatState = .Chat {
        didSet {
            if let controller = self.controller {
                controller.state = state
            }
        }
    }

    func markAsRead() {
        for i in 0..<order.count {
            let msg = order.message(i)
            if (!msg.isRead) {
                msg.setProperty("read", value: "true")
            }
        }
    }

    func sync() {
        dispatch_sync(lock){
            self.syncInner()
        }
    }
    
    func translateToIndex(plain: Int) -> NSIndexPath? {
        var x = plain
        for i in 0..<groups.count {
            for j in 0..<groups[i].count {
                x--
                if (x == 0) {
                    return NSIndexPath(forRow: j, inSection: i)
                }
            }
            x--
        }
        return nil
    }

    private func syncInner() {
        if (cells.isEmpty) {
            cells.append(SetupModel(order: order))
        }
        let cellsCount = cells.count
        let startedFrom = lastKnownMessage
        var model = cells.last!
        var modelChangeCount = 0
        
        var expertModel = cells.filter({$0 is ExpertModel}).last as? ExpertModel
        expertModel = expertModel != nil ? (expertModel!.status ? expertModel : nil) : nil
        var progressModel = cells.last is TaskInProgressModel || cells.last is LookingForExpertModel ? cells.last : LookingForExpertModel(order: order)

        if (model is LookingForExpertModel || model is TaskInProgressModel) {
            cells.removeLast();
            model = cells.last!
        }

        while (lastKnownMessage < order.count) {
            if (modelChangeCount > 2) {
                print("Loop found! Enforce next message")
                lastKnownMessage++
            }
            let msg = order.message(lastKnownMessage)
            print("\(order.jid) -> \(msg.type)")
            if (msg.type != .System && !model.accept(msg)) { // switch model
                modelChangeCount++
                var newModel : ChatCellModel? = nil
                if (msg.type == .ExpertAssignment) {
                    if (expertModel == nil || !expertModel!.accept(msg)) {
                        expertModel = ExpertModel()
                        newModel = expertModel
                    }
                    if progressModel is LookingForExpertModel {
                        progressModel = TaskInProgressModel(order: order)
                    }
                }
                else if (msg.type == .ExpertCancel) {
                    expertModel!.accept(msg)
                    expertModel = nil
                }
                else if (msg.type == .ExpertProgress) {
                    progressModel?.accept(msg)
                }
                else if (msg.type == .Answer) {
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += (msg.body!);
                    answer += "\n<a class=\"back_to_chat\" href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"
                    newModel = AnswerReceivedModel(id: id, progress: progressModel as! TaskInProgressModel)
                    progressModel = nil
                }
                else if (msg.type == .ExpertMessage) {
                    newModel = ChatMessageModel(incoming: true, author: msg.from)
                }
                else {
                    newModel = ChatMessageModel(incoming: false, author: "me")
                    if (progressModel == nil) {
                        progressModel = LookingForExpertModel(order: order)
                    }
                }
                if (newModel != nil) {
                    cells.append(newModel!)
                    model = cells.last!
                    continue
                }
            }
            lastKnownMessage++
            modelChangeCount = 0
        }
        
        state = .Chat
        if (model is AnswerReceivedModel) {
            switch(order.status) {
            case .Deciding:
                state = .Ask
                break
            case .Closed:
                state = .Closed
                break
            default:
                state = .Chat
            }
        }
        else if (order.isActive && progressModel != nil) {
            cells.append(progressModel!)
        }
        controller?.messages.beginUpdates()
        updateGroups()
        controller?.messages.reloadData()
        controller?.messages.endUpdates()

        if ((startedFrom != lastKnownMessage || cells.count != cellsCount) && controller != nil) {
            controller!.answerText = answer
            controller!.scrollToLastMessage()
        }
    }

    private var cells: [ChatCellModel] = [];
    var groups: [[ChatCellModel]] = []
    var experts: [ExpertModel] = []
    private func updateGroups() {
        groups.removeAll()
        var group = Array<ChatCellModel>()
        for cell in cells {
            if cell is ExpertModel {
                experts.append(cell as! ExpertModel)
                groups.append(group)
                group = Array<ChatCellModel>()
            }
            else {
                group.append(cell)
            }
        }
        groups.append(group)
    }
    
    var lastIndex: NSIndexPath {
        return NSIndexPath(forRow: groups[groups.count - 1].count - 1, inSection: groups.count - 1)
    }

    func numberOfSectionsInTableView(tableView: UITableView) -> Int {
        return groups.count
    }

    func tableView(tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return section > 0 ? CGFloat(ExpertPresentation.height + 8) : 0
    }
    
    func tableView(tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        if (section == 0) {
            return UIView()
        }
        let expert = experts[section - 1]
        let expertPresentation = NSBundle.mainBundle().loadNibNamed("ExpertPresentation", owner: self, options: [:])[0] as! UIView
        try! expert.form(chatCell: expertPresentation)
        let container = UIView()
        container.backgroundColor = UIColor.clearColor()
        container.frame = CGRectMake(0, 0, tableView.frame.width, ExpertPresentation.height + 8)
        container.addSubview(expertPresentation)
        expertPresentation.frame = CGRectMake(24, 8, tableView.frame.width - 48, ExpertPresentation.height)
        return container
    }
    
    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return groups[section].count;
    }

    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let model = groups[indexPath.section][indexPath.row]
        let cell = tableView.dequeueReusableCellWithIdentifier(String(model.type), forIndexPath: indexPath) as! ChatCell
        cell.controller = controller
        cell.frame.size.width = tableView.frame.width
        try! model.form(chatCell: cell)
        if let control = cell as? SimpleChatCell {
            control.actionHighlighted = indexPath == lastIndex
        }
        return cell
    }

    func tableView(tableView: UITableView, willDisplayCell cell: UITableViewCell, forRowAtIndexPath indexPath: NSIndexPath) {
        cell.backgroundColor = UIColor.clearColor()
        cell.contentView.backgroundColor = UIColor.clearColor()
    }

    var modelCells: [CellType: ChatCell] = [:]
    func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        let model = groups[indexPath.section][indexPath.row]
        var modelCell = modelCells[model.type]
        if modelCell == nil {
            modelCell = (tableView.dequeueReusableCellWithIdentifier(String(model.type)) as! ChatCell)
            modelCells[model.type] = modelCell
        }
        modelCell?.frame.size.width = tableView.frame.width
        if let compositeCell = modelCell as? MessageChatCell {
            return model.height(maxWidth: compositeCell.maxWidth)
        }
        else {
            return model.height(maxWidth: tableView.frame.width)
        }
    }

    init(order: ExpLeagueOrder) {
        self.order = order
        self.lock = dispatch_queue_create("com.expleague.LockQueue\(order.jid.user)", nil)
        super.init()
        order.model = self
        sync()
    }
}

class ChatAction: NSObject {
    let action: ()->Void
    let caption: String

    init(action: () -> Void, caption: String) {
        self.action = action
        self.caption = caption
    }

    func push() {
        action()
    }
}

enum ChatState: Int {
    case Chat = 0
    case Ask = 2
    case Closed = 3
}
