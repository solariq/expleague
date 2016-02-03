//
// Created by Igor E. Kuralenok on 26/01/16.
// Copyright (c) 2016 Experts League Inc. All rights reserved.
//

import Foundation
import UIKit
import MapKit
import XMPPFramework

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
    }
    
    override func accept(message: ExpLeagueMessage) -> Bool {
        if (message.from != author || message.isAnswer) {
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
        return message.type == .TopicStarter
    }

    func formatPeriodRussian(interval: NSTimeInterval) -> String {
        let formatter = NSDateFormatter()
        formatter.dateFormat = "H:mm:ss"
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
            label.textColor = UIColor.redColor()
            label.text = "ПРОСРОЧЕНО НА: " + formatPeriodRussian(-timeLeft)
            break
        case .Canceled:
            label.textColor = UIColor.yellowColor()
            label.text = "ОТМЕНЕНО"
            break
        case .Closed:
            label.textColor = UIColor.greenColor()
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
        eipCell.name.text = expertProperties["name"] as? String
        if let pagesCount = expertProperties["count"] as? Int {
            eipCell.pages = Int(pagesCount)
        }
        eipCell.action = {
            let order = self.mvc.order!
            order.cancel()
            self.mvc.order = order
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
        return message.type == .SystemMessage
    }
    
    let mvc: MessagesVeiwController
    init(mvc: MessagesVeiwController) {
        self.mvc = mvc
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
            let order = self.mvc.order!
            order.close(stars: 5)
            self.mvc.order = order
        }
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        return false
    }
    
    let mvc: MessagesVeiwController
    init(controller: MessagesVeiwController) {
        self.mvc = controller
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
            let order = self.mvc.order!
            order.cancel()
            self.mvc.order = order
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
    let mvc: MessagesVeiwController
    init (mvc: MessagesVeiwController){
        self.mvc = mvc
        tracker = XMPPTracker(onPresence: {(presence: XMPPPresence) -> Void in
            let statuses = try! presence.nodesForXPath("//*[local-name()='status' and namespace-uri()='http://expleague.com/scheme']")
            if statuses.count > 0, let status = statuses[0] as? DDXMLElement {
                self.cell?.online = status.attributeIntegerValueForName("experts-online", withDefaultValue: 0)
            }
        })
        AppDelegate.instance.activeProfile?.track(tracker!)
        mvc.order!.track(tracker!)
    }
}

class AnswerReceivedModel: ChatCellModel {
    var expertProperties: NSDictionary?
    let controller: MessagesVeiwController
    var id: String?
    var type: CellType {
        return .AnswerReceived
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return AnswerReceivedCell.height + 8
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        if (!message.isAnswer) {
            return false
        }
        controller.answerAppend("<div id=\"\(id)\"/>")
        id = "message-\(message.hashValue)"
        message.visitParts(AnswerVisitor(controller))
        return true
    }

    func form(chatCell cell: ChatCell) throws {
        guard let arCell = cell as? AnswerReceivedCell else {
            throw ModelErrors.WrongCellType
        }
        try progress.form(chatCell: arCell)
        arCell.action = {
            self.controller.scrollView.scrollRectToVisible(self.controller.answerView.frame, animated: true)
            self.controller.answerView.stringByEvaluatingJavaScriptFromString("document.getElementById('\(self.id!)').scrollIntoView()")
        }
    }
    
    class AnswerVisitor: ExpLeagueMessageVisitor {
        let parent: MessagesVeiwController
        init(_ parent: MessagesVeiwController) {
            self.parent = parent;
        }
        
        func message(message: ExpLeagueMessage, text: String) {
            parent.answerAppend("<p>\(text)</p>';")
        }
        
        func message(message: ExpLeagueMessage, title: String, text: String) {
            parent.answerAppend("<h3>\(title)</h3><p>\(text)</p>")
        }
        
        func message(message: ExpLeagueMessage, title: String, link: String) {
            parent.answerAppend("<a href=\"\(link)\">\(title)</a>")
        }
        func message(message: ExpLeagueMessage, title: String, image: UIImage) {
            let data = UIImageJPEGRepresentation(image, 1.0)!
            let screenWidth = AppDelegate.instance.window!.frame.width
            if (image.size.width > screenWidth - 20) {
                parent.answerAppend("<h3>\(title)</h3><img width='\(screenWidth - 20)' align='middle' src='data:image/jpeg;base64,\(data.base64EncodedStringWithOptions([]))'/>")
            }
            else {
                parent.answerAppend("<h3>\(title)</h3><img align='middle' src='data:image/jpeg;base64,\(data.base64EncodedStringWithOptions([]))'/>")
            }
        }
    }

    let progress: ExpertInProgressModel
    init(controller: MessagesVeiwController, progress: ExpertInProgressModel) {
        self.controller = controller
        self.progress = progress
    }
}