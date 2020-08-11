package com.sunggil.blesample.data

interface StreamingCallback {
    fun onSuccess(item : MelonStreamingItem)

    fun onFail(error : String)
}