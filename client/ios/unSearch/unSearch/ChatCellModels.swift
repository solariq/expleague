//
// Created by Igor E. Kuralenok on 26/01/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import XMPPFramework
import MMMarkdown

import unSearchCore

protocol ChatCellModel {
    var type: CellType {get}

    func height(maxWidth width: CGFloat) -> CGFloat
    func form(chatCell cell: UIView) throws
    func accept(_ message: ExpLeagueMessage) -> Bool
}

enum ModelErrors: Error {
    case wrongCellType
}

private class MessageVisitor: ExpLeagueMessageVisitor {
    let model: CompositeCellModel
    init(model: CompositeCellModel) {
        self.model = model;
    }
    func message(_ message: ExpLeagueMessage, text: String) {
        model.append(text: text, time: message.ts as Date)
    }
    
    func message(_ message: ExpLeagueMessage, title: String, text: String) {
        let result = NSMutableAttributedString()
        result.append(NSAttributedString(string: title, attributes: [
            NSFontAttributeName: UIFont.preferredFont(forTextStyle: UIFontTextStyle.headline)
            ]))
        result.append(NSAttributedString(string: "\n" + text, attributes: [
            NSFontAttributeName: UIFont.preferredFont(forTextStyle: UIFontTextStyle.body)
            ]))
        model.append(richText: result, time: message.ts as Date)
    }
    
    func message(_ message: ExpLeagueMessage, title: String, link: String) {
        model.append(
            richText: NSAttributedString(string: title, attributes: [
                NSFontAttributeName: UIFont.preferredFont(forTextStyle: UIFontTextStyle.body),
                NSLinkAttributeName: link,
                NSForegroundColorAttributeName: UIColor.blue
                ]),
            time: message.ts as Date)
    }
    func message(_ message: ExpLeagueMessage, title: String, image: UIImage) {
        model.append(image: image, time: message.ts as Date)
    }
}

class CompositeCellModel: ChatCellModel {
    let separatorHeight: CGFloat = 8

    fileprivate var parts:[AnyObject] = []
    fileprivate var timeStamps: [Date] = []
    var textAlignment: NSTextAlignment = .left
    var effectiveWidth: CGFloat = 0

    func append(text: String, time: Date) {
        parts.append(text as AnyObject)
        timeStamps.append(time)
    }

    func append(richText text: NSAttributedString, time: Date) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(image img: UIImage, time: Date) {
        parts.append(img)
        timeStamps.append(time)
    }

    func append(location point: CLLocation, time: Date) {
        parts.append(point)
        timeStamps.append(time)
    }

    func append(action run: @escaping () -> Void, caption: String, time: Date) {
        parts.append(ChatAction(action: run, caption: caption))
        timeStamps.append(time)
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        message.visitParts(MessageVisitor(model: self))
        return true;
    }

    var type: CellType {
        return .none
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        var height: CGFloat = 0
        for i in 0 ..< parts.count {
            let blockSize = self.blockSize(width: width - 8, index: i)
            height += blockSize.height
        }
        return ceil(height)
    }

    func form(chatCell cell: UIView) throws {
        guard let messageViewCell = cell as? MessageChatCell else {
            throw ModelErrors.wrongCellType
        }
        form(messageViewCell: messageViewCell)
    }

