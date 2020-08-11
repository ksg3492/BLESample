package com.sunggil.blesample.data

class MelonStatus {
    companion object {
        var session_id = ""
        var member_key = "0"
        var loggingToken = ""
        var bitrate = ""
        var isChangeST = "N"  //Y, N
        var cid = ""
        var menuid = ""
        var ctype = ""
        var mdn = "0000"
        var pcid = ""
        var appVersion = ""
        var deviceId = ""
        var phoneModel = ""
        var metaType = "SKM"
        var cookies : ArrayList<String>? = null

        fun init() {
            session_id = ""
            member_key = "0"
            loggingToken = ""
            bitrate = ""
            isChangeST = "N"
            cid = ""
            menuid = ""
            ctype = ""
            mdn = "0000"
            pcid = ""
            appVersion = ""
            deviceId = ""
            phoneModel = ""
            metaType = "SKM"
            cookies = null
        }

        fun addCookies(cookie : String) {
            if (cookies == null) {
                cookies = ArrayList()
            }

            cookies!!.add(cookie)
        }
    }
}