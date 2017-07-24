//
//  AnswerViewController.swift
//  unSearch
//
//  Created by Igor E. Kuralenok on 11.07.17.
//  Copyright © 2017 Experts League Inc. All rights reserved.
//

import UIKit
import FBSDKCoreKit
import QuickLook
import MobileCoreServices

import unSearchCore

class AnswerViewController: UIViewController {
    var text: String = "" {
        willSet (newValue){
            if (text != newValue) {
                if (isViewLoaded) {
                    let path = Bundle.main.bundlePath
                    let baseURL = URL(fileURLWithPath: path);
                    webView.loadHTMLString("<html><head><script src=\"md-scripts.js\"></script>\n"
                        + "<link rel=\"stylesheet\" href=\"markdownpad-github.css\"></head>"
                        + "<body>\(newValue)</body></html>", baseURL: baseURL)
                    (parent as? OrderDetailsViewController)?.stateChanged()
                }
            }
        }
    }
    var data: ChatModel!
    var action: (() -> ())? = nil
    var answerDelegate: AnswerDelegate?
    
    @IBOutlet weak var bottomWebViewConstraint: NSLayoutConstraint!
    @IBOutlet weak var webView: UIWebView!
    @IBOutlet weak var scoreButton: UIButton!
    @IBAction func score(_ sender: Any) {
        action?()
    }
    
    fileprivate let renderer = BNHtmlPdfKit(pageSize: BNPageSizeA4)!
    func shareAnswer() {
        let path = Bundle.main.bundlePath
        let baseURL = URL(fileURLWithPath: path);
        let html = "<html><head>\n"
            + "<link rel=\"stylesheet\" href=\"markdownpad-github-pdf.css\"></head>"
            + "<body>\(text)</body></html>"
        let temp = URL(fileURLWithPath: NSTemporaryDirectory())
        let pdfUrl = URL(string: "unSearch-answer-" + data.order.id + ".pdf", relativeTo: temp)
        renderer.delegate = self
        renderer.baseUrl = baseURL
        renderer.saveHtml(asPdf: html, toFile: pdfUrl!.path)
        FBSDKAppEvents.logEvent("Share answer", parameters: ["user": ExpLeagueProfile.active.jid.user, "room": data.order.id])
    }
    
    func onStateChanged() {
        guard isViewLoaded else {
            return
        }
        switch(data.state) {
        case .save:
            bottomWebViewConstraint.constant = 64
            scoreButton.setTitle("СОХРАНИТЬ", for: .normal)
            action = {
                FBSDKAppEvents.logEvent("Save aow", parameters: [
                    "order": self.data.order.id
                    ])
                self.data.order.markSaved()
                self.data.state = .closed
                self.onStateChanged()
            }
        case .ask:
            bottomWebViewConstraint.constant = 64
            scoreButton.setTitle("ОЦЕНИТЬ ОТВЕТ", for: .normal)
            action = {
                self.navigationController?.pushViewController(self.feedback, animated: true)
            }
        default:
            bottomWebViewConstraint.constant = 0
            action = nil
        }
        view.layoutIfNeeded()
    }
    
    fileprivate var feedback: FeedbackViewController!
    override func viewDidLoad() {
        super.viewDidLoad()
        PurchaseHelper.instance.register([
            "com.expleague.unSearch.Star30r",
            "com.expleague.unSearch.Star75r",
            "com.expleague.unSearch.Star150r",
            "com.expleague.unSearch.Star300r",
            ])
        let path = Bundle.main.bundlePath
        let baseURL = URL(fileURLWithPath: path);
        scoreButton.layer.cornerRadius = scoreButton.frame.height / 2
        scoreButton.clipsToBounds = true
        
        webView.loadHTMLString("<html><head><script src=\"md-scripts.js\"></script>\n"
            + "<link rel=\"stylesheet\" href=\"markdownpad-github.css\"></head>"
            + "<body>\(text)</body></html>", baseURL: baseURL)
        answerDelegate = AnswerDelegate(parent: self, view: webView)
        webView.delegate = answerDelegate
        feedback = storyboard!.instantiateViewController(withIdentifier: "feedbackVC") as! FeedbackViewController
        feedback.owner = self
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        navigationController?.interactivePopGestureRecognizer?.isEnabled = true
        self.parent?.navigationItem.rightBarButtonItem = nil
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        (parent as! OrderDetailsViewController).stateChanged()
        navigationController?.interactivePopGestureRecognizer?.isEnabled = false
        parent?.navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .action, target: self, action: #selector(shareAnswer))
    }
}