    func form(messageViewCell cell: MessageChatCell) {
        for view in cell.content.subviews { // cleanup
            view.removeFromSuperview()
        }
        var height: CGFloat = 0.0
        var width: CGFloat = 0.0
        var prev: UIView?

        for i in 0 ..< parts.count {
            let blockSize = self.blockSize(width: cell.maxWidth - 8, index: i)
            var block: UIView?
            if let text = parts[i] as? String {
                let label = UITextView()
                label.isEditable = false
                label.dataDetectorTypes = .all
                label.font = ChatCell.defaultFont
                label.text = text
                label.textAlignment = .left
                label.backgroundColor = UIColor.clear
                label.textContainerInset = UIEdgeInsets.zero
                label.contentInset = UIEdgeInsets.zero
                label.contentSize = blockSize
                label.isScrollEnabled = false
                label.textContainer.lineFragmentPadding = 0
                label.textAlignment = self.textAlignment

                block = label
            }
            else if let richText = parts[i] as? NSAttributedString {
                let label = UITextView()
                label.isEditable = false
                label.dataDetectorTypes = .all
                label.attributedText = richText
                label.textAlignment = .left
                label.backgroundColor = UIColor.clear
                label.textContainerInset = UIEdgeInsets.zero
                label.contentInset = UIEdgeInsets.zero
                label.contentSize = blockSize
                label.isScrollEnabled = false
                label.textContainer.lineFragmentPadding = 0
                label.textAlignment = self.textAlignment
                block = label
            }
            else if let image = parts[i] as? UIImage {
                let imageView = UIImageView()
                imageView.image = image
                block = imageView
            }
            else if let location = parts[i] as? CLLocation {
                let mapView = MKMapView()
                let region = MKCoordinateRegionMake(location.coordinate, MKCoordinateSpanMake(0.005, 0.005))
                mapView.setRegion(region, animated: false)
                mapView.setCenter(location.coordinate, animated: false)
                mapView.isUserInteractionEnabled = false
                let annotation = MKPointAnnotation()
                annotation.coordinate = location.coordinate
                mapView.addAnnotation(annotation)
                block = mapView
            }
            else if let action = parts[i] as? ChatAction {
                let button = UIButton(type: .custom)
                button.setTitle(action.caption, for: UIControlState())
                if #available(iOS 9.0, *) {
                    button.addTarget(action, action: #selector(ChatAction.push), for: .primaryActionTriggered)
                } else {
                    button.addTarget(action, action: #selector(ChatAction.push), for: .touchUpInside)
                }
                block = button
            }
            if (block != nil) {
                cell.content.addSubview(block!)
                width = max(width, blockSize.width)
                block!.translatesAutoresizingMaskIntoConstraints = false
                block!.addConstraint(NSLayoutConstraint(item: block!, attribute: .width, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: blockSize.width))
                block!.addConstraint(NSLayoutConstraint(item: block!, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: blockSize.height))

                if (prev != nil) {
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .top, relatedBy: .equal, toItem: prev!, attribute: .bottom, multiplier: 1, constant: 0))
                }
                else {
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .top, relatedBy: .equal, toItem: cell.content, attribute: .top, multiplier: 1, constant: 4))
                }
                switch(self.textAlignment) {
                case .left:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .leading, relatedBy: .equal, toItem: cell.content, attribute: .leading, multiplier: 1, constant: 4))
                    break
                case .center:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .centerX, relatedBy: .equal, toItem: cell.content, attribute: .centerX, multiplier: 1, constant: 0))
                    break
                default:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .trailing, relatedBy: .equal, toItem: cell.content, attribute: .trailing, multiplier: 1, constant: 4))
                    break

                }
                height += blockSize.height
                prev = block
            }
        }

//        cell.content.addConstraint(NSLayoutConstraint(item: cell.content, attribute: .height, relatedBy: .greaterThanOrEqual, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: ceil(height)))
        effectiveWidth = width + 8
        //cell.content.addConstraint(NSLayoutConstraint(item: cell.content, attribute: .width, relatedBy: .greaterThanOrEqual, toItem: nil, attribute: .notAnAttribute, multiplier: 1, constant: ceil(width + 12)))
//        print("Cell: \(cell) for model \(self). Content size: (\(width), \(height)), cell size: \(cell.frame.size), maxWidth: \(cell.maxWidth)")
//        print("avatar frame \(cell.avatar?.frame ?? CGRect.zero). effective width: \(effectiveWidth)")
    }

    fileprivate func blockSize(width: CGFloat, index: Int) -> CGSize {
        var blockSize: CGSize
        if let text = parts[index] as? String {
            blockSize = text.boundingRect(
                    with: CGSize(width: floor(width), height: CGFloat(MAXFLOAT)),
                    options: NSStringDrawingOptions.usesLineFragmentOrigin,
                    attributes: [
                            NSFontAttributeName : ChatCell.defaultFont
                    ],
                    context: nil
                ).size
//            blockSize.width += 1
//            blockSize.height += 1
        }
        else if let richText = parts[index] as? NSAttributedString {
            blockSize = richText.boundingRect(
            with: CGSize(width: width, height: CGFloat(MAXFLOAT)),
                    options: NSStringDrawingOptions.usesLineFragmentOrigin,
                    context: nil).size
        }
        else if let image = parts[index] as? UIImage {
            var bs = image.size
            if (bs.width > width - 32) {
                bs.height *= (width - 32)/bs.width
                bs.width = (width - 32)
            }
            blockSize = bs
        }
        else if let _ = parts[index] as? CLLocation {
            blockSize = CGSize(width: (width - 32), height: (width - 32))
        }
        else if let _ = parts[index] as? ChatAction {
            blockSize = CGSize(width: width - 10, height: 40)
        }
        else {
            blockSize = CGSize(width: 0, height: 0)
        }
        return CGSize(width: ceil(blockSize.width), height: ceil(blockSize.height))
    }
}

