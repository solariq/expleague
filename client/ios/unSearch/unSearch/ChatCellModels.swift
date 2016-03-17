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
    func form(chatCell cell: UIView) throws
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
        model.append(text: text, time: message.ts)
    }
    
    func message(message: ExpLeagueMessage, title: String, text: String) {
        let result = NSMutableAttributedString()
        result.appendAttributedString(NSAttributedString(string: title, attributes: [
            NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleHeadline)
            ]))
        result.appendAttributedString(NSAttributedString(string: "\n" + text, attributes: [
            NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
            ]))
        model.append(richText: result, time: message.ts)
    }
    
    func message(message: ExpLeagueMessage, title: String, link: String) {
        model.append(
            richText: NSAttributedString(string: title, attributes: [
                NSFontAttributeName: UIFont.preferredFontForTextStyle(UIFontTextStyleBody),
                NSLinkAttributeName: link,
                NSForegroundColorAttributeName: UIColor.blueColor()
                ]),
            time: message.ts)
    }
    func message(message: ExpLeagueMessage, title: String, image: UIImage) {
        model.append(image: image, time: message.ts)
    }
}

class CompositeCellModel: ChatCellModel {
    let separatorHeight: CGFloat = 8

    private var parts:[AnyObject] = []
    private var timeStamps: [NSDate] = []
    var textAlignment: NSTextAlignment = .Left

    func append(text text: String, time: NSDate) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(richText text: NSAttributedString, time: NSDate) {
        parts.append(text)
        timeStamps.append(time)
    }

    func append(image img: UIImage, time: NSDate) {
        parts.append(img)
        timeStamps.append(time)
    }

    func append(location point: CLLocation, time: NSDate) {
        parts.append(point)
        timeStamps.append(time)
    }

