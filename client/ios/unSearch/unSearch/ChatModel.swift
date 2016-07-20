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
    var lastAnswer: AnswerReceivedModel?

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
        order.messages.forEach{msg in
            msg.read = true
        }
    }

    func sync(rebuild: Bool = false) {
        dispatch_async(dispatch_get_main_queue()){
            if (rebuild) {
                self.rebuild()
            }
            else {
                self.syncInner()
            }
        }
    }
    
    func translateToIndex(plain: Int) -> NSIndexPath? {
        var x = plain - 1
        for i in 0..<groups.count {
            for j in 0..<groups[i].count {
                x -= 1
                if (x <= 0) {
                    return NSIndexPath(forRow: j, inSection: i)
                }
            }
            x -= 1
        }
        return nil
    }

    private var finished = true
    private func rebuild() {
        finished = true
        cells.removeAll()
        lastAnswer = nil
        lastKnownMessage = 0
        answer = ""
        syncInner()
    }
    
    private func syncInner() {
        guard finished else {
            rebuild()
            return
        }
        finished = false
        var progressModel = cells.last is TaskInProgressModel || cells.last is LookingForExpertModel ? cells.last : nil
        if (cells.isEmpty) {
            cells.append(SetupModel(order: order))
            progressModel = LookingForExpertModel(order: order)
        }
        var model = cells.last!
        var modelChangeCount = 0
        
        var expertModel = cells.filter({$0 is ExpertModel}).last as? ExpertModel
        expertModel = expertModel != nil ? (expertModel!.status != .Canceled ? expertModel : nil) : nil

        if (model is LookingForExpertModel || model is TaskInProgressModel) {
            cells.removeLast();
            model = cells.last!
        }
        let messages = order.messages
        while (lastKnownMessage < messages.count) {
            if (modelChangeCount > 2) {
                AppDelegate.instance.activeProfile!.log("Loop found in the chat model! Enforcing next message.")
                lastKnownMessage += 1
                guard lastKnownMessage < messages.count else {
                    break
                }
            }
            let msg = messages[lastKnownMessage]
            print("\(order.jid) -> \(msg.type)")//: \(msg.body ?? "")")
            if (!model.accept(msg)) { // switch model
                modelChangeCount += 1
                var newModel : ChatCellModel? = nil
                if (msg.type == .ExpertAssignment) {
                    let expert = msg.expert!
                    if (expertModel == nil || expertModel!.expert.login != expert.login) {
                        expertModel = ExpertModel(expert: expert)
                        newModel = expertModel
                    }
                    else {
                        expertModel?.status = .OnTask
                    }
                    if progressModel is LookingForExpertModel {
                        progressModel = TaskInProgressModel(order: order)
                    }
                }
                else if (msg.type == .ExpertCancel) {
                    expertModel!.status = .Canceled
                    expertModel = nil
                }
                else if (msg.type == .ExpertProgress) {
                    progressModel?.accept(msg)
                }
                else if (msg.type == .Answer) {
                    expertModel?.status = .Finished
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += (msg.body!);
                    answer += "\n<a class=\"back_to_chat\" href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"
                    lastAnswer = AnswerReceivedModel(id: id, progress: (progressModel as? TaskInProgressModel) ?? TaskInProgressModel(order: order))
                    newModel = lastAnswer
                    
                    progressModel = nil
                }
                else if (msg.type == .ExpertMessage) {
                    newModel = ChatMessageModel(incoming: true, author: msg.from)
                }
                else if (msg.type == .ClientMessage) {
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
            lastKnownMessage += 1
            modelChangeCount = 0
        }
        
        switch(order.status) {
        case .Deciding:
            state = .Ask
        case .Closed, .Canceled:
            state = order.fake ? .Save : .Closed
        default:
            state = .Chat
            if (progressModel != nil) {
                cells.append(progressModel!)
            }
        }
        
        updateGroups()
        controller?.messages.reloadData()
        controller?.answerText = answer
        finished = true
        self.controller?.scrollToLastMessage()
    }

    private var cells: [ChatCellModel] = [];
    var groups: [[ChatCellModel]] = []
    var experts: [ExpertModel] = []
    private func updateGroups() {
        groups.removeAll()
        experts.removeAll()
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
    
    var lastIndex: NSIndexPath? {
        for i in (0..<groups.count).reverse() {
            if (groups[i].count > 0) {
                return NSIndexPath(forRow: groups[i].count - 1, inSection: i)
            }
        }
        return nil
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
        super.init()
        QObject.connect(order, signal: #selector(ExpLeagueOrder.notify), receiver: self, slot: #selector(self.sync))
    }
    
    deinit {
        QObject.disconnect(self)
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
    case Save = 1
    case Ask = 2
    case Closed = 3
}