class ChatMessageModel: CompositeCellModel {
    let author: String
    let incoming: Bool
    let active: Bool
    var closed: Bool = false

    override var type: CellType {
        return incoming ? .incoming : .outgoing
    }

    init(incoming: Bool, author: String, active: Bool) {
        self.author = author
        self.incoming = incoming
        self.active = active
        super.init()
        if (!active && incoming) {
            append(richText: NSAttributedString(string: "Администратор", attributes: [
                NSFontAttributeName: UIFont.boldSystemFont(ofSize: 12),
                NSForegroundColorAttributeName: Palette.COMMENT
            ]), time: Date())
        }
    }

    override func height(maxWidth width: CGFloat) -> CGFloat {
        return MessageChatCell.height(contentHeight: super.height(maxWidth: width)) + 6
    }
    
    override func form(messageViewCell cell: MessageChatCell) {
        if let avatar = (cell as MessageChatCell).avatar, let expert = ExpLeagueProfile.active.expert(login: author) {
            avatar.isHidden = active
            avatar.expert = expert
            cell.avatarWidth?.constant = active ? CGFloat(0) : CGFloat(25)
        }
        super.form(messageViewCell: cell)
        cell.widthConstraint.constant = effectiveWidth
    }
    
    func close() {
        closed = true
    }
    
    override func accept(_ message: ExpLeagueMessage) -> Bool {
        if (closed || message.from != author || (message.type != .expertMessage && message.type != .clientMessage)) {
            return false
        }

        return super.accept(message)
    }
}

class SetupModel: NSObject, ChatCellModel, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    var timer: Timer?
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
        super.init()
        for image in order.offer.images {
            do {
                let request = URLRequest(url: image as URL)
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returning: nil)
                if let image = UIImage(data: imageData) {
                    images.append(image)
                }
            }
            catch {
                ExpLeagueProfile.active.log("Unable to load image \(image.absoluteString): \(error)");
            }
        }
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    var type: CellType {
        return .setup
    }
    
    var images: [UIImage] = []
    var location: CLLocationCoordinate2D?
    
    func textHeight(_ width: CGFloat) -> CGFloat {
        return ceil(order.text.boundingRect(
            with: CGSize(width: width, height: CGFloat(MAXFLOAT)),
            options: NSStringDrawingOptions.usesLineFragmentOrigin,
            attributes: [
                NSFontAttributeName : ChatCell.topicFont
            ],
            context: nil).size.height)
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return SetupChatCell.height(textHeight: textHeight(width - 32), attachments: images.count + (order.offer.local ? 1 : 0))
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        return message.type == .topic
    }

    func formatPeriodRussian(_ interval: TimeInterval) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "H'ч 'mm'м'"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        let days = Int(floor(interval / (24 * 60 * 60)))
        let ending = days % 10
        var text = "";
        if (ending == 0 && days > 0) {
            text += "\(days) дней, "
        }
        else if (ending > 0 && ending < 2 && (days < 10 || days > 20)) {
            text += "\(days) день, "
        }
        else if (ending > 2 && ending < 5 && (days < 10 || days > 20)) {
            text += "\(days) дня, "
        }
        else if (ending >= 5) {
            text += "\(days) дней, "
        }
        text += formatter.string(from: Date(timeIntervalSinceReferenceDate: interval))
        return text;
    }
    
    @objc
    func advanceTimer(_ timer: Timer) {
        let label = timer.userInfo as! UILabel
        let timeLeft = order.before - CFAbsoluteTimeGetCurrent()
        switch(order.status) {
        case .open, .expertSearch, .overtime:
            label.textColor = Palette.COMMENT
            label.text = "ОТКРЫТО" + (timeLeft > 0 ? ". Осталось: " + formatPeriodRussian(timeLeft) : "")
        case .canceled:
            label.textColor = Palette.COMMENT
            label.text = "ОТМЕНЕНО"
        case .closed, .deciding:
            label.textColor = Palette.OK
            label.text = "ВЫПОЛНЕНО"
        default:
            break
        }
    }
    
    func form(chatCell cell: UIView) throws {
        guard let setupCell = cell as? SetupChatCell else {
            throw ModelErrors.wrongCellType
        }
        setupCell.topic.text = order.offer.topic
        setupCell.textHeight = textHeight(setupCell.textWidth)
        timer = Timer.scheduledTimer(timeInterval: 30, target: self, selector: #selector(SetupModel.advanceTimer(_:)), userInfo: setupCell.status, repeats: true)
        setupCell.attachments = images.count + (order.offer.local ? 1 : 0)
        setupCell.attachmentsView.dataSource = self
//        setupCell.isHidden = !(images.count > 0 || order.offer.local)
        setupCell.attachmentsView.delegate = self
        advanceTimer(timer!)
    }
    
    func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : images.count + (order.offer.local ? 1 : 0)
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "AttachmentCell", for: indexPath) as! AttachmentCell
        if ((indexPath as NSIndexPath).item >= images.count) {
            let mapView = MKMapView()
            let location = order.offer.location!
            let region = MKCoordinateRegionMake(location, MKCoordinateSpanMake(0.005, 0.005))
            mapView.setRegion(region, animated: false)
            mapView.setCenter(location, animated: false)
            mapView.isUserInteractionEnabled = false
            let annotation = MKPointAnnotation()
            annotation.coordinate = location
            mapView.addAnnotation(annotation)
            cell.content = mapView
        }
        else {
            let image = UIImageView(image: images[(indexPath as NSIndexPath).item])
            image.contentMode = UIViewContentMode.scaleAspectFit
            image.backgroundColor = UIColor.white
            cell.content = image
        }
        return cell
    }
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let max = CGSize(width: collectionView.frame.width - 32, height: collectionView.frame.height - 4)
        if (indexPath as NSIndexPath).item < images.count {
            let image = images[(indexPath as NSIndexPath).item]
            return CGSize(width: image.size.width / image.size.height * max.height, height: max.height)
        }
        return max
    }
    
    deinit {
        timer?.invalidate()
    }
}

