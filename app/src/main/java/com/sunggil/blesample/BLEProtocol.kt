package com.sunggil.blesample

import android.util.Log
import androidx.annotation.NonNull
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class BLEProtocol(@NonNull var command : Int) : Serializable {
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


    private fun writeObject(stream : ObjectOutputStream) {
//        Log.e("SG2","BLEPROTOCAL OVERRIDE writeObject()")
        try {
            stream.writeInt(command);
            stream.writeInt(arg1);
            stream.writeInt(arg2);
            stream.writeObject(message);
            stream.writeInt(contentLength);
            stream.writeObject(content);
        } catch (throws : IOException) {
            Log.e("SG2","BLEPROTOCAL OVERRIDE writeObject() error : ${throws.message}")
        }
    }



    private fun readObject(stream : ObjectInputStream) {
//        Log.e("SG2","BLEPROTOCAL OVERRIDE readObject()")
        try {
            command = stream.readInt()
            arg1 = stream.readInt()
            arg2 = stream.readInt()
            message = stream.readObject() as String
            contentLength = stream.readInt()
            content = stream.readObject() as ByteArray?
        } catch (throws : IOException) {
            Log.e("SG2","BLEPROTOCAL OVERRIDE readObject() error1 : ${throws.message}")
        } catch (ce : ClassNotFoundException) {
            Log.e("SG2","BLEPROTOCAL OVERRIDE readObject() error2 : ${ce.message}")

        }
    }

}