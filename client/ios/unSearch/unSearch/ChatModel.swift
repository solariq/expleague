//
// Created by Igor E. Kuralenok on 09/03/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit

import unSearchCore

@objc
class ChatModel: NSObject, UITableViewDataSource, UITableViewDelegate {
    fileprivate var lastKnownMessage: Int = 0
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

    var state: ChatState = .chat {
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

    func sync(_ rebuild: Bool = false) {
        DispatchQueue.main.async{
            if (rebuild) {
                self.rebuild()
            }
            else {
                self.syncInner()
            }
        }
    }
    
    func translateToIndex(_ plain: Int) -> IndexPath? {
        var x = plain - 1
        for i in 0..<groups.count {
            for j in 0..<groups[i].count {
                x -= 1
                if (x <= 0) {
                    return IndexPath(row: j, section: i)
                }
            }
            x -= 1
        }
        return nil
    }

    fileprivate var finished = true
    fileprivate func rebuild() {
        finished = true
        cells.removeAll()
        lastAnswer = nil
        lastKnownMessage = 0
        answer = ""
        syncInner()
    }
    
    fileprivate func syncInner() {
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
        expertModel = expertModel != nil ? (expertModel!.status != .canceled ? expertModel : nil) : nil

        if (model is LookingForExpertModel || model is TaskInProgressModel) {
            cells.removeLast();
            model = cells.last!
        }
        let messages = order.messages
        while (lastKnownMessage < messages.count) {
            if (modelChangeCount > 2) {
                ExpLeagueProfile.active.log("Loop found in the chat model! Enforcing next message.")
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
                if (msg.type == .expertAssignment) {
                    let expert = msg.expert!
                    if (expertModel == nil || expertModel!.expert.login != expert.login) {
                        expertModel = ExpertModel(expert: expert)
                        newModel = expertModel
                    }
                    else {
                        expertModel?.status = .onTask
                    }
                    if progressModel is LookingForExpertModel {
                        progressModel = TaskInProgressModel(order: order)
                    }
                }
                else if (msg.type == .expertCancel) {
                    expertModel!.status = .canceled
                    expertModel = nil
                }
                else if (msg.type == .expertProgress) {
                    _ = progressModel?.accept(msg)
                }
                else if (msg.type == .answer) {
                    expertModel?.status = .finished
                    let id = "message-\(msg.hashValue)"
                    answer += "\n<div id=\"\(id)\"/>\n"
                    answer += msg.html;
                    answer += "\n<a class=\"back_to_chat\" href='unSearch:///chat-messages#\(cells.count)'>Обратно в чат</a>\n"
                    lastAnswer = AnswerReceivedModel(id: id, progress: (progressModel as? TaskInProgressModel) ?? TaskInProgressModel(order: order))
                    newModel = lastAnswer
                    
                    progressModel = nil
                }
                else if (msg.type == .expertMessage) {
                    newModel = ChatMessageModel(incoming: true, author: msg.from)
                }
                else if (msg.type == .clientMessage) {
                    newModel = ChatMessageModel(incoming: false, author: "me")
                    if (progressModel == nil) {
                        progressModel = LookingForExpertModel(order: order)
                    }
                }
                else if (msg.type == .clientCancel) {
                    expertModel?.status = .finished
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
        case .deciding:
            state = .ask
        case .closed, .canceled:
            state = order.fake ? .save : .closed
        default:
            state = .chat
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

    fileprivate var cells: [ChatCellModel] = [];
    var groups: [[ChatCellModel]] = []
    var experts: [ExpertModel] = []
    fileprivate func updateGroups() {
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
    
    var lastIndex: IndexPath? {
        for i in (0..<groups.count).reversed() {
            if (groups[i].count > 0) {
                return IndexPath(row: groups[i].count - 1, section: i)
            }
        }
        return nil
    }

    func numberOfSections(in tableView: UITableView) -> Int {
        return groups.count
    }

    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return section > 0 ? CGFloat(ExpertPresentation.height + 8) : 0
    }
    
    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        if (section == 0) {
            return UIView()
        }
        let expert = experts[section - 1]
        let expertPresentation = Bundle.main.loadNibNamed("ExpertPresentation", owner: self, options: [:])?[0] as! UIView
        try! expert.form(chatCell: expertPresentation)
        let container = UIView()
        container.backgroundColor = UIColor.clear
        container.frame = CGRect(x: 0, y: 0, width: tableView.frame.width, height: ExpertPresentation.height + 8)
        container.addSubview(expertPresentation)
        expertPresentation.frame = CGRect(x: 24, y: 8, width: tableView.frame.width - 48, height: ExpertPresentation.height)
        return container
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return groups[section].count;
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let model = groups[(indexPath as NSIndexPath).section][(indexPath as NSIndexPath).row]
        let cell = tableView.dequeueReusableCell(withIdentifier: String(describing: model.type), for: indexPath) as! ChatCell
        cell.controller = controller
        cell.frame.size.width = tableView.frame.width
        try! model.form(chatCell: cell)
        if let control = cell as? SimpleChatCell {
            control.actionHighlighted = indexPath == lastIndex
        }
        return cell
    }

    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        cell.backgroundColor = UIColor.clear
        cell.contentView.backgroundColor = UIColor.clear
    }

    var modelCells: [CellType: ChatCell] = [:]
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        let model = groups[(indexPath as NSIndexPath).section][(indexPath as NSIndexPath).row]
        var modelCell = modelCells[model.type]
        if modelCell == nil {
            modelCell = (tableView.dequeueReusableCell(withIdentifier: String(describing: model.type)) as! ChatCell)
            modelCells[model.type] = modelCell
            modelCell!.frame.size.width = tableView.frame.width
        }
        try! model.form(chatCell: modelCell!)
        modelCell!.updateConstraints()
        modelCell!.layoutIfNeeded()
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
        QObject.connect(order, signal: #selector(ExpLeagueOrder.messagesChanged), receiver: self, slot: #selector(self.sync))
    }
    
    deinit {
        QObject.disconnect(self)
    }
}

class ChatAction: NSObject {
    let action: ()->Void
    let caption: String

    init(action: @escaping () -> Void, caption: String) {
        self.action = action
        self.caption = caption
    }

    func push() {
        action()
    }
}

enum ChatState: Int {
    case chat = 0
    case save = 1
    case ask = 2
    case closed = 3
}
