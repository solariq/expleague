//
//  FeedbackViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 11.07.17.
//  Copyright © 2017 Experts League Inc. All rights reserved.
//

import Foundation

import unSearchCore

class FeedbackViewController: UIViewController, UITextViewDelegate {
    @IBOutlet weak var commentButton: UIButton!
    @IBOutlet weak var scoreButton: UIButton!
    
    @IBOutlet weak var orderDetails: UITextView!
    @IBOutlet weak var scoreDetails: UITextView!
    @IBOutlet weak var comment: UITextView!
    
    @IBAction func fire(_ sender: AnyObject) {
        guard !busy else {
            return
        }
        let rate = self.rate ?? 0
        if (rate > 1) {
            busy = true
            let purchaseId = payments[rate - 2]
            PurchaseHelper.instance.request(purchaseId) {rc, payment in
                switch(rc) {
                case .accepted:
                    if !(self.comment.text.isEmpty || self.comment.text == "Ваш комментарий") {
                        self.owner.data.order.send(text: self.comment.text)
                    }
                    self.owner.data.order.feedback(stars: rate, payment: payment)
                    self.navigationController?.popViewController(animated: true)
                case .error:
                    let alert = UIAlertController(title: "unSearch", message: "Не удалось провести платеж!", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                    self.present(alert, animated: true, completion: nil)
                case .rejected:
                    let alert = UIAlertController(title: "unSearch", message: "Платеж отклонен!", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                    self.present(alert, animated: true, completion: nil)
                    break
                }
                self.busy = false
            }
        }
        else if (rate == 1) {
            if (comment.text.isEmpty || comment.text == "Ваш комментарий") {
                let alert = UIAlertController(title: "Оставьте комментарий", message: "Пожалуйста, расскажите нам что пошло не так. Эта информация необходима для улучшения качества услуг.", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                present(alert, animated: true, completion: nil)
            }
            else {
                owner.data.order.send(text: comment.text)
                owner.data.order.feedback(stars: rate, payment: nil)
                navigationController?.popViewController(animated: true)
            }
        }
        else {
            if (comment.text.isEmpty || comment.text == "Ваш комментарий") {
                let alert = UIAlertController(title: "Оставьте комментарий", message: "Для продолжения поиска необходимо уточнить тему. Сделайте это в комментарии.", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "Ok", style: .default, handler: nil))
                present(alert, animated: true, completion: nil)
            }
            else {
                owner.data.order.send(text: "Продолжаем поиск. Ваш комментарий: " + comment.text)
                navigationController?.popViewController(animated: true)
                (owner.parent as? OrderDetailsViewController)?.state = .chat
            }
        }
    }

    @IBOutlet var stars: [UIImageView]!
    
    let payments = [
        "com.expleague.unSearch.Star30r",
        "com.expleague.unSearch.Star75r",
        "com.expleague.unSearch.Star150r",
        "com.expleague.unSearch.Star300r"
    ]
    var rate: Int?
    fileprivate var busy = false
    fileprivate var keyboardTracker: KeyboardStateTracker?
    fileprivate weak var commentVC: CommentViewController?
    fileprivate var shown = false
    
    override func viewDidLoad() {
        keyboardTracker = KeyboardStateTracker(check: {true}){height in
            self.commentVC?.bottomConstraint?.constant = height
        }
        scoreButton.layer.cornerRadius = scoreButton.frame.height / 2
        scoreButton.clipsToBounds = true
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(self.handleTap(_:))))
        comment.delegate = self
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        updateDescription(self.rate, order: owner.data.order)
        if (comment.text.isEmpty || comment.text == "Ваш комментарий") {
            comment.isHidden = true
        }
        keyboardTracker?.start()
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if !(navigationController?.viewControllers.contains(self) ?? false) {
            comment.text = ""
            rate = nil
            keyboardTracker?.stop()
        }
    }
    
    func handleTap(_ recognizer: UITapGestureRecognizer) {
        let tap = recognizer.location(in: view)
        var minDistance = CGFloat.infinity;
        var rate = 0
        for i in 0..<stars.count {
            let rect = stars[i].frame
            let starCenter = CGPoint(x: rect.origin.x + rect.width / 2, y: rect.origin.y + rect.height / 2)
            let distance = self.distance(tap, starCenter)
            if (distance < 20 && distance < minDistance) {
                minDistance = distance
                rate = i + 1
            }
        }
        guard minDistance < CGFloat.infinity else {
            return
        }
        self.rate = self.rate != rate ? rate : nil
        updateDescription(self.rate, order: owner.data.order);
    }
    
