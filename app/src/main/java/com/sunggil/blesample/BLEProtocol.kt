package com.sunggil.blesample

import androidx.annotation.NonNull
import java.io.Serializable

class BLEProtocol(@NonNull val command : Int) : Serializable {
//    var command : Int = 0
    var arg1 : Int= -1
    var arg2 : Int= -1
    var message = ""
    var contentLength : Int= 0
    var content : ByteArray? = null
        set(value) {
            field = value

            if (value == null) {
                contentLength = 0
            } else {
                contentLength = value!!.size
            }
        }


}