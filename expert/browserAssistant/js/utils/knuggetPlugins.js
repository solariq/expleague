'use strict';

//@@ $("ELEMENT") scrapping around
(function ($) {

    $.expr[':'].regex = function (elem, index, match) {
        var matchParams = match[3].split(','),
            validLabels = /^(data|css):/,
            attr = {
                method: matchParams[0].match(validLabels) ?
                    matchParams[0].split(':')[0] : 'attr',
                property: matchParams.shift().replace(validLabels, '')
            },
            regexFlags = 'ig',
            regex = new RegExp(matchParams.join('').replace(/^\s+|\s+$/g, ''), regexFlags);
        return regex.test(jQuery(elem)[attr.method](attr.property));
    };

    $.fn.classes = function () {
        var classes = [];
        $.each(this, function (i, v) {
            var splitClassName = v.className.split(/\s+/);

            for (var j in splitClassName) {
                var className = splitClassName[j];
                if (className) {
                    if (classes.indexOf(className === -1)) {
                        classes.push(className);
                    }
                }
            }
        });
        return classes;
    };

    $.fn.nodesArray = function () {
        var arr = [];

        $(this).find("div, span, p, table, td, tr, li, a").each(function () {
            var nodeType = $(this).get(0).tagName.toLowerCase();

            arr.push(nodeType);

            var classes = $(this).classes();
            if (classes.length) {
                $.each(classes, function (i, v) {
                    arr.push(nodeType + "." + v);
                });
            }

            if ($(this).attr('id')) {
                arr.push(nodeType + "#" + $(this).attr('id'));
            }
        });

        return arr;
    };

    $.fn.knuggetScrapping = function (action) {

        if (action === 'destroy') {
            destroy();
            return true;
        }

        var catchedDom = "knuggetCatched";
        var checkedDomName = "knugget_knugget_knugget";
        var wordsLimit = 200;

        var el = $(this); //MAIN OBJECT !!!!

        //If selector have similar blocks but different content;
        var selection = getElementWrap(el);

        if (selection == undefined) {
            selection = getElementWrapByOne(el);
        }


        var text = "";
        if (selection != undefined) {
            selection.addClass(catchedDom);
            text = getTextFromWrap(selection);
        }

        return text;

        //@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

        function cleanArray(a) {
            var newArray = new Array();
            for (var i = 0; i < a.length; i++) {
                if (a[i] && a[i].length > 3 && a[i].indexOf("<!--") === -1 && a[i].indexOf("-->") === -1) {
                    newArray.push(a[i]);
                }
            }

            return newArray;
        }

        function cleanText(string) {
            return string
                .replace(/(\r\n|\n|\r)/gm, " ")
                .replace(/[&\/\\#,+=—•()$~%.\-!'"„“:;_*?<>|{}]/g, " ")
                .trim().replace(/\s+/g, " ")
                .toLowerCase();
        }

        function getUniqueValuesArray(a) {
            return a.filter(function (elem, pos) {
                return a.indexOf(elem) == pos;
            });
        }

        function getElementWrap(elem) {
            //******* lyginimas pagal layout'us ***********************************************************************
            var selectedByNodes, selectedByClasses;

            elem.parents().each(function () {

                var $this = $(this);

                var firstLevelNodes = $this.nodesArray();
                $this.addClass(checkedDomName);

                $this.parent().children().not("." + checkedDomName).each(function () {

                    var childLevelNodes = $(this).nodesArray();

                    //lyginam ar layout'ai panasus
                    var sames = [];
                    var firstLevelNodesClone = firstLevelNodes.slice(0);
                    $.grep(childLevelNodes, function (elm) {

                        if ($.inArray(elm, firstLevelNodesClone) !== -1) {
                            sames.push(elm);
                            firstLevelNodesClone.splice($.inArray(elm, firstLevelNodesClone), 1);
                        }
                    });

                    var skirtumas = (sames.length / firstLevelNodes.length) * 100;

                    if (skirtumas > 70) {
                        selectedByNodes = $this;
                        return false;
                    }
                });

                if (selectedByNodes) {
                    return false;
                }
            });



            if (selectedByNodes == undefined) {
                elem.parents().each(function (i) {
                    var $this = $(this);
                    var nodeType = $this.get(0).tagName.toLowerCase();
                    var classes = cleanArray($this.classes());
                    if (classes.length > 0) {
                        for (i = 0; i < classes.length; i++) {
                            if ($this.parent().children(nodeType + "." + classes[i]).not("." + checkedDomName).length > 0) {
                                selectedByClasses = $this;
                                return false;
                            }
                        }
                    }
                });
            }

            $("." + checkedDomName).removeClass(checkedDomName);

            return selectedByNodes || selectedByClasses;
        }

        function getElementWrapByOne(elem) {

            var wrap;
            elem.parents().each(function () {
                if ($(this).children("h1").length || $(this).children(":regex(class,article), :regex(class,content)").length) {
                    wrap = $(this).parent();
                    return false;
                }
            });

            return wrap;
        }

        function getPopularWords(a) {
            var p = {};

            $.each(a, function (i, v) {
                if (p[v]) {
                    p[v]++;
                } else {
                    p[v] = 1;
                }
            });

            var s = [];
            for (var key in p) {
                s.push({
                    key: key,
                    value: p[key]
                });
            }

            s.sort(function (x, y) {
                return y.value - x.value;
            });

            var pp = [];
            for (var i = 0; i < s.length; i++) {
                pp.push(s[i].key);
            }

            return pp;
        }

        function getTextFromWrap(elem) {

            var elmClone = elem.clone();

            $(elmClone).find("" +
                "script,noscript,style,iframe,input" +
                ",:regex(href,javascript)" +
                ",:regex(class,relate)" +
                ",:regex(class,date),:regex(class,time):not(:regex(class,timeline)),:regex(class,timestamp)" +
                ",:regex(class,loader),:regex(class,loading)" +
                ",:regex(class,count):not(':regex(class,account)'),:regex(class,vote),:regex(class,signature),:regex(class,author)" +
                ",:regex(class,read),:regex(class,more)" +
                ",:regex(class,button)" +
                ",:regex(class,links),:regex(class,bar)" +
                ",:regex(class,thumb),:regex(id,thumb)" +
                ",:regex(class,comment)" +
                ",:regex(class,hidden),:regex(class,hide),:regex(css:display, ^none$)" +
                ",:regex(class,share-button),:regex(id,share_button),footer" +
                ",:regex(class,translate)" +
                ",:regex(class,rg_meta)" +
                ",:regex(class,source)" +
                ",:regex(class,bottom)" +
                ",:regex(class,rg_bb_i_meta)" +
                ",:regex(class,footer),:regex(id,footer),footer" +
                ",*[class*='uiStreamFooter']" + //facebook
                ",textarea" + //facebook
                "").not(":regex(class,article),:regex(class,collapse)").remove();

            elmClone.find("*").each(function () {
                $(this).after(" ").before(" ");
            });

            var trimText = elmClone.text() + " " + window.location.host.replace("www.", "") + " " + document.title;

            trimText = cleanText(trimText);
            trimText = trimText.split(" ");

            //Collecting metadata
            var metaChecker = ["title", "description", "keywords", "og:site_name", "og:type", "og:description", "twitter:title", "title:description"];
            var metas = document.getElementsByTagName('meta');
            for (var x = 0, y = metas.length; x < y; x++) {

                var name = metas[x].name != undefined && metas[x].name != "" ? metas[x].name.toLowerCase() : null;
                var property = metas[x].property != undefined && metas[x].property != "" ? metas[x].property.toLowerCase() : null;
                name = name || property;

                if ($.inArray(name, metaChecker) > -1) {

                    var content = cleanText(metas[x].content);
                    var contentArray = content.split(" ");

                    trimText = trimText.concat(contentArray);
                }
            }

            var cleanedText = cleanArray(trimText);

            if (cleanedText.length > wordsLimit) {
                cleanedText = getPopularWords(cleanedText);
            } else {
                cleanedText = getUniqueValuesArray(cleanedText);
            }

            cleanedText = cleanedText.slice(0, wordsLimit).join(" ");

            $(elmClone).remove();

            return cleanedText;
        }

        function destroy() {
            $("." + catchedDom).removeClass(catchedDom);
            $("." + checkedDomName).removeClass(checkedDomName);
        }
    };

    //@@ Get data about $("ELEMENET") like url, src, title, sitepage, ...
    $.fn.knuggetCollection = function (callback) {


        var dragElmnt = this;
        var collection = KNUGGET.Drag.Data;

        var maker = {

            /*Facebook*/
            Drag_fb_text: function () {
                this.Drag_d_text();
                this.Get_fb_referer();
            },

            Drag_fb_picture: function () {

                if (dragElmnt.hasClass("shareMediaPhoto")) {
                    var bgUrl = dragElmnt.css('background-image');
                    bgUrl = /^url\((['"]?)(.*)\1\)$/.exec(bgUrl);
                    bgUrl = bgUrl ? bgUrl[2] : "";
                    collection.Image = bgUrl;
                } else {
                    collection.Image = dragElmnt.attr('src');
                }

                if (dragElmnt.parent().hasClass("uiVideoThumb")) {
                    collection.Type = "video";
                    var sharetext = dragElmnt.nearest("a.shareText");
                    collection.Href = correctUrl(sharetext.attr("href"));
                    collection.Title = sharetext.text();
                }

                this.Get_fb_referer();
            },

            Drag_fb_apicture: function () {

                var apicture = dragElmnt.find("img");
                collection.Image = apicture.attr('src');
                collection.Referer = correctUrl(dragElmnt.attr("href"));

                //drag image from fb chat
                if (dragElmnt.closest(".conversation").length && dragElmnt.hasClass("_ksh")) {
                    collection.Type = "picture";
                    collection.Image = collection.Href = correctUrl(dragElmnt.attr("href"));
                }

                this.Get_fb_referer();
            },

            Drag_fb_link: function () {

                var apicturedivCounter = dragElmnt.children("div").length;
                var apictureCounter = dragElmnt.find("img").length;

                if (apictureCounter == 1 && apicturedivCounter) {
                    collection.Type = "picture";
                    dragElmnt = dragElmnt.find("img:first");
                    this.Drag_fb_picture();
                } else {
                    collection.Href = correctUrl(dragElmnt.attr("href"));
                    collection.Title = dragElmnt.text();
                }

                this.Get_fb_referer();
            },

            Drag_fb_video: function () {
                this.Drag_d_video();

                this.Get_fb_referer();
            },

            Get_fb_referer: function () {

                var postUrl = dragElmnt.nearest("a._5pcq");
                var postTitle = dragElmnt.parents('.mtm').find("._6m6 a");

                if (postUrl.length && postUrl.is(':not(.uiStreamPrivacy)')) {
                    collection.Href = correctUrl(postUrl.attr("href"));
                } else {
                    collection.Href = correctUrl(postTitle.attr("href"));
                }

                if (postTitle.length) {
                    collection.Title = postTitle.text();

                    if (!collection.Referer) {
                        collection.Referer = window.location.href;
                    }

                    collection.Type = "link";
                }

            },

            /*Ffffound*/
            Drag_ffffound_picture: function () {
                if (dragElmnt.parents(".related_to_item_xs").length || dragElmnt.parents(".related_to_item").length || dragElmnt.parents(".more_images_item").length) {
                    //Small ffffound images
                    collection.Image = dragElmnt.attr('src');
                }
            },

            Drag_ffffound_apicture: function () {
                dragElmnt = dragElmnt.find("img");
                this.Drag_ffffound_picture();
                collection.Referer = dragElmnt.attr("href");
            },

            /*Flickr*/
            //TODO: need refactoring flickr image catcher
            Drag_flickr_picture: function () {
                var details;
                if (dragElmnt.hasClass("pc_img") && dragElmnt.parents(".photo-display-item").length) {
                    details = dragElmnt.parent().attr("href").split("/");
                    var ownerId = details[4];
                    var photoId = details[5];
                    collection.Src_location = "http://www.flickr.com/photos/" + ownerId + "/" + photoId + "/sizes/o/in/photostream";
                } else if (dragElmnt.hasClass("nextprev_thumb")) {
                    details = dragElmnt.parent().attr("href").split("/");
                    collection.ownerID = details[2];
                    collection.photoID = details[3];
                }
            },

            /*Google*/
            Drag_google_apicture: function () {
                dragElmnt = dragElmnt.find("img:first");
                collection.Referer = dragElmnt.attr("href");
                this.Drag_google_picture();
            },

            Drag_google_picture: function () {
                if (dragElmnt.hasClass("rg_i") && dragElmnt.parent().attr('href') && dragElmnt.parent().attr('href').indexOf('imgurl=') != -1) {

                    var ghref = dragElmnt.parent().attr('href');
                    //Check if exist file
                    var imgurl = KNUGGET.urlParam('imgurl', ghref);

                    collection.Image = imgurl;

                    collection.Referer = KNUGGET.urlParam('imgrefurl', ghref);

                    var meta = $.parseJSON(dragElmnt.nearest(".rg_meta").text());
                    if (meta["pt"] != null) {
                        collection.Keywords = meta["pt"];
                    }

                    collection.Keywords = collection.Keywords + " " + document.title;

                } else if (dragElmnt.attr('id') == "irc_mi") {
                    collection.Referer = dragElmnt.nearest(".irc_vpl").attr('href');
                } else if (dragElmnt.attr('class')) {
                    if (dragElmnt.attr('class').indexOf("vidthumb") != -1) {
                        collection.Type = "video";
                        var link = dragElmnt.nearest("h3 a");
                        collection.Referer = link.attr("href");
                    }
                }
            },

            /*Pinterest*/
            Drag_pinterest_picture: function () {
                collection.Image = dragElmnt.attr("src");
                collection.Title = dragElmnt.attr('alt');
            },

            Drag_pinterest_apicture: function () {

                var apicture = dragElmnt.find("img");

                collection.Image = apicture.attr('src');
                collection.Title = apicture.attr('alt');
                collection.Type = 'link';
                collection.Referer = correctUrl(dragElmnt.attr("href"));

            },

            /*Tumblr*/
            Drag_tumblr_picture: function () {
                if (dragElmnt.hasClass("image")) {
                    //
                } else if (dragElmnt.parent().hasClass("photoset_photo")) {
                    collection.Image = dragElmnt.parent().attr("href");
                }

                //Referer to post
                this.Get_tumblr_referer();

                /*
                ******************************************************
                //ON SERVER NEED CHECK EXIST 1280 or not, if yes, then get this.
                ******************************************************
                */
            },

            Drag_tumblr_text: function () {
                this.Drag_d_text();

                this.Get_tumblr_referer();
            },

            Get_tumblr_link: function () {
                if (dragElmnt.hasClass("click_glass")) {
                    var photoStageImg = dragElmnt.parents(".post_content").find(".photo_stage_img");
                    if (photoStageImg.length && photoStageImg.css('background-image').length) {
                        var bgUrl = photoStageImg.css('background-image');
                        bgUrl = /^url\((['"]?)(.*)\1\)$/.exec(bgUrl);
                        bgUrl = bgUrl ? bgUrl[2] : "";
                        collection.Image = bgUrl;
                        collection.Type = "picture";
                    } else {
                        collection.Href = correctUrl(dragElmnt.attr('href'));
                    }

                    collection.Keywords = dragElmnt.find(".post_tags").text();
                }

                //Referer to post
                this.Get_tumblr_referer();
            },

            Get_tumblr_referer: function () {
                //Referer to post
                var source = dragElmnt.nearest("a.post_permalink");
                if (!source.length) {
                    source = dragElmnt.nearest("a.tumblelog_info");
                    if (!source.length) {
                        source = dragElmnt.nearest(".reblog_source .post_info_link");
                        if (!source.length) {
                            source = dragElmnt.nearest(".post_info_link");
                        }
                    }
                }
                if (source.length) {
                    collection.Referer = source.attr("href");
                }
            },


            /*Twitter*/
            Drag_twitter_picture: function () {
                collection.Image = dragElmnt.attr("src");
                if (collection.Image.indexOf("/profile_images/") != -1) {
                    collection.Image = collection.Image; //.replace("_normal.jpg", ".jpg");
                }
            },

            /*Youtube.com*/
            Drag_youtube_picture: function () {
                if (dragElmnt.parents("a[href*='/watch?v=']").length) {
                    collection.Type = "video";
                    collection.Provider = "youtube";
                    collection.Href = correctUrl(dragElmnt.attr("src"));
                }
            },

            /*Vimeo*/
            Drag_vimeo_picture: function () {
                if (dragElmnt.parents("li[id^='clip_']").length) {
                    collection.Type = "video";
                    collection.Provider = "vimeo";
                    collection.Href = "http://vimeo.com" + dragElmnt.parent("a").attr("href");
                }
            },


            /*9gag gif*/
            Drag_ninegag_apicture: function () {

                var gifSource = dragElmnt.find('.badge-animated-container-animated').attr('data-image');

                if (gifSource) {
                    collection.Image = gifSource;
                } else {
                    collection.Image = dragElmnt.find('img').attr('src');
                }

                collection.Href = dragElmnt.attr('href');
                collection.Title = dragElmnt.parents('article').find('h2').text();
            },

            Drag_ninegag_link: function () {

                var childElement = dragElmnt.find('.badge-animated-container-animated');

                var gifSource = childElement.find('img').attr('src');
                if (gifSource) {
                    collection.Type = "picture";
                    collection.Image = gifSource;
                    collection.Href = dragElmnt.attr('href');
                    collection.Title = dragElmnt.parents('article').find('h2').text();
                }

                //grab gif
                var dataImage = childElement.attr("data-image");
                if (dataImage && dataImage.length && dataImage.indexOf(".gif") !== -1) {
                    collection.Type = "picture";
                    collection.Image = dataImage;
                    collection.Href = dragElmnt.attr('href');
                    collection.Title = dragElmnt.parents('article').find('h2').text();
                }
            },

            /*Feedly*/
            Drag_feedly_picture: function () {
                this.Get_feedly_referer();
            },

            Drag_feedly_address_bar: function () {
                var inlineFrame = $(".inlineFrame");
                if (inlineFrame.length && inlineFrame.is(":visible")) {
                    var title = inlineFrame.find("a.entryTitle");
                    collection.Title = title.length ? title.text() : "";
                    collection.Href = title.length ? title.attr("href") : "";
                }

                this.Drag_d_address_bar();
            },

            Get_feedly_referer: function () {
                var referer = dragElmnt.nearest(".title");
                if (referer.length) {
                    collection.Referer = referer.attr("href");
                }
            },

            //shutterstock.com
            Drag_shutterstock_picture: function () {

                collection.Image = dragElmnt.attr('src');
                collection.Title = dragElmnt.attr('alt');

                if (dragElmnt.parents('.mosaic_cell').length) {
                    collection.Href = correctUrl(dragElmnt.closest('a').attr("href"));
                }

                if (!collection.Referer) {
                    collection.Referer = window.location.href;
                }

            },

            //dribble.com
            Drag_dribbble_picture: function () {

                collection.Title = dragElmnt.attr('alt');

                if (!collection.Referer) {
                    collection.Referer = window.location.href;
                }

                this.Drag_d_picture();

            },

            //dribbble.com grid
            Drag_dribbble_link: function () {

                collection.Title = dragElmnt.find('strong').text();
                collection.Href = correctUrl(dragElmnt.attr("href"));
                collection.Type = 'link';

                if (!collection.Referer) {
                    collection.Referer = window.location.href;
                }

                dragElmnt = dragElmnt.closest('.dribbble-img').find("img:first");
                this.Drag_d_picture();

            },


            Drag_behance_picture: function () {

                collection.Title = dragElmnt.attr('alt');
                this.Drag_d_picture();

            },

            Drag_behance_link: function () {

                collection.Title = dragElmnt.attr('alt');

                if (dragElmnt.closest('.cover-img-link').length) {

                    collection.Href = correctUrl(dragElmnt.closest('.cover-img-link').attr("href"));
                    collection.Type = 'link';

                    if (!collection.Referer) {
                        collection.Referer = window.location.href;
                    }

                }

                this.Drag_d_picture();

            },

            /* ==========================================================================
            DEFAULT PARSERS
            ========================================================================== */

            Drag_d_picture: function () {

                var src = dragElmnt.attr('src');

                if (src.indexOf("data:image/") === 0) {

                    collection.Base64Image = src;

                } else if (src.indexOf("data:image/") !== -1) {

                    src = src.replace(/(\r\n|\n|\r)/gm, "").replace(/ /g, '');

                    if (src.indexOf("data:image/") === 0) {
                        collection.Base64Image = src;
                    } else {
                        collection.Image = src;
                    }

                } else {

                    collection.Image = correctUrl(dragElmnt.attr('src'));

                }
            },

            Drag_d_apicture: function () {
                dragElmnt = dragElmnt.find("img:first");
                this.Drag_d_picture();
            },

            Drag_d_link: function () {
                collection.Href = collection.Href ? collection.Href : correctUrl(dragElmnt.attr('href'));
                collection.Title = collection.Title ? collection.Title : dragElmnt.text();
                collection.Referer = collection.Referer ? collection.Referer : window.location.href;
                this.Drag_d_apicturebg();
            },

            Drag_d_apicturebg: function () {

                var desc = [];
                desc[0] = dragElmnt.attr('title');
                desc[1] = dragElmnt.text();
                desc = cleanArray(desc);

                collection.Keywords = desc.join(" ");

                //var backgroundUrlMatch = new RegExp(/\((.*?)\)/);
                //var backgroundImages = new Array();
                //var image;

                //// Capture background URL if it has one
                //if (backgroundUrlMatch.test(dragElmnt.css('background-image'))) {
                //    image = dragElmnt.css('background-image').match(backgroundUrlMatch)[1].replace(/('|")/g,'');
                //} else {

                //    dragElmnt.find('*').each(function(key, element) {

                //        var backgroundUrl = $(element).css('background-image');

                //        if (backgroundUrlMatch.test(backgroundUrl)) {
                //            image = backgroundUrl.match(backgroundUrlMatch)[1].replace(/('|")/g,'');
                //            return false;
                //        }
                //    })
                //}

                //if (image) {
                //    collection.Image = image;
                //}

                collection.Type = 'link';

                if (!collection.Referer) {
                    collection.Referer = correctUrl(dragElmnt.attr("href"));
                    collection.Href = correctUrl(dragElmnt.attr("href"));
                }
            },

            Drag_d_text: function () {
                var selection = "";
                var sel;
                var container;
                var i;
                var eventWindow = KNUGGET.Drag.Context;
                if (eventWindow == null) {
                    eventWindow = window;
                }

                if (typeof eventWindow.getSelection != "undefined") {
                    sel = eventWindow.getSelection();

                    if (sel.rangeCount) {
                        container = document.createElement("div");
                        var len;
                        for (i = 0, len = sel.rangeCount; i < len; i++) {
                            var a = sel.getRangeAt(i).cloneContents();
                            if (a && a.firstChild && a.firstChild.tagName && /h[0-9]/.test(a.firstChild.tagName.toLowerCase())) {
                                collection.Title = a.firstChild.textContent;
                            }
                            container.appendChild(a);
                        }
                        selection = container.innerHTML;
                    }
                } else if (typeof document.selection != "undefined") {
                    if (document.selection.type == "Text") {
                        selection = document.selection.createRange().htmlText;
                    }
                }

                var htmlSelection = $("<div>").html(selection);
                htmlSelection.find("script,noscript").remove();
                htmlSelection.find("font").each(function () {
                    $(this).replaceWith("<div>" + $(this).html() + "</div>");
                });
                htmlSelection.html($.trim(htmlSelection.html())); //rremove multisplaces
                htmlSelection.html(htmlSelection.html().replace(/(<br\s*\/?>){2,}/gi, '<br>')); //rremove multi br
                htmlSelection.find("*").filter(function () {
                    var spaces = (this.nodeType == 3 && !/\S/.test(this.nodeValue));
                    var emptyTags = $.trim($(this).html()) == ''; //rremove empty tags
                    return spaces || emptyTags;
                }).remove();


                selection = htmlSelection.html();

                var stripSelection = stripTags(selection, '');


                collection.Text = stripSelection;
                collection.Keywords = selection;
            },

            Drag_d_video: function () {
                collection.Provider = dragElmnt.attr("data-provider");
            },

            /*drag from address bar*/
            Drag_d_address_bar: function () {
                collection.Type = "link";
                collection.MakeSnapshot = true;
                collection.Href = collection.Href ? collection.Href : window.location.href;
            },

            Drag_default: function () {

                if (!collection.Image && dragElmnt.attr('src') && collection.Type != "video") {

                    var src = dragElmnt.attr('src');

                    if (src.indexOf("data:image/") === 0) {
                        collection.Base64Image = src;
                    } else {
                        collection.Image = correctUrl(dragElmnt.attr('src'));
                    }
                }

                //validate image url
                if (collection.Image && collection.Image.length > 0 && collection.Image.indexOf("http") !== 0) {
                    collection.Image = correctUrl(collection.Image);
                }

                if (!collection.Keywords && dragElmnt.attr('alt')) {
                    collection.Keywords = dragElmnt.attr('alt');
                } else if (!collection.Keywords) {
                    collection.Keywords = "";
                }

                //clean whitespaces and line breaks
                if (collection.Title) {
                    collection.Title = collection.Title.replace(/\s{2,}/g, ' ').replace(/(\n\s*?\n)\s*\n/, '$1');
                }

                if (!collection.Title) {
                    if (document.title && document.title != "") {
                        collection.Title = document.title;
                    } else {
                        collection.Title = document.URL;
                    }
                }

                if (!collection.Referer) {
                    collection.Referer = window.location.href;
                }

                if (collection.Type == "link" && !collection.Href) {
                    collection.Href = correctUrl(dragElmnt.attr('href') || window.location.href);
                }

                var scrapEnable = 1;
                //Disable parsing
                if (collection.Sitepage === 'youtube' && collection.Provider === 'youtube') {
                    scrapEnable = 0;
                }

                if (scrapEnable) {
                    collection.Keywords = dragElmnt.knuggetScrapping();
                    if (collection.Keywords.length > 1000) {
                        collection.Keywords = collection.Keywords.substring(0, 1000);
                    }

                    dragElmnt.knuggetScrapping("destroy");
                }
            }
        };

        function correctUrl(url) {

            if (url.indexOf("http://") == 0 || url.indexOf("https://") == 0) {
                //dO NOTHING
            } else if (url.indexOf("//") == 0) {
                url = location.protocol + url; // http:||https: + end url
            } else if (url.indexOf("/") == 0) {
                var domain = window.location.protocol + "//" + window.location.host;
                url = domain.substring(0, location.href.lastIndexOf('/')) + url; //real domain + end url
            } else {
                url = location.href.substring(0, location.href.lastIndexOf('/')) + "/" + url;
                //url = window.location.protocol + "//" + location.host + "/" + url;
            }
            return url;
        }

        function getImgSizes(imgSrc) {

            var newImg = new Image();
            newImg.src = imgSrc;
            var dims = {
                width: newImg.width,
                height: newImg.height
            };

            return dims;
        }

        function stripTags(str, allow) {

            allow = (((allow || "") + "").toLowerCase().match(/<[a-z][a-z0-9]*>/g) || []).join('');
            var tags = /<\/?([a-z][a-z0-9]*)\b[^>]*>/gi;
            var commentsAndPhpTags = /<!--[\s\S]*?-->|<\?(?:php)?[\s\S]*?\?>/gi;
            return str.replace(commentsAndPhpTags, '').replace(tags, function ($0, $1) {
                return allow.indexOf('<' + $1.toLowerCase() + '>') > -1 ? $0 : '';
            });
        }

        function cleanArray(array, selectors) {

            if (selectors)
                return $.grep(array, function (n) {
                    n = (n.length) ? n : false;
                    return (n);
                }); //clean array with jquery selectors
            else
                return $.grep(array, function (n) {
                    return (n);
                }); //clean array with simple values
        }

        function getDragType() {

            if (dragElmnt.is("#" + KNUGGET_EVENTS.selectors.VideoIcon) || dragElmnt.find('#' + KNUGGET_EVENTS.selectors.VideoIcon).length) {

                if (!dragElmnt.is("#" + KNUGGET_EVENTS.selectors.VideoIcon)) {
                    dragElmnt = dragElmnt.find('#' + KNUGGET_EVENTS.selectors.VideoIcon + ':first');
                }

                collection.Type = 'video';
                collection.Href = dragElmnt.attr("data-src");

            } else if (dragElmnt.is('img')) {

                var linkElement;

                //Check if dragged image is contained in link element within 5 levels
                //for (var i = 0; i < 5; i++) {
                //    if (dragElmnt.parents().eq(i).is('a')) {
                //        linkElement = dragElmnt.parents().eq(i);
                //        break;
                //    }
                //};

                if (linkElement) {

                    collection.Title = dragElmnt.attr('alt');
                    collection.Href = correctUrl(linkElement.attr("href"));
                    collection.Type = 'link';

                    if (!collection.Referer) {
                        collection.Referer = window.location.href;
                    }


                    collection.Type = 'link';

                } else {

                    collection.Type = 'picture';

                }


            } else if (dragElmnt.is('a')) {

                var cloneElement = dragElmnt.clone();

                //Remove all invisible tags that does not contain images
                cloneElement.find(':hidden:not(:contains("img")').not('img').remove();

                if (cloneElement.find('img').length === 1) {

                    collection.Type = 'apicture';

                } else {

                    ////////CHECKINI BACKGROUND IR KAD NEBUTU 1x1 image
                    var validExtensions = {
                        jpg: true,
                        jpeg: true,
                        png: true,
                        gif: true
                    };

                    var bg = dragElmnt.css('background-image');
                    var patt = /\url|\(|\"|\"|\'|\)/g;
                    bg = bg.replace(patt, '').split("?");
                    bg = bg[0];

                    if (validExtensions[bg.substr(bg.lastIndexOf('.') + 1)]) {
                        var bgSizes = getImgSizes(bg);
                        if (bgSizes.width > 1 && bgSizes.height > 1) {
                            //FOUND backgrounded img INSIDE <a> style
                            collection.Type = 'apicturebg';
                            collection.Src = bg;
                        } else {
                            //FOUND <A>
                            collection.Type = 'link';
                        }
                    } else {
                        collection.Type = 'link';
                    }
                }

            } else if (dragElmnt.is('[draggable="true"]')) {

                //REMOVE_ ALL TAGS Except img
                var dragElmnt2 = dragElmnt.clone();
                dragElmnt2.find(":hidden").not("img").remove();


                if (dragElmnt2.children().length == 1 && dragElmnt2.find('img').length == 1) {
                    collection.Type = 'apicture';
                } else {
                    ////////CHECKINI BACKGROUND IR KAD NEBUTU 1x1 image
                    var validExtensions = {
                        jpg: true,
                        jpeg: true,
                        png: true,
                        gif: true
                    };
                    var bg = dragElmnt.css('background-image');
                    var patt = /\url|\(|\"|\"|\'|\)/g;
                    bg = bg.replace(patt, '').split("?");
                    bg = bg[0];


                    if (validExtensions[bg.substr(bg.lastIndexOf('.') + 1)]) {
                        var bgSizes = getImgSizes(bg);
                        if (bgSizes.width > 1 && bgSizes.height > 1) {
                            //FOUND backgrounded img INSIDE <a> style
                            collection.Type = 'apicturebg';
                            collection.Src = bg;
                        } else {
                            //FOUND <A>
                            collection.Type = 'link';
                        }
                    } else {
                        collection.Type = 'link';
                    }
                }

            } else if (collection.Type == "address_bar") {
                //

            } else {
                //FOUND <tag> a.k.a selected text
                collection.Type = 'text';

            }
        }

        function getSitePage() {

            var websites = {
                fb: /^(http|https):\/\/www.facebook.com/,
                ffffound: /^(http|https):\/\/ffffound.com/,
                tumblr: /^(http|https):\/\/www.tumblr.com/,
                tumblrin: /^.tumblr.com/,
                pinterest: /^https?:\/\/(www\.)?pinterest.com/,
                shutterstock: /^https?:\/\/(www\.)?shutterstock.com/,
                dribbble: /^https?:\/\/(www\.)?dribbble.com/,
                behance: /^https?:\/\/(www\.)?behance.net/,
                flickr: /^(http|https):\/\/www.flickr.com/,
                twitter: /^(http|https):\/\/twitter.com/,
                google: /^(http|https):\/\/www.google./,
                youtube: /^(http|https):\/\/www.youtube.com/,
                vimeo: /^(http|https):\/\/vimeo.com/,
                ninegag: /^(http|https):\/\/9gag.com/,
                feedly: /^(http|https):\/\/feedly.com/,
            };

            var page = "";

            $.each(websites, function (key, val) {
                page = (val.test(window.location.href)) ? key : "d";
                if (page != "d") {
                    return false;
                }
            });

            collection.Sitepage = page;
        }



        /* ==========================================================================
        INIT
        ========================================================================== */

        collection = $.extend({}, KNUGGET.DragDATA, collection);

        // Get drag type and website to construct maker function
        getDragType();
        getSitePage();

        if (collection.Type != "" && collection.Sitepage != "") {

            var funcName = "Drag_" + collection.Sitepage + "_" + collection.Type;
            var runFunction = false;

            //ijungiam konkretaus saito scrapinga
            if (typeof maker[funcName] == "function") {
                maker["" + funcName + ""]();
                runFunction = true;
            }

            funcName = "Drag_d_" + collection.Type;
            if (!runFunction && typeof maker[funcName] == "function") {
                maker["" + funcName + ""]();
            }
        }

        maker.Drag_default();
        callback(collection);

    };

    $.fn.videoDragSquare = function (event) {

        switch (event.data.provider) {
            case "youtube":
                //disable on fullscreen
                if ($("body").attr("data-player-size") !== "fullscreen") {

                    var container = $(event.target).hasClass("html5-video-container") ? $(event.target) : $(event.target).closest(".html5-video-container");

                    if (container.length) {
                        includeIcon(container.find("video").eq(0) || container.find("embed").eq(0), "youtube", container);
                    } else {
                        includeIcon($(this), "youtube");
                    }
                }

                break;
            case "vimeo":
                includeIcon($(this), "vimeo");
                break;
        }

        function includeIcon(video, provider, parentContainer) {

            var vHeight = 30;
            var vTop = 0;
            var vLeft = 0;
            var src = "";
            var videoId;
            var isYoutubePage = false;


            if (!video && parentContainer) {
                video = parentContainer; //If video does not have container, set video as container itself (for embeded players)
            } else if (!video) {
                return false;
            }

            //Remove video icon
            $("#" + KNUGGET_EVENTS.selectors.VideoIcon).remove();

            //Parse video ID from youtube URL
            if (window.location.host === "www.youtube.com") {
                var knuggetVideoId = $("#" + KNUGGET_EVENTS.selectors.VideoIcon).attr("data-src");
                if (knuggetVideoId && knuggetVideoId.length) {
                    knuggetVideoId = knuggetVideoId.replace("http://www.youtube.com/watch?v=", "").replace("https://www.youtube.com/watch?v=", "");
                }
                isYoutubePage = 1;
            }

            video.addClass(KNUGGET_EVENTS.selectors.VideoBlock);

            vTop = video.offset().top;

            if (isYoutubePage) {
                //video icon jump off when video is resized on player frame: https://www.youtube.com/watch?v=3avhU0N5lJI
                var originalVideoTop = Math.abs(parseInt(video.css("top")));
                if (originalVideoTop) {
                    vTop = vTop + originalVideoTop;
                }

                vLeft = parentContainer ? parentContainer.offset().left : video.offset().left;

            } else {
                vLeft = video.offset().left;
            }

            vTop = vTop + vHeight; //space 30px

            switch (provider) {
                case "youtube":

                    if (window.location.host === "www.youtube.com") {
                        var metavideoId = $("meta[itemprop=videoId]");
                        if (metavideoId && metavideoId.attr("content").length) {
                            src = "https://www.youtube.com/watch?v=" + metavideoId.attr("content");
                        }
                    }

                    if (src === "") {
                        if (video.is("embed")) {
                            if (video.attr("flashvars") && video.attr("flashvars").indexOf("&vid=") != -1) {
                                videoId = KNUGGET.urlParam("vid", video.attr("flashvars"));
                            } else if (video.attr("flashvars") && video.attr("flashvars").indexOf("&video_id=") != -1) {
                                videoId = KNUGGET.urlParam("video_id", video.attr("flashvars"));
                            } else if (video.attr("src")) {
                                src = video.attr("src");
                            }
                            if (videoId)
                                src = "https://www.youtube.com/watch?v=" + videoId;
                        } else if (video.is("video")) {
                            videoId = video.attr("data-youtube-id");
                            if (videoId)
                                src = "https://www.youtube.com/watch?v=" + videoId;
                        } else if (video.attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1 && video.parents(".knugget_video_player")) {
                            src = video.parents(".knugget_video_player").attr("data-knuggetVideoHref");
                        } else {
                            src = video.attr("src") || video.attr("data");

                            //disable from playlist outside
                            if (src.indexOf("list=") !== -1) { //src.indexOf("origin=") !== -1 ||
                                src = "";
                            }
                            //remove all queryparams
                            if (src.indexOf('?') > 0) {
                                src = src.substring(0, src.indexOf('?'));
                            }
                        }
                    }

                    break;
                case "vimeo":
                    if (video.is("object")) {
                        var flashvars = video.find("param[name=flashvars]");

                        if (flashvars && flashvars.val().indexOf("&clip_id=") != -1) {
                            videoId = KNUGGET.urlParam("clip_id", flashvars.val());
                        }

                        if (videoId)
                            src = "http://www.vimeo.com/" + videoId;
                    } else if (video.hasClass("vimeo_holder") && !video.find("object").length) { //html5
                        if (video.find("video").length) {
                            src = video.find("video[src*='//player.vimeo.com/play']").attr("src");
                        } else {
                            var playerId = video.find("div[id^='player_']");
                            playerId = playerId.attr("id");
                            if (playerId) {
                                playerId = playerId.split("_");
                                playerId = playerId[1];
                                if (playerId)
                                    src = "http://www.vimeo.com/" + playerId;
                            }
                        }
                    } else if (video.hasClass("player") && video.find("video,object").length && video.attr("data-fallback-url")) {
                        src = video.attr("data-fallback-url");
                    } else if (video.attr("src").indexOf("https://attachment.fbsbx.com/external_iframe.php") > -1 && video.parents(".knugget_video_player")) {
                        src = video.parents(".knugget_video_player").attr("data-knuggetVideoHref");
                    } else {
                        src = video.attr("src");
                    }

                    break;
            }

            if (src) {

                var knuggetVideo = document.createElement('img');
                knuggetVideo.id = KNUGGET_EVENTS.selectors.VideoIcon;
                knuggetVideo.setAttribute('data-provider', provider);
                knuggetVideo.setAttribute('data-src', src);
                knuggetVideo.setAttribute('draggable', true);
                knuggetVideo.setAttribute('src', KNUGGET.extensionFileUrl("images/drag_icon.gif"));
                knuggetVideo.setAttribute('style', 'width:30px !important;height:30px !important;');

                $(knuggetVideo).appendTo("body").css({
                    'top': vTop,
                    'left': vLeft
                });

            }
            return true;
        }
    };

    //:<< video iconos matomumui, nes kai kurie turi sluoksni, ir slepia ikonele musu
    $.fn.fixFlash = function () {

        var htmlas;
        // loop through every embed tag on the site
        $("embed[src*='://s.ytimg.com/yts/swfbin/watch']").each(function () {
            var embed = $(this)[0];
            var newEmbed;
            // everything but Firefox & Konqueror
            if (embed.outerHTML) {
                htmlas = embed.outerHTML; // replace an existing wmode parameter
                if (htmlas.match(/wmode\s*=\s*('|")[a-zA-Z]+('|")/i))
                    newEmbed = htmlas.replace(/wmode\s*=\s*('|")window('|")/i, "wmode='transparent'");
                    // add a new wmode parameter
                else
                    newEmbed = htmlas.replace(/<embed\s/i, "<embed wmode='transparent' ");
                // replace the old embed object with the fixed version
                //embed.insertAdjacentHTML('beforeBegin', newEmbed);
                embed.insertAdjacentHTML('afterEnd', newEmbed);
                embed.parentNode.removeChild(embed);
            } else {
                // cloneNode is buggy in some versions of Safari & Opera, but works fine in FF
                newEmbed = embed.cloneNode(true);
                if (!newEmbed.getAttribute('wmode') || newEmbed.getAttribute('wmode').toLowerCase() == 'window')
                    newEmbed.setAttribute('wmode', 'transparent');
                embed.parentNode.replaceChild(newEmbed, embed);
            }
        });

        $("object[data*='://www.youtube.com/v/'], object[data*='vimeocdn.com/p/flash']").each(function () {
            var object = $(this)[0];
            var newObject;
            // object is an IE specific tag so we can use outerHTML here
            if (object.outerHTML) {
                htmlas = object.outerHTML; // replace an existing wmode parameter
                if (htmlas.match(/<param\s+name\s*=\s*('|")wmode('|")\s+value\s*=\s*('|")[a-zA-Z]+('|")\s*\/?\>/i))
                    newObject = htmlas.replace(/<param\s+name\s*=\s*('|")wmode('|")\s+value\s*=\s*('|")window('|")\s*\/?\>/i, "<param name='wmode' value='transparent' />");
                    // add a new wmode parameter
                else
                    newObject = htmlas.replace(/<\/object\>/i, "<param name='wmode' value='transparent' />\n</object>");
                // loop through each of the param tags
                var children = object.childNodes;
                for (var j = 0; j < children.length; j++) {
                    try {
                        if (children[j] != null) {
                            var theName = children[j].getAttribute('name');
                            if (theName != null && theName.match(/flashvars/i)) {
                                newObject = newObject.replace(/<param\s+name\s*=\s*('|")flashvars('|")\s+value\s*=\s*('|")[^'"]*('|")\s*\/?\>/i, "<param name='flashvars' value='" + children[j].getAttribute('value') + "' />");
                            }
                        }
                    } catch (err) { }
                }
                // replace the old embed object with the fixed versiony
                object.insertAdjacentHTML('beforeBegin', newObject);
                object.parentNode.removeChild(object);
            }
        });
    };

}(jQuery));