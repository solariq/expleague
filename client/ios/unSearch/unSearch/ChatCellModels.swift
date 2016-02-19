//
// Created by Igor E. Kuralenok on 26/01/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import XMPPFramework
import MMMarkdown

protocol ChatCellModel {
    var type: CellType {get}

    func height(maxWidth width: CGFloat) -> CGFloat
    func form(chatCell cell: ChatCell) throws
    func accept(message: ExpLeagueMessage) -> Bool
}

enum ModelErrors: ErrorType {
    case WrongCellType
}

private class MessageVisitor: ExpLeagueMessageVisitor {
    let model: CompositeCellModel
    init(model: CompositeCellModel) {
        self.model = model;
    }
    func message(message: ExpLeagueMessage, text: String) {
        model.append(text: text, time: message.time)
    }
    
    func message(message: ExpLeagueMessage, title: String, text: String) {
        let result = NSMutableAttributedString()
        result.appendAttributedString(NSAttributedString(string: title, attributes: [
            NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleHeadline)
            ]))
        result.appendAttributedString(NSAttributedString(string: "\n" + text, attributes: [
            NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
            ]))
        model.append(richText: result, time: message.time)
    }
    
    func message(message: ExpLeagueMessage, title: String, link: String) {
        model.append(
            richText: NSAttributedString(string: title, attributes: [
                NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody),
                NSLinkAttributeName: link,
                NSForegroundColorAttributeName: UIColor.blueColor()
                ]),
            time: message.time)
    }
    func message(message: ExpLeagueMessage, title: String, image: UIImage) {
        model.append(
            richText: NSAttributedString(
                string: title,
                attributes: [
                    NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody),
                    NSForegroundColorAttributeName: UIColor.blueColor()]),
            time: message.time
        )
        model.append(image: image, time: message.time)
    }
}

class CompositeCellModel: ChatCellModel {
    let defaultFont = UIFont(name: "Helvetica Neue", size: 14)!
    let separatorHeight: CGFloat = 8

    private var parts:[AnyObject] = []
    private var timeStamps: [NSTimeInterval] = []
    var textAlignment: NSTextAlignment = .Left

    func append(text text: String, time: NSTimeInterval) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(richText text: NSAttributedString, time: NSTimeInterval) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(image img: UIImage, time: NSTimeInterval) {
        parts.append(img)
        timeStamps.append(time)
    }

    func append(location point: CLLocation, time: NSTimeInterval) {
        parts.append(point)
        timeStamps.append(time)
    }

    func append(action run: () -> Void, caption: String, time: NSTimeInterval) {
        parts.append(ChatAction(action: run, caption: caption))
        timeStamps.append(time)
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        message.visitParts(MessageVisitor(model: self))
        return true;
    }

    var type: CellType {
        return .None
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        var height: CGFloat = 0
        for i in 0 ..< parts.count {
            if (i > 0) {
                height += separatorHeight
            }
            let blockSize = self.blockSize(width: width, index: i)
            height += blockSize.height
        }
        return height
    }

    func form(chatCell cell: ChatCell) throws {
        guard let messageViewCell = cell as? CompositeChatCell else {
            throw ModelErrors.WrongCellType
        }
        form(messageViewCell: messageViewCell)
    }

