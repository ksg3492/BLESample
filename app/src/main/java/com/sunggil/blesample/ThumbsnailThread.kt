package com.sunggil.blesample

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.sunggil.blesample.data.MelonItem

class ThumbsnailThread(var context : Context, var array : ArrayList<MelonItem>, var listener : ThumbsListener) : Thread() {
    private var isStop = false

    override fun run() {
        super.run()

        for (i in 0..(array.size-1)) {
            if (isStop) {
                Log.e("SG2","ThumbsThread Stop!!!!!!")
                return
            }

            val bytes = Glide.with(context)
                .`as`(ByteArray::class.java)
                .override(100)
                .load(array.get(i).albumImg)
                .submit()
                .get();

//            Log.e("SG2","ThumbsThread Index : ${i}, size : ${bytes.size}")
            listener?.onResult(i, bytes)
        }


    }

    fun stopGlide() {
        isStop = true
    }

    interface ThumbsListener {
        fun onResult(position : Int, data : ByteArray)
    }
}