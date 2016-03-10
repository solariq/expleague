//
// Created by Igor E. Kuralenok on 09/03/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

@objc
class ChatModel: NSObject, UITableViewDataSource, UITableViewDelegate {
    private var lastKnownMessage: Int = 0
    private var haveActiveExpert = false
    let order: ExpLeagueOrder
    let lock: dispatch_queue_t

    weak var controller: OrderDetailsVeiwController? {
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

    var progressModel: ExpertInProgressModel? = nil
    var progressCellIndex: Int?
    func sync() {
        dispatch_sync(lock){
            self.syncInner()
        }
    }

    private func syncInner() {
        if (cells.isEmpty) {
            cells.append(SetupModel(order: order))
        }
        let cellsCount = cells.count
        let startedFrom = lastKnownMessage
        var model = cells.last!
        var modelChangeCount = 0
        if (model is LookingForExpertModel) {
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
            if (!model.accept(msg)) { // switch model
                modelChangeCount++
                var newModel : ChatCellModel? = nil
                if (msg.type == .ExpertAssignment) {
                    haveActiveExpert = true
                    progressModel = ExpertInProgressModel(order: order)
                    progressCellIndex = cells.count
                    newModel = progressModel!
                }
                else if (msg.type == .ExpertCancel) {
                    if (progressCellIndex != nil) {
                        cells.removeAtIndex(progressCellIndex!)
                    }
                    haveActiveExpert = false
                    progressModel = nil
                    progressCellIndex = nil
                }
                else if (msg.type == .ExpertProgress) {
                    progressModel?.accept(msg)
                }
                else if (msg.type == .Answer) {
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += (msg.body!);
                    answer += "\n<a class=\"back_to_chat\" href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"
                    newModel = AnswerReceivedModel(id: id, progress: progressModel!)
                    progressModel = nil
                    if (progressCellIndex != nil) {
                        cells.removeAtIndex(progressCellIndex!)
                        progressCellIndex = nil
                    }
                    haveActiveExpert = false
                }
                else if (msg.type == .ExpertMessage) {
                    newModel = ChatMessageModel(incoming: true, author: msg.from)
                }
                else {
                    newModel = ChatMessageModel(incoming: false, author: "me")
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
            case .Feedback:
                state = .Feedback
                break
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
        else if (order.status == .ExpertSearch && !(cells.last! is LookingForExpertModel)) {
            cells.append(LookingForExpertModel(order: order))
        }
        else if(progressModel != nil) {
            cells.removeAtIndex(progressCellIndex!)
            progressCellIndex = nil
            progressModel = nil
        }

        if ((startedFrom != lastKnownMessage || cells.count != cellsCount) && controller != nil) {
            controller!.answerText = answer
            controller!.messages.reloadData()
            controller!.scrollToLastMessage()
        }
    }

    var cells: [ChatCellModel] = [];

    func tableView(tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section > 0 ? 0 : cells.count;
    }

    func tableView(tableView: UITableView, cellForRowAtIndexPath indexPath: NSIndexPath) -> UITableViewCell {
        let message = cells[indexPath.item]
        let cell = tableView.dequeueReusableCellWithIdentifier(String(message.type), forIndexPath: indexPath) as! ChatCell
        cell.controller = controller
        cell.frame.size.width = tableView.frame.width
        try! message.form(chatCell: cell)
        return cell
    }

    func tableView(tableView: UITableView, willDisplayCell cell: UITableViewCell, forRowAtIndexPath indexPath: NSIndexPath) {
        cell.backgroundColor = UIColor.clearColor()
        cell.contentView.backgroundColor = UIColor.clearColor()
    }

    var modelCells: [CellType: ChatCell] = [:]
    func tableView(tableView: UITableView, heightForRowAtIndexPath indexPath: NSIndexPath) -> CGFloat {
        let model = cells[indexPath.item]
        var modelCell = modelCells[model.type]
        if modelCell == nil {
            modelCell = (tableView.dequeueReusableCellWithIdentifier(String(model.type)) as! ChatCell)
            modelCells[model.type] = modelCell
        }
        modelCell?.frame.size.width = tableView.frame.width
        if let compositeCell = modelCell as? CompositeChatCell {
            return model.height(maxWidth: compositeCell.maxContentSize.width)
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
    case Feedback = 1
    case Ask = 2
    case Closed = 3
}