class ExpertModel: ChatCellModel {
    func height(maxWidth width: CGFloat) -> CGFloat {
        return ExpertPresentation.height
    }
    
    func form(chatCell cell: UIView) throws {
        guard let eipCell = cell as? ExpertPresentation else {
            throw ModelErrors.wrongCellType
        }
        
        eipCell.name.text = expert.name
        eipCell.avatar.expert = expert
        
        switch (status) {
        case .onTask:
            eipCell.status.text = "Над вашим заказом работает"
            eipCell.status.textColor = Palette.COMMENT
            break
        case .canceled:
            eipCell.status.text = "Эксперт отказался от задания"
            eipCell.status.textColor = Palette.ERROR
            break
        case .finished:
            eipCell.status.text = "Для вас работал эксперт"
            eipCell.status.textColor = Palette.COMMENT
            break
        }
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        return false
    }
    
    var type: CellType {
        return .expert
    }
    
    let expert: ExpLeagueMember
    var status: ExpertModelStatus = .onTask
    
    init(expert: ExpLeagueMember) {
        self.expert = expert
    }
}

enum ExpertModelStatus: Int {
    case onTask, canceled, finished
}

class TaskInProgressModel: NSObject, ChatCellModel {
//    let listener: OrderTracker
    var expertProperties = NSMutableDictionary()
    var pagesCount = 0
    var callsCount = 0
    var topics: [ExpLeagueTag] = []
    var patterns: [ExpLeagueTag] = []
    
    var type: CellType {
        return .taskInProgress
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return TaskInProgressCell.height
    }

    func form(chatCell cell: UIView) throws {
        guard let eipCell = cell as? TaskInProgressCell else {
            throw ModelErrors.wrongCellType
        }
        eipCell.pages = pagesCount
        eipCell.calls = callsCount
        eipCell.topicIcon.image = topics.isEmpty ? order.typeIcon : topics.last!.icon
        
        if (eipCell.action == nil) {
            eipCell.action = {() -> Void in
                self.order.cancel(eipCell.controller!)
            }
        }
        eipCell.patternsView.dataSource = self
        eipCell.patternsView.delegate = self
    }

    func accept(_ message: ExpLeagueMessage) -> Bool {
        expertProperties.addEntries(from: message.properties as [AnyHashable: Any])
        if let change = message.change {
            switch change.target {
            case .Pattern:
                if let tag = order.parent.tag(name: change.name) {
                    if (change.type != .Remove) {
                        patterns.append(tag)
                    }
                    else if let index = patterns.index(of: tag) {
                        patterns.remove(at: index)
                    }
                }
                break
            case .Tag:
                if let tag = order.parent.tag(name: change.name) {
                    if (change.type != .Remove) {
                        topics.append(tag)
                    }
                    else if let index = topics.index(of: tag) {
                        topics.remove(at: index)
                    }
                }
                break
            case .Url:
                pagesCount += 1
                break
            case .Phone:
                callsCount += 1
                break
            }
        }
        return message.type == .expertProgress || message.type == .expertAssignment
    }
    
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
    }
}