    func append(action run: () -> Void, caption: String, time: NSDate) {
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
            let blockSize = self.blockSize(width: width - 12, index: i)
            height += blockSize.height
        }
        return ceil(height + 8)
    }

    func form(chatCell cell: UIView) throws {
        guard let messageViewCell = cell as? MessageChatCell else {
            throw ModelErrors.WrongCellType
        }
        form(messageViewCell: messageViewCell)
    }

    func form(messageViewCell cell: MessageChatCell) {
        for view in cell.content.subviews { // cleanup
            view.removeFromSuperview()
        }
        cell.content.autoresizesSubviews = false
        cell.content.autoresizingMask = .None
        var height: CGFloat = 0.0
        var width: CGFloat = 0.0
        var prev: UIView?

        for i in 0 ..< parts.count {
            var blockSize = self.blockSize(width: cell.maxWidth - 12, index: i)
            blockSize.width += 12
            var block: UIView?
            if let text = parts[i] as? String {
                let label = UITextView()
                label.editable = false
                label.dataDetectorTypes = .All
                label.font = ChatCell.defaultFont
                label.text = text
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
                width = max(width, blockSize.width)
                block!.translatesAutoresizingMaskIntoConstraints = false
                block!.addConstraint(NSLayoutConstraint(item: block!, attribute: .Width, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: blockSize.width))
                block!.addConstraint(NSLayoutConstraint(item: block!, attribute: .Height, relatedBy: .Equal, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: blockSize.height))

                if (prev != nil) {
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .Top, relatedBy: .Equal, toItem: prev!, attribute: .Bottom, multiplier: 1, constant: 0))
                }
                else {
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .Top, relatedBy: .Equal, toItem: cell.content, attribute: .TopMargin, multiplier: 1, constant: 0))
                }
                switch(self.textAlignment) {
                case .Left:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .Leading, relatedBy: .Equal, toItem: cell.content, attribute: .LeadingMargin, multiplier: 1, constant: 0))
                    break
                case .Center:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .CenterX, relatedBy: .Equal, toItem: cell.content, attribute: .CenterX, multiplier: 1, constant: 0))
                    break
                default:
                    cell.content.addConstraint(NSLayoutConstraint(item: block!, attribute: .Trailing, relatedBy: .Equal, toItem: cell.content, attribute: .TrailingMargin, multiplier: 1, constant: 0))
                    break

                }
                height += blockSize.height
                prev = block
            }
        }

        cell.content.addConstraint(NSLayoutConstraint(item: cell.content, attribute: .Height, relatedBy: .GreaterThanOrEqual, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: ceil(height + 10)))
        cell.content.addConstraint(NSLayoutConstraint(item: cell.content, attribute: .Width, relatedBy: .GreaterThanOrEqual, toItem: nil, attribute: .NotAnAttribute, multiplier: 1, constant: ceil(width + 12)))
        print("Cell: \(cell.dynamicType), content size: (\(width), \(height)), cell size: \(cell.frame.size)")
    }

    private func blockSize(width width: CGFloat, index: Int) -> CGSize {
        var blockSize: CGSize
        if let text = parts[index] as? String {
            blockSize = text.boundingRectWithSize(
                    CGSizeMake(width, CGFloat(MAXFLOAT)),
                    options: NSStringDrawingOptions.UsesLineFragmentOrigin,
                    attributes: [
                            NSFontAttributeName : ChatCell.defaultFont
                    ],
                    context: nil
                ).size
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
        return CGSizeMake(blockSize.width, blockSize.height)
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
    
    override func form(messageViewCell cell: MessageChatCell) {
        super.form(messageViewCell: cell)
//        if (incoming) {
//            (cell as! MessageChatCell).avatar.image = AppDelegate.instance.activeProfile!.avatar(author, url: nil)
//            (cell as! MessageChatCell).avatar.layer.cornerRadius = (cell as! MessageChatCell).avatar.frame.size.width / 2;
//            (cell as! MessageChatCell).avatar.clipsToBounds = true;
//        }
    }
    
    override func accept(message: ExpLeagueMessage) -> Bool {
        if (message.from != author || message.type == .Answer) {
            return false
        }

        return super.accept(message)
    }
}

class SetupModel: NSObject, ChatCellModel, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    var timer: NSTimer?
    let order: ExpLeagueOrder
    init(order: ExpLeagueOrder) {
        self.order = order
        super.init()
        for image in order.offer.images {
            do {
                let request = NSURLRequest(URL: image)
                let imageData = try NSURLConnection.sendSynchronousRequest(request, returningResponse: nil)
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
        return .Setup
    }
    
    var images: [UIImage] = []
    var location: CLLocationCoordinate2D?
    
    func textHeight(width: CGFloat) -> CGFloat {
        return ceil(order.text.boundingRectWithSize(
            CGSizeMake(width, CGFloat(MAXFLOAT)),
            options: NSStringDrawingOptions.UsesLineFragmentOrigin,
            attributes: [
                NSFontAttributeName : ChatCell.topicFont
            ],
            context: nil).size.height)
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return SetupChatCell.height(textHeight: textHeight(width), attachments: images.count + (order.offer.local ? 1 : 0))
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
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
        case .Open, .ExpertSearch, .Deciding:
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
    
    func form(chatCell cell: UIView) throws {
        guard let setupCell = cell as? SetupChatCell else {
            throw ModelErrors.WrongCellType
        }
        setupCell.topic.text = order.offer.topic
        setupCell.textHeight = textHeight(setupCell.textWidth)
        timer = NSTimer.scheduledTimerWithTimeInterval(1, target: self, selector: "advanceTimer:", userInfo: setupCell.status, repeats: true)
        setupCell.attachments = images.count + (order.offer.local ? 1 : 0)
        setupCell.attachmentsView.dataSource = self
        setupCell.attachmentsView.delegate = self
        advanceTimer(timer!)
    }
    
    func collectionView(collectionView: UICollectionView, canMoveItemAtIndexPath indexPath: NSIndexPath) -> Bool {
        return false
    }
    
    func collectionView(collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return section > 0 ? 0 : images.count + (order.offer.local ? 1 : 0)
    }
    
    func collectionView(collectionView: UICollectionView, cellForItemAtIndexPath indexPath: NSIndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCellWithReuseIdentifier("AttachmentCell", forIndexPath: indexPath) as! AttachmentCell
        if (indexPath.item >= images.count) {
            let mapView = MKMapView()
            let location = order.offer.location!
            let region = MKCoordinateRegionMake(location, MKCoordinateSpanMake(0.005, 0.005))
            mapView.setRegion(region, animated: false)
            mapView.setCenterCoordinate(location, animated: false)
            mapView.userInteractionEnabled = false
            let annotation = MKPointAnnotation()
            annotation.coordinate = location
            mapView.addAnnotation(annotation)
            cell.content = mapView
        }
        else {
            let image = UIImageView(image: images[indexPath.item])
            image.contentMode = UIViewContentMode.ScaleAspectFit
            image.backgroundColor = UIColor.whiteColor()
            cell.content = image
        }
        return cell
    }
    
    func collectionView(collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAtIndexPath indexPath: NSIndexPath) -> CGSize {
        let max = CGSizeMake(collectionView.frame.width - 32, collectionView.frame.height - 4)
        if indexPath.item < images.count {
            let image = images[indexPath.item]
            return CGSizeMake(image.size.width / image.size.height * max.height, max.height)
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
            throw ModelErrors.WrongCellType
        }
        
        eipCell.name.text = expert!.name
        eipCell.avatar.image = expert!.avatar
        eipCell.avatar.online = expert!.available
        if (status) {
            eipCell.status.text = "Работает над вашим заказом"
            eipCell.status.textColor = Palette.COMMENT
        }
        else {
            eipCell.status.text = "Отказался от задания"
            eipCell.status.textColor = Palette.ERROR
        }
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        if (message.type == .ExpertAssignment) {
            let expert = message.expert!
            if (self.expert == nil) {
                self.expert = expert
                return true
            }
            return self.expert!.login == expert.login
        }
        else if (message.type == .ExpertCancel) {
            status = false
            return false
        }
        return false
    }
    
    var type: CellType {
        return .Expert
    }
    
    var expert: ExpLeagueMember?
    var status = true
}

class TaskInProgressModel: ChatCellModel {
//    let listener: OrderTracker
    var expertProperties = NSMutableDictionary()
    var pagesCount = 0
    var type: CellType {
        return .TaskInProgress
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return TaskInProgressCell.height
    }

    func form(chatCell cell: UIView) throws {
        guard let eipCell = cell as? TaskInProgressCell else {
            throw ModelErrors.WrongCellType
        }
        eipCell.pages = pagesCount
        if (eipCell.action == nil) {
            eipCell.action = {() -> Void in
                self.order.cancel()
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

class LookingForExpertModel: ChatCellModel {
    weak var cell: LookingForExpertCell?
    var active = true
    
    var type: CellType {
        return .LookingForExpert
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return LookingForExpertCell.height
    }

    func form(chatCell cell: UIView) throws {
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
    var score: Int?
    var type: CellType {
        return .AnswerReceived
    }

    func height(maxWidth width: CGFloat) -> CGFloat {
        return AnswerReceivedCell.height
    }
    
    func accept(message: ExpLeagueMessage) -> Bool {
        if (message.type == .Feedback) {
            score = message.properties["stars"] as? Int
        }
        guard message.type == .Answer || message.type == .Feedback else {
            return false
        }
        return true
    }

    func form(chatCell cell: UIView) throws {
        guard let arCell = cell as? AnswerReceivedCell else {
            throw ModelErrors.WrongCellType
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