    func updateDescription(_ rate: Int?, order: ExpLeagueOrder) {
        let pages = owner.data.lastAnswer?.progress.pagesCount ?? 0
        let calls = owner.data.lastAnswer?.progress.callsCount ?? 0
        orderDetails.text = "Чтобы найти ответ на ваш вопрос, эксперт просмотрел \(pages) страниц\(Lang.rusNumEnding(pages, variants: ["у", "ы", ""]))"
        orderDetails.text = orderDetails.text + (calls > 0 ? ", сделал \(calls) звон\(Lang.rusNumEnding(calls, variants: ["ок", "ка", "ков"]))." : ".")
        
        let realRate = rate ?? 0
        for i in 0..<stars.count {
            stars[i].isHighlighted = i < realRate
        }
        switch realRate {
        case 5:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Еще как! Ответ выше всяких похвал. Большое спасибо эксперту.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 14)]))
            text.append(NSAttributedString(string: "\n300р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 16)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ОЦЕНИТЬ", for: .normal)
        case 4:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Да. Хороший ответ. Именно это и ожидалось. Спасибо эксперту.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 14)]))
            text.append(NSAttributedString(string: "\n150р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 16)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ОЦЕНИТЬ", for: .normal)
        case 3:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Вполне. Нормальный ответ, но в нем есть что улучшить.\n\nСумма поощрения составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 14)]))
            text.append(NSAttributedString(string: "\n75р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 16)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ОЦЕНИТЬ", for: .normal)
        case 2:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Не совсем. Старались, но не смогли мне помочь.\n\nСумма пожертвования составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 14)]))
            text.append(NSAttributedString(string: "\n30р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 16)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ОЦЕНИТЬ", for: .normal)
        case 1:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Увы. Только зря потрачено время. Укажите в комментарии, что не понравилось.\n\nОплата составит:", attributes: [NSFontAttributeName: UIFont.systemFont(ofSize: 14)]))
            text.append(NSAttributedString(string: "\n0р", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 16)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ОЦЕНИТЬ", for: .normal)
        default:
            let text = NSMutableAttributedString()
            text.append(NSAttributedString(string: "Еще нет. Полученный ответ неполный.\nУкажите в комментарии, что требуется дополнить.", attributes: [NSFontAttributeName: UIFont.boldSystemFont(ofSize: 15)]))
            self.scoreDetails.attributedText = text
            self.scoreButton.setTitle("ПРОДОЛЖИТЬ ПОИСК", for: .normal)
        }
        self.scoreDetails.textAlignment = .center
        scoreButton.isEnabled = true
    }
    
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if let vc = segue.destination as? CommentViewController {
            vc.callback = {(text: String) -> () in
                self.comment.text = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if (!text.isEmpty) {
                    self.comment.isHidden = false
                }
            }
            if (!self.comment.text.isEmpty && self.comment.text != "Ваш комментарий") {
                vc.initText = self.comment.text
            }
            else {
                vc.initText = ""
            }
            vc.owner = self
            commentVC = vc
        }
    }
    
    func distance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let xDist = p2.x - p1.x
        let yDist = p2.y - p1.y
        return sqrt((xDist * xDist) + (yDist * yDist));
    }
    
    func textViewDidBeginEditing(_ textView: UITextView) {
        performSegue(withIdentifier: "editComment", sender: self)
    }
    
    var owner: AnswerViewController!
}

class CommentViewController: UIViewController {
    @IBOutlet weak var bottomConstraint: NSLayoutConstraint?
    @IBOutlet weak var commentText: UITextView!
    
    @IBAction func back(_ sender: Any) {
        navigationController?.popViewController(animated: true)
    }
    var callback: ((String) -> ())?
    var keyboardTracker: KeyboardStateTracker?
    var initText: String = ""
    var owner: FeedbackViewController!
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        self.commentText.becomeFirstResponder()
        self.commentText.delegate = self
        self.commentText.text = initText
        self.bottomConstraint?.constant = owner.keyboardTracker?.height ?? 0
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        callback?(commentText.text)
    }
}


extension CommentViewController: UITextViewDelegate {
    func textViewDidEndEditing(_ textView: UITextView) {
        self.navigationController?.popViewController(animated: true)
    }
}
