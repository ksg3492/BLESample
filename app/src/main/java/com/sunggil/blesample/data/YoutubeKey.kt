package com.sunggil.blesample.data

class YoutubeKey {
    var apikey : String? = ""
    get() {
        return field ?: ""
    }
    var secret = ""
    var client_id = ""
    var client_id_code = ""
    var secret_code = ""
}