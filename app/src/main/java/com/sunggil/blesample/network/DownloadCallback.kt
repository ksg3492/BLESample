package com.sunggil.blesample.network

interface DownloadCallback {
    fun onResult(success : Boolean, fileName : String, size : Long)
    fun onUpdate(data : ByteArray, size : Int, length : Long) : Boolean
}