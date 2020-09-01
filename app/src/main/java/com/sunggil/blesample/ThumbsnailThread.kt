package com.sunggil.blesample

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide

class ThumbsnailThread(var context : Context, var array : ArrayList<String>, var listener : ThumbsListener) : Thread() {
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
                .load(array.get(i))
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