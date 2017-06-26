var Search = {
    init: function () {
        $(document).ready(function () {
            var searchViewModel = new Search.searchViewModel();
            searchViewModel.initModel();
            ko.applyBindings(searchViewModel);
        });
    }
};
Search.searchViewModel = function () {
    var self = this;
    self.searchInput = ko.observable();
    self.searchResult = ko.observableArray();
    self.resultsOnPage = 10;
    self.currentQuery = null;

    self.searchInputKeyUp = function (data, event) {
        if (self.searchInputEmpty()) {
            $("#x").fadeOut();
        } else {
            if (event.which === 13) {
                self.searchButtonClick();
            } else {
                $("#x").fadeIn();
            }
        }
    };

    self.xButtonClick = function () {
        $("#search_input").val("");
        $("#x").hide();
    };

    self.searchButtonClick = function () {
        if (!self.searchInputEmpty()) {
            self.currentQuery = self.searchInput();
            self.initPagination(self.resultsOnPage, 1);
            self.search(self.currentQuery, 0);
        }
    };

    self.search = function (query, offset, ignoreHistoryPush) {
        $("#pagination").hide();
        $("#loader").show();
        self.searchResult.removeAll();

        var url = "/search?text=" + query + "&startIndex=" + offset;
        $.ajax({
            url: url,
            type: "GET",
            success: function (data) {
                $("#loader").hide();
                _.each(data.items, function (item) {
                    var searchItem = new self.searchResultItem(item.topic, item.link, item.mdLink, "");
                    self.searchResult.push(searchItem);
                });
                $(function () {
                    var pag = $("#pagination");
                    if (self.resultsOnPage !== data.resultsPerPage) {
                        self.resultsOnPage = data.resultsPerPage;
                        pag.pagination('updateItemsOnPage', data.resultsPerPage);
                    }
                    var currentPage = pag.pagination('getCurrentPage');
                    var pagesCount = pag.pagination('getPagesCount');
                    var items = currentPage * self.resultsOnPage;
                    if (pagesCount === currentPage && data.items && data.items.length === self.resultsOnPage) {
                        items += self.resultsOnPage;
                        pag.pagination('updateItems', items);
                    }
                    pag.show();

                    if (!ignoreHistoryPush) {
                        var stateObj = {query: query, offset: offset, items: items, currentPage: currentPage};
                        history.pushState(stateObj, "", "");
                    }
                });
            },
            error: function (e) {
                $("#loader").hide();
                alert("Ошибка при загрузке ответов");
            }
        });
    };

    self.searchInputEmpty = function () {
        return ($.trim($("#search_input").val()) === "");
    };

    self.initPagination = function (items, currentPage) {
        var pag = $("#pagination");
        pag.hide();
        pag.pagination({
            displayedPages: 3,
            items: items,
            currentPage: currentPage,
            itemsOnPage: self.resultsOnPage,
            useAnchors: false,
            prevText: '&larr;',
            nextText: '&rarr;',
            cssStyle: 'light-theme',
            onPageClick: function (pageNumber, event) {
                var offset = (pageNumber - 1) * self.resultsOnPage;
                self.search(self.currentQuery, offset);
            }
        });
    };

    self.initModel = function () {
        window.onpopstate = function (event) {
            var state = history.state;
            if (state) {
                self.searchInput(state.query);
                self.currentQuery = state.query;
                self.initPagination(state.items, state.currentPage);
                self.search(state.query, state.offset, true);
            }
        };
        self.initPagination(self.resultsOnPage, 1);
    };

    self.searchResultItem = function resultItem(title, link, mdLink, description) {
        this.title = ko.observable(title);
        this.link = ko.observable(link);
        this.mdLink = ko.observable(mdLink);
        this.description = ko.observable(description);
    };
};