package com.sunggil.blesample.data

class YoutubeItem {
    var id = ""
        get() {
            return field ?: ""
        }
    var channelId = ""
        get() {
            return field ?: ""
        }
    var title = ""
    var thumbnail = ""
    var url = ""
    var duration = 0 // 리스트
    var publish_day = ""
    var expires_in = 0
    var stream_type = "" // 검색
        set(value) {
            field = value ?: ""
        }
}