    func form(messageViewCell cell: CompositeChatCell) {
        for view in cell.content.subviews { // cleanup
            view.removeFromSuperview()
        }
        cell.content.autoresizesSubviews = false
        cell.content.autoresizingMask = .None
        var height: CGFloat = 0.0
        var width: CGFloat = 0.0
        let textColor = cell is MessageChatCell && !(cell as! MessageChatCell).incoming  ? UIColor.whiteColor() : UIColor.blackColor()

        for i in 0 ..< parts.count {
            if (i > 0) {
                height += separatorHeight
            }
            let blockSize = self.blockSize(width: cell.maxContentSize.width, index: i)
            var block: UIView?
            if let text = parts[i] as? String {
                let label = UITextView()
                label.editable = false
                label.dataDetectorTypes = .All
                label.font = defaultFont
                label.text = text
                label.textColor = textColor
                label.textAlignment = .Left
                label.backgroundColor = UIColor.clearColor()
                label.textContainerInset = UIEdgeInsetsZero
                label.contentInset = UIEdgeInsetsZero
                label.contentSize = blockSize
                label.scrollEnabled = false
                label.textContainer.lineFragmentPadding = 0
                label.textAlignment = self.textAlignment
                block = label
            }
            else if let richText = parts[i] as? NSAttributedString {
                let label = UITextView()
                label.editable = false
                label.dataDetectorTypes = .All
                label.attributedText = richText
                label.textColor = textColor
                label.textAlignment = .Left
                label.backgroundColor = UIColor.clearColor()
                label.textContainerInset = UIEdgeInsetsZero
                label.contentInset = UIEdgeInsetsZero
                label.contentSize = blockSize
                label.scrollEnabled = false
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
                mapView.setCenterCoordinate(location.coordinate, animated: false)
                mapView.userInteractionEnabled = false
                let annotation = MKPointAnnotation()
                annotation.coordinate = location.coordinate
                mapView.addAnnotation(annotation)
                block = mapView
            }
            else if let action = parts[i] as? ChatAction {
                let button = UIButton(type: .Custom)
                button.setTitle(action.caption, forState: .Normal)
                if #available(iOS 9.0, *) {
                    button.addTarget(action, action: "push", forControlEvents: .PrimaryActionTriggered)
                } else {
                    button.addTarget(action, action: "push", forControlEvents: .TouchUpInside)
                }
                block = button
            }
            if (block != nil) {
                cell.content.addSubview(block!)
                block!.frame.origin = CGPointMake(0, height)
                height += blockSize.height
                width = max(width, blockSize.width)
                block!.frame.size = blockSize
            }
        }
        for sub in cell.content.subviews {
            if (sub is UITextView) {
                sub.frame.size.width = min(cell.maxContentSize.width, width)
            }
        }
        cell.content.frame.size = CGSizeMake(min(cell.maxContentSize.width, width), height + 2)
        print("Cell: \(cell.dynamicType), content size: \(cell.content.frame.size), cell size: \(cell.frame.size)")
    }

    private func blockSize(width width: CGFloat, index: Int) -> CGSize {
        var blockSize: CGSize
        if let text = parts[index] as? String {
            blockSize = text.boundingRectWithSize(
            CGSizeMake(width, CGFloat(MAXFLOAT)),
                    options: NSStringDrawingOptions.UsesLineFragmentOrigin,
                    attributes: [
                            NSFontAttributeName : defaultFont
                    ],
                    context: nil).size
        }
        else if let richText = parts[index] as? NSAttributedString {
            blockSize = richText.boundingRectWithSize(
            CGSizeMake(width, CGFloat(MAXFLOAT)),
                    options: NSStringDrawingOptions.UsesLineFragmentOrigin,
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
            blockSize = CGSizeMake((width - 32), (width - 32))
        }
        else if let _ = parts[index] as? ChatAction {
            blockSize = CGSizeMake(width - 10, 40)
        }
        else {
            blockSize = CGSizeMake(0, 0)
        }
        return CGSizeMake(blockSize.width, blockSize.height + 2)
    }
}

class ChatMessageModel: CompositeCellModel {
    let author: String
    let incoming: Bool

    override var type: CellType {
        return incoming ? .Incoming : .Outgoing
    }

    init(incoming: Bool, author: String) {
        self.author = author
        self.incoming = incoming
    }

    override func height(maxWidth width: CGFloat) -> CGFloat {
        return MessageChatCell.height(contentHeight: super.height(maxWidth: width)) + 6
    }
    