extension TaskInProgressModel: UICollectionViewDelegate, UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
        return false
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : patterns.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "PatternCell", for: indexPath) as! AttachmentCell
        let image = UIImageView(image: patterns[(indexPath as NSIndexPath).item].icon)
        image.contentMode = UIViewContentMode.scaleAspectFit
        image.backgroundColor = UIColor.white
        cell.content = image
        return cell
    }
}

class LookingForExpertModel: ChatCellModel {
    weak var cell: LookingForExpertCell?
    var active = true
    
    var type: CellType {
        return .lookingForExpert
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return LookingForExpertCell.height
    }

    func form(chatCell cell: UIView) throws {
        guard let lfeCell = cell as? LookingForExpertCell else {
            throw ModelErrors.wrongCellType
        }
        self.cell = lfeCell
        lfeCell.action = {
            self.order.cancel(lfeCell.controller!)
        }
        switch (order.offer.experts.count) {
        case 0:
            lfeCell.caption.text = "Ждем эксперта"
        case 1:
            lfeCell.caption.text = "Ждем выбранного эксперта"
        default:
            lfeCell.caption.text = "Ждем выбранных экспертов"
        }
        if (!ExpLeagueProfile.active.connected) {
            self.cell?.progress.stopAnimating()
            self.cell?.expertsOnline.text = "Подключаемся к серверу"
        }
        else if (ExpLeagueProfile.active.onlineExperts.count == 0) {
            self.cell?.progress.startAnimating()
            self.cell?.expertsOnline.text = "c 22 до 10 (Мск) эксперты отдыхают"
        }
        else {
            let count: Int
            if (order.offer.experts.count > 0) {
                count = Set(order.offer.experts).intersection(Set(ExpLeagueProfile.active.onlineExperts)).count
            }
            else {
                count = ExpLeagueProfile.active.onlineExperts.count
            }
            self.cell?.progress.startAnimating()
            self.cell?.expertsOnline.text = "\(count) эксперт\(Lang.rusNumEnding(count, variants: ["", "а", "ов"])) online"
        }
        self.cell!.layoutIfNeeded()
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        return false
    }
    
    @objc
    func onExpertsChanged() {
        DispatchQueue.main.async {
            if (self.cell != nil) {
                try! self.form(chatCell: self.cell!)
            }
        }
    }
    
    let order: ExpLeagueOrder
    init (order: ExpLeagueOrder){
        self.order = order
        QObject.connect(ExpLeagueProfile.active, signal: #selector(ExpLeagueProfile.expertsChanged), receiver: self, slot: #selector(self.onExpertsChanged))
        QObject.connect(ExpLeagueProfile.active, signal: #selector(ExpLeagueProfile.connectedChanged), receiver: self, slot: #selector(self.onExpertsChanged))
    }
}

class ReopenRoomModel: ChatCellModel {
    weak var cell: ReopenRoomCell?
    var active = true
    
    var type: CellType {
        return .lookingForExpert
    }
    
    func height(maxWidth width: CGFloat) -> CGFloat {
        return LookingForExpertCell.height
    }
    
    func form(chatCell cell: UIView) throws {
        guard let reopenCell = cell as? ReopenRoomCell else {
            throw ModelErrors.wrongCellType
        }
        self.cell = reopenCell
        reopenCell.action = {
            self.order.cancel(reopenCell.controller!)
        }
        self.cell!.layoutIfNeeded()
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        return false
    }

    let order: ExpLeagueOrder
    init (order: ExpLeagueOrder){
        self.order = order
    }
}

class AnswerReceivedModel: ChatCellModel {
    var expertProperties: NSDictionary?
    let id: String
    var score: Int?
    var source: ExpLeagueMessage?
    var type: CellType {
        return .answerReceived
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return AnswerReceivedCell.height + (score == nil ? -20 : 0)
    }
    
    func accept(_ message: ExpLeagueMessage) -> Bool {
        if (message.type == .feedback) {
            score = message.properties["stars"] as? Int
        }
        if (source == nil && message.type == .answer) {
            source = message
        }
        guard source == message || message.type == .feedback else {
            return false
        }
        return true
    }

    func form(chatCell cell: UIView) throws {
        guard let arCell = cell as? AnswerReceivedCell else {
            throw ModelErrors.wrongCellType
        }
        try progress.form(chatCell: arCell)
        arCell.id = id
        arCell.rating = score
    }

    let progress: TaskInProgressModel
    init(id: String, progress: TaskInProgressModel) {
        self.id = id
        self.progress = progress
    }
}
