package com.sunggil.blesample.data

class MelonStreamingItem {
    var PATH = ""
    var ISLIVESTREAMING = ""
    var METATYPE = ""
    var LDBPATH = ""
    var ALBUMID = ""
    var ALBUMIMGPATH = ""
    var LOGGINGTOKEN = ""
    var STREAMING = ""
    var AVAILSTATUS = ""
    var PNAME = "" // artist
    var LYRICSPATH = ""
    var ISDOWNLOAD = ""
    var PERIOD = ""
    var CNAME = "" // title
    var MESSAGECODE = ""
    var RESULT = ""
    var BITRATE = ""
    var CID = ""
    var MESSAGE = ""
    var ACTION = ""
    var ALBUM = "" // album title
    var OPTION : MelonStreamingOption? = null

    inner class MelonStreamingOption {
        var ACTION = ""
        var MESSAGE = ""
    }

    override fun toString() : String {
        return "MelonStreamingItem{" +
                "PATH='" + PATH + '\'' +
                ", ISLIVESTREAMING='" + ISLIVESTREAMING + '\'' +
                ", METATYPE='" + METATYPE + '\'' +
                ", LDBPATH='" + LDBPATH + '\'' +
                ", ALBUMID='" + ALBUMID + '\'' +
                ", ALBUMIMGPATH='" + ALBUMIMGPATH + '\'' +
                ", LOGGINGTOKEN='" + LOGGINGTOKEN + '\'' +
                ", STREAMING='" + STREAMING + '\'' +
                ", AVAILSTATUS='" + AVAILSTATUS + '\'' +
                ", PNAME='" + PNAME + '\'' +
                ", LYRICSPATH='" + LYRICSPATH + '\'' +
                ", ISDOWNLOAD='" + ISDOWNLOAD + '\'' +
                ", PERIOD='" + PERIOD + '\'' +
                ", CNAME='" + CNAME + '\'' +
                ", MESSAGECODE='" + MESSAGECODE + '\'' +
                ", RESULT='" + RESULT + '\'' +
                ", BITRATE='" + BITRATE + '\'' +
                ", CID='" + CID + '\'' +
                '}';
    }
}