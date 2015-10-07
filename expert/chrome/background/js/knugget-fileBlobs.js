angular.module('fileBlobsFactory', []).factory('fileBlob', ["$http", '$q', function($http, $q) {

    var apiUrl = KNUGGET.config.domain + "api";
    var blockIDPrefix = "block-";

    var ALLOW_FILE_TYPES = [
        "image/jpg",
        "image/jpeg",
        "image/gif",
        "image/png",
        "image/bmp",
        "image/x-windows-bmp",
        "image/tiff",
        "image/x-tiff",
        "image/webp",
        "jpeg",
        "jpg",
        "gif",
        "png",
        "bmp",
        "tiff",
        "webp"
    ];

    //******** returns ***************
    // if 200, it's OK
    // if 400  - error: snapshotFail
    // if 400  - error: fileIsEmpty
    // if 400  - error: fileTypeIsWrong
    // if 400  - error: azureFail
    //*******************************

    var fileFactory = {
        //file preparation to blob
        Blob: function(uploadType, file, callback) {
            this.submitUri = null;
            this.azureStorageUrl = "";

            this.uploadType = uploadType;
            this.done = callback;

            this.MAX_BLOCK_SIZE = 256 * 1024; //split to 256KB files

            this.blockIds = new Array();

            this.currentFilePointer = 0;

            this.selectedFile = file;
            this.totalBytesRemaining = file.size;

            var $this = this;

            this.getWriteToken()
                .success(function(response, status) {
                    if (status == "200" && response) {

                        //this.files = e.target.files;
                        //this.selectedFile = this.files[0];

                        $this.filename = $this.guid();

                        var baseUrl = response.replace(/"/g, "");

                        var indexOfQueryStart = baseUrl.indexOf("?");
                        $this.azureStorageUrl = baseUrl.substring(0, indexOfQueryStart);
                        $this.submitUri = $this.azureStorageUrl + '/' + $this.filename + baseUrl.substring(indexOfQueryStart);
                        $this.uploadFileInBlocks();
                    }
                }).error(function(response, status) {
                    $this.done({
                        error: true
                    });
                });
        },

        fileProcessing: function(data) {

            var arrayBuffer;
            var azureBlob;

            var deferred = $q.defer();

            //screen snapshot or base64string img
            if (data.Base64Image || data.MakeSnapshot) {

                var image = data.Base64Image ? JSON.parse(JSON.stringify(data.Base64Image)) : JSON.parse(JSON.stringify(KNUGGET.screenSnapshotValue));

                if (!image.length) {

                    deferred.resolve({
                        status: 400,
                        data: {
                            error: "snapshotFail"
                        }
                    });

                } else {

                    var imageRaw = image.replace("data:image/jpeg;base64,", "").replace("data:image/gif;base64,", "").replace("data:image/png;base64,", "");
                    arrayBuffer = KNUGGET.imageProcessor.convertBase64toAb(imageRaw);

                    if (data.MakeSnapshot) {

                        arrayBuffer.type = "image/jpeg";

                    } else {

                        var fileType = image.split(";base64")[0];
                        fileType = fileType.replace("data:", "");

                        if ($.inArray(fileType, ALLOW_FILE_TYPES) == -1) {
                            //error
                            deferred.resolve({
                                status: 400,
                                data: {
                                    error: "fileTypeIsWrong: " + fileType
                                }
                            });
                        }

                        arrayBuffer.type = fileType;
                    }

                    azureBlob = new fileFactory.Blob("Base64String", arrayBuffer, function(result) {

                        delete data.Files;
                        delete data.Base64Image;

                        // Clear screenshot value
                        KNUGGET.screenSnapshotValue = "";

                        if (result && result.filename) {

                            data.Image = result.filename;

                            fileFactory.fileUpload(data, deferred);

                        } else {

                            deferred.resolve({
                                status: 400,
                                data: {
                                    error: "azureFail"
                                }
                            });
                        }
                    });
                }

            } else if ((data.Files && data.Files[0]) || ((data.Type == "picture" || data.Type == "apicture" || data.Type == "link") && data.Image)) {

                //Files from desktop
                if (data.Files && data.Files[0]) {
                    //TODO: refactor as base64 image processing and sending to backend with promises functionality

                    data.Referer = "From desktop";

                    //Only allowed image file formats
                    if ($.inArray(data.Files[0].type, ALLOW_FILE_TYPES) == -1) {
                        return false;
                    }

                    data.Files[0].binary = data.Files[0].binary.split(";base64,")[1];
                    arrayBuffer = KNUGGET.imageProcessor.convertBase64toAb(data.Files[0].binary);
                    data.Files[0].size = arrayBuffer.size;
                    data.Files[0].binary = arrayBuffer.binary;

                    //check if filesize is 0, stop upload
                    if (data.Files[0].size) {

                        azureBlob = new fileFactory.Blob("File", data.Files[0], function(result) { //data.Files[0] : size, type, binary
                            delete data.Files;

                            if (result && result.filename) {
                                //console.log(result.filename);

                                data.Image = result.filename;

                                fileFactory.fileUpload(data, deferred);
                            }
                        });
                    } else {
                        //file is empty
                        deferred.resolve({
                            status: 400,
                            data: {
                                error: "fileIsEmpty"
                            }
                        });
                    }

                    //images from web

                    //Files from web
                
                } else if ((data.Type == "picture" || data.Type == "apicture" || data.Type == "link") && data.Image) {

                    console.log("aaa");

                    KNUGGET.imageProcessor.getBiggerImage(data.Image, data.Sitepage, function (image) {

                        KNUGGET.imageProcessor.getRemoteImageStream(image, function (stream) {

                            if (stream && $.inArray(stream.type.toLowerCase(), ALLOW_FILE_TYPES) == -1) {

                                console.log("fileTypeIsWrong", stream.type.toLowerCase());

                                deferred.resolve({
                                    status: 400,
                                    data: {
                                        error: "fileTypeIsWrong"
                                    }
                                });

                            } else if (stream && stream.size) {

                                azureBlob = new fileFactory.Blob("RemoteImage", stream, function(result) {
                                    delete data.Files;

                                    if (result && result.filename) {
                                        //console.log(result.filename);
                                        data.Image = result.filename;

                                        fileFactory.fileUpload(data, deferred);
                                    }
                                });

                            } else {
                                //file is empty
                                deferred.resolve({
                                    status: 400,
                                    data: {
                                        error: "fileIsEmpty"
                                    }
                                });
                            }
                        });
                    });
                }

                azureBlob = null;
            }

            return deferred.promise;
        },

        fileUpload: function(data, deferred) {
            $http.post(apiUrl + '/item/add', data).then(function(response) {
                deferred.resolve(response);
            });
        }
    };

    fileFactory.Blob.prototype = {

        uploadFileInBlocks: function() { //slice filestream to small packages
            if (this.totalBytesRemaining > 0) {
                //console.log("current file pointer = " + this.currentFilePointer + " bytes read = " + this.MAX_BLOCK_SIZE);

                var binary = this.uploadType == "RemoteImage" ? this.selectedFile : this.selectedFile.binary;
                var fileContent = binary.slice(this.currentFilePointer, this.currentFilePointer + this.MAX_BLOCK_SIZE);
                var blockId = blockIDPrefix + this.pad(this.blockIds.length, 6);
                //console.log("block id = " + blockId);
                this.blockIds.push(btoa(blockId));

                this.uploadBlock(fileContent);

                this.currentFilePointer += this.MAX_BLOCK_SIZE;
                this.totalBytesRemaining -= this.MAX_BLOCK_SIZE;
                if (this.totalBytesRemaining < this.MAX_BLOCK_SIZE) {
                    this.MAX_BLOCK_SIZE = this.totalBytesRemaining;
                }
            } else {
                this.combineBlocksList();
            }
        },

        uploadBlock: function(content) { //upload small packages to azure 
            var $this = this;

            var uri = this.submitUri + '&comp=block&blockid=' + this.blockIds[this.blockIds.length - 1];

            //console.log(uri);

            //var requestData = new Uint8Array(content);
            var requestData = content;

            $.ajax({
                url: uri,
                type: "PUT",
                data: requestData,
                processData: false,
                beforeSend: function(xhr) {
                    xhr.setRequestHeader('x-ms-blob-type', 'BlockBlob');
                },
                success: function(data, status) {
                    //console.log(data);
                    //console.log(status);
                    //$this.bytesUploaded += requestData.length;
                    //var percentComplete = ((parseFloat($this.bytesUploaded) / parseFloat($this.selectedFile.size)) * 100).toFixed(2);

                    $this.uploadFileInBlocks();
                },
                error: function(xhr, desc, err) {
                    //console.log(desc);
                    //console.log(err);
                }
            });

        },

        combineBlocksList: function() { //send packages ids to combine to one file
            var $this = this;

            var uri = this.submitUri + '&comp=blocklist';
            var requestBody = '<?xml version="1.0" encoding="utf-8"?><BlockList>';
            for (var i = 0; i < this.blockIds.length; i++) {
                requestBody += '<Latest>' + this.blockIds[i] + '</Latest>';
            }
            requestBody += '</BlockList>';
            $.ajax({
                url: uri,
                type: "PUT",
                data: requestBody,
                beforeSend: function(xhr) {
                    xhr.setRequestHeader('x-ms-blob-content-type', $this.selectedFile.type);
                    xhr.setRequestHeader('x-ms-blob-cache-control', "public, max-age=1209600");
                },
                success: function(data, status) {
                    //console.log(status);
                    if (status == "success") {
                        $this.done({
                            filename: $this.azureStorageUrl + '/' + $this.filename
                        });
                    }
                },
                error: function(xhr, desc, err) {
                    //console.log(desc);
                    //console.log(err);
                }
            });

        },
        getWriteToken: function() {
            return $http.post(apiUrl + "/main/getWriteToken");
        },
        guid: function() {
            var dateNow = new Date();
            var y = dateNow.getFullYear();
            var mDefault = dateNow.getMonth() + 1;
            var m = mDefault < 10 ? "0" + mDefault : mDefault;
            var dDefault = dateNow.getDate();
            var d = dDefault < 10 ? "0" + dDefault : dDefault;
            var date = y + "/" + m + "/" + d + "/";

            var guid, i, j;
            guid = '';
            for (j = 0; j < 32; j++) {
                i = Math.floor(Math.random() * 16).toString(16).toUpperCase();
                guid = guid + i;
            }

            var extension = this.selectedFile.type.split(";charset");
            extension = "." + extension[0].replace("image/", "").replace("jpeg", "jpg");

            if (extension == ".") extension = "";

            if ($.inArray(extension, [".jpg", ".gif", ".png"]) === -1) {
                extension = "";
            }

            return date + guid + extension;
        },

        pad: function(number, length) { //generate small package id
            var str = '' + number;
            while (str.length < length) {
                str = '0' + str;
            }
            return str;
        }
    };

    return fileFactory;
}]);