    override func form(messageViewCell cell: CompositeChatCell) {
        (cell as! MessageChatCell).incoming = incoming
        super.form(messageViewCell: cell)
        if (incoming) {
            (cell as! MessageChatCell).avatar.image = AppDelegate.instance.activeProfile!.avatar(author, url: nil)
            (cell as! MessageChatCell).avatar.layer.cornerRadius = (cell as! MessageChatCell).avatar.frame.size.width / 2;
            (cell as! MessageChatCell).avatar.clipsToBounds = true;
        }
    }
    
    override func accept(message: ExpLeagueMessage) -> Bool {
        if (message.from != author || message.type == .Answer) {
            return false
        }

        return super.accept(message)
    }
}

class SetupModel: CompositeCellModel {
    static var timer: NSTimer?
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
        super.init()
        textAlignment = .Center
        append(text: order.text, time: order.started)
        if (order.topic.hasPrefix("{")) {
            let json = try! NSJSONSerialization.JSONObjectWithData(order.topic.dataUsingEncoding(NSUTF8StringEncoding)!, options: []) as! [String: AnyObject]
            let attachments = (json["attachments"] as! String).componentsSeparatedByString(", ")
            for attachment in attachments {
                if (attachment.isEmpty) {
                    continue
                }
                
                let image = AppDelegate.instance.activeProfile!.loadImage(attachment)
                append(image: image!, time: order.started)
            }
            if (json["local"] as! Bool) {
                let location = json["location"] as! [String: AnyObject]
                self.location = CLLocationCoordinate2DMake(location["latitude"] as! CLLocationDegrees, location["longitude"] as! CLLocationDegrees)
                append(location: CLLocation(latitude: self.location!.latitude, longitude: self.location!.longitude), time: order.started)
            }
        }
    }
    
    override var type: CellType {
        return .Setup
    }
    
    var images: [UIImage] = []
    var location: CLLocationCoordinate2D?

    override func height(maxWidth width: CGFloat) -> CGFloat {
        return SetupChatCell.height(contentHeight: super.height(maxWidth: width))
    }
    
    override func accept(message: ExpLeagueMessage) -> Bool {
        return message.type == .Topic
    }

    func formatPeriodRussian(interval: NSTimeInterval) -> String {
        let formatter = NSDateFormatter()
        formatter.dateFormat = "H'ч 'mm'м'"
        formatter.timeZone = NSTimeZone(forSecondsFromGMT: 0)
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
        text += formatter.stringFromDate(NSDate(timeIntervalSinceReferenceDate: interval))
        return text;
    }
    
    @objc
    func advanceTimer(timer: NSTimer) {
        let label = timer.userInfo as! UILabel
        let timeLeft = order.before - CFAbsoluteTimeGetCurrent()
        switch(order.status) {
        case .Open, .ExpertSearch:
            label.textColor = UIColor.lightGrayColor()
            label.text = "ОТКРЫТО. Осталось: " + formatPeriodRussian(timeLeft)
            break
        case .Overtime:
            label.textColor = OngoingOrderStateCell.ERROR_COLOR
            label.text = "ПРОСРОЧЕНО НА: " + formatPeriodRussian(-timeLeft)
            break
        case .Canceled:
            label.textColor = UIColor.yellowColor()
            label.text = "ОТМЕНЕНО"
            break
        case .Closed:
            label.textColor = OngoingOrderStateCell.GREEN_COLOR
            label.text = "ВЫПОЛНЕНО"
            break
        default:
            break
        }
    }
    override func form(chatCell cell: ChatCell) throws {
        guard let setupCell = cell as? SetupChatCell else {
            throw ModelErrors.WrongCellType
        }
        SetupModel.timer?.invalidate()
        SetupModel.timer = NSTimer.scheduledTimerWithTimeInterval(1, target: self, selector: "advanceTimer:", userInfo: setupCell.label, repeats: true)
        advanceTimer(SetupModel.timer!)
        try super.form(chatCell: setupCell)
    }
}