extension AnswerViewController: BNHtmlPdfKitDelegate {
    func htmlPdfKit(_ htmlPdfKit: BNHtmlPdfKit!, didSavePdfFile file: String!) {
        let objectsToShare = [URL(fileURLWithPath: file)]
        let activityVC = UIActivityViewController(activityItems: objectsToShare, applicationActivities: nil)
        present(activityVC, animated: true, completion: nil)
    }
}


class PreviewItem: NSObject, QLPreviewItem {
    var previewItemTitle: String? {
        return ""
    }
    
    var previewItemURL: URL? {
        return url
    }
    
    var previewItemContentType: String? {
        return (kUTTypeImage as NSString) as String
    }
    
    let url: URL
    let contentType: String?
    
    init(url: URL, contentType: String?) {
        self.url = url
        self.contentType = contentType
    }
}

class AnswerDelegate: NSObject, UIWebViewDelegate, UIGestureRecognizerDelegate, QLPreviewControllerDataSource {
    func webView(_ webView: UIWebView, shouldStartLoadWith request: URLRequest, navigationType: UIWebViewNavigationType) -> Bool {
        if let url = request.url , url.scheme == "unsearch" {
            if (url.path == "/chat-messages") {
                if let indexStr = url.fragment, let index = Int(indexStr) {
                    let odvc = parent.parent as! OrderDetailsViewController
                    odvc.state = .chat
                    odvc.chat.messages.scrollToRow(at: parent.data.translateToIndex(index)!, at: .middle, animated: false)
                }
            }
            return false
        }
        else if let url = request.url , (url.scheme?.hasPrefix("http"))! {
            UIApplication.shared.openURL(url)
            return false
        }
        
        return true
    }
    
    var isImage: Bool = false
    var imageUrl: URL? = nil
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
    
    func gestureRecognizer(sender: UITapGestureRecognizer) {
        guard sender.state == .ended else {
            return
        }
        let touchPoint = sender.location(in: view)
        let imageSRC = String(format: "document.elementFromPoint(%f, %f).src", touchPoint.x, touchPoint.y)
        let src = view.stringByEvaluatingJavaScript(from: imageSRC)!.replacingOccurrences(of: "&amp;", with: "&")
        if !src.isEmpty {
            self.imageUrl = URL(string: src)
            if (imageUrl != nil && imageUrl!.scheme != nil && imageUrl!.host != nil) {
                isImage = true
                let preview = QLPreviewController()
                preview.dataSource = self
                parent.navigationController?.show(preview, sender: self)
            }
        }
    }
    
    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        guard let cache = EVURLCache.storagePath(url: imageUrl!) else {
            return imageUrl! as NSURL
        }
        return PreviewItem(url: URL(fileURLWithPath: cache), contentType: nil)
    }
    
    func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
        return imageUrl != nil ? 1 : 0
    }
    
    let parent: AnswerViewController
    let view: UIWebView
    var gr: UITapGestureRecognizer!
    init(parent: AnswerViewController, view: UIWebView) {
        self.parent = parent
        self.view = view
        super.init()
        gr = UITapGestureRecognizer(target: self, action: #selector(self.gestureRecognizer(sender:)))
        gr.delegate = self
        view.addGestureRecognizer(gr)
    }
}

