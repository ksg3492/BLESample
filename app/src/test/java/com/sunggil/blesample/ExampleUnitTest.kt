package com.sunggil.blesample

import android.os.Handler
import android.os.Message
import com.google.gson.Gson
import com.sunggil.blesample.data.MelonDomain
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {



        var b = ByteArray(10)
        b.set(0, 1)
        b.set(1, 2)
        b.set(2, 3)
        b.set(3, 4)
        b.set(4, 5)

        val str = String(b, 0, 5)
        str.toByteArray()
        println("before str : ${str.toByteArray().size}")


        str.toByteArray()
    }

}