class ExpertInProgressModel: ChatCellModel {
//    let listener: OrderTracker
    var expertProperties = NSMutableDictionary()
    var pagesCount = 0
    var type: CellType {
        return .ExpertInProgress
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return ExpertInProgressCell.height + 8
    }

    func form(chatCell cell: ChatCell) throws {
        guard let eipCell = cell as? ExpertInProgressCell else {
            throw ModelErrors.WrongCellType
        }
        eipCell.pages = pagesCount
        eipCell.name.text = expertProperties["name"] as? String
        eipCell.expertAvatar.image = AppDelegate.instance.activeProfile!.avatar(expertProperties["login"] as! String, url: expertProperties["login"] as? String)
        eipCell.expertAvatar.layer.cornerRadius = 10;
        eipCell.expertAvatar.clipsToBounds = true;

        eipCell.action = {
            self.order.cancel()
        }
        if (eipCell.progress != nil) {
            if (AppDelegate.instance.stream.isConnected()) {
                eipCell.progress.startAnimating()
            } else {
                eipCell.progress.stopAnimating()
            }
        }
    }
    func accept(message: ExpLeagueMessage) -> Bool {
        expertProperties.addEntriesFromDictionary(message.properties as [NSObject : AnyObject])
        if (message.type == .ExpertProgress) {
            let type = message.properties["type"] as? String
            if (type != nil && type! == "pageVisited") {
                pagesCount++
            }
        }
        return message.type == .ExpertProgress || message.type == .ExpertAssignment
    }
    
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
    }
}

class FeedbackModel: ChatCellModel {
    //    let listener: OrderTracker
    var type: CellType {
        return .Feedback
    }
    
    func height(maxWidth width: CGFloat) -> CGFloat {
        return FeedbackCell.height + 24
    }
    
    func form(chatCell cell: ChatCell) throws {
        guard let feedbackCell = cell as? FeedbackCell else {
            throw ModelErrors.WrongCellType
        }
        feedbackCell.action = {
            self.order.close(stars: 5)
        }
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        return false
    }
    
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
    }
}

class LookingForExpertModel: ChatCellModel {
    weak var cell: LookingForExpertCell?
    var active = true
    
    var type: CellType {
        return .LookingForExpert
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return LookingForExpertCell.height
    }

    func form(chatCell cell: ChatCell) throws {
        guard let lfeCell = cell as? LookingForExpertCell else {
            throw ModelErrors.WrongCellType
        }
        self.cell = lfeCell
        lfeCell.action = {
            self.order.cancel()
        }
        if (lfeCell.progress != nil) {
            if (AppDelegate.instance.stream.isConnected()) {
                lfeCell.progress.startAnimating()
            } else {
                lfeCell.progress.stopAnimating()
            }
        }
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        return false
    }
    
    var tracker: XMPPTracker?
    let order: ExpLeagueOrder
    init (order: ExpLeagueOrder){
        self.order = order
        tracker = XMPPTracker(onPresence: {(presence: XMPPPresence) -> Void in
            let statuses = try! presence.nodesForXPath("//*[local-name()='status' and namespace-uri()='http://expleague.com/scheme']")
            if statuses.count > 0, let status = statuses[0] as? DDXMLElement {
                self.cell?.online = status.attributeIntegerValueForName("experts-online", withDefaultValue: 0)
            }
        })
        order.parent.track(tracker!)
    }
}

class AnswerReceivedModel: ChatCellModel {
    var expertProperties: NSDictionary?
    let id: String
    var type: CellType {
        return .AnswerReceived
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return AnswerReceivedCell.height + 8
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        guard message.type == .Answer else {
            return false
        }
        return true
    }

    func form(chatCell cell: ChatCell) throws {
        guard let arCell = cell as? AnswerReceivedCell else {
            throw ModelErrors.WrongCellType
        }
        try progress.form(chatCell: arCell)
        arCell.id = id
    }

    let progress: ExpertInProgressModel
    init(id: String, progress: ExpertInProgressModel) {
        self.id = id
        self.progress = progress
    }
}