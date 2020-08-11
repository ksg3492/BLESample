package com.sunggil.blesample.test

import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import com.sunggil.blesample.AppConst
import com.sunggil.blesample.data.MelonItem
import com.sunggil.blesample.data.MelonStatus
import com.sunggil.blesample.data.MelonStreamingItem
import com.sunggil.blesample.data.StreamingCallback
import com.sunggil.blesample.network.DownloadCallback
import com.sunggil.blesample.network.RetrofitService
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*

class MainViewModelTest : ViewModel() {
    val TAG : String = "SG2"

    fun startStreaming(item : MelonItem?, callback : StreamingCallback) {
        if (item == null)
            return

        val p = HashMap<String, String>()
        p.put("cookie", getCookie())
        p.put("mdn", MelonStatus.mdn);
        p.put("ukey", MelonStatus.member_key)
        p.put("menuid", item.menuId)
        p.put("cid", item.trackId)
        p.put("hwkey", MelonStatus.pcid)
        p.put("session_id", MelonStatus.session_id)
        p.put("changeST", MelonStatus.isChangeST)
        p.put("metaType", MelonStatus.metaType)

        MelonStatus.isChangeST = "N" //한번 동시 스트리밍 처리 후 초기화
        MelonStatus.cid = item.trackId
        MelonStatus.menuid = item.menuId
        MelonStatus.bitrate = ""
        MelonStatus.loggingToken = ""

        val call = RetrofitService.getInstance.getMelonStreaming(p)
        call.enqueue(object : Callback<MelonStreamingItem> {
            override fun onResponse(call: Call<MelonStreamingItem>, response: Response<MelonStreamingItem>) {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        val data : MelonStreamingItem = response.body()!!

                        MelonStatus.bitrate = data.BITRATE
                        MelonStatus.loggingToken = data.LOGGINGTOKEN

                        data.ALBUM = item.albumName
                        var result = data.RESULT

                        if (result.equals("0")) {
                            if (data.OPTION != null) {
                                data.ACTION = data.OPTION!!.ACTION
                                data.MESSAGE = data.OPTION!!.MESSAGE
                            } else {
                                data.ACTION = "1"
                                data.MESSAGE = ""
                            }
                        } else if (result.equals("-2002")) {
                            data.ACTION = result
                            callback.onFail(result);
                            return;
                        } else {
                            //권리사
                            data.ACTION = result
                            callback.onFail(data.MESSAGE)
                            return;
                        }

                        callback.onSuccess(data);
                    }
                } else {
                    callback.onFail(AppConst.ERROR.RESPONSE_NOT_SUCCESS);
                }
            }

            override fun onFailure(call: Call<MelonStreamingItem>, t: Throwable) {
                callback.onFail(AppConst.ERROR.RESPONSE_NOT_SUCCESS);
            }
        });
    }


    var writeThread : WriteResponseBodyToDisk? = null
    fun stopDownload() {
        if (writeThread != null) {
            writeThread!!.setStop()
            writeThread = null
        }
    }

    fun downloadStreamingFile(fileName : String, url : String, callback : DownloadCallback) {
        Log.e("SG2","downloadStreamingFile is Main Looper? ${Looper.myLooper() == Looper.getMainLooper()}")
        if (url == null || url.equals(""))
            return

        val call = RetrofitService.getInstance.downloadFileWithDynamicUrl(url)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    writeThread = WriteResponseBodyToDisk(fileName, response.body()!!, callback)
                    writeThread!!.start()
                } else {
                    callback.onResult(false, "", 0)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                callback.onResult(false, "", 0)
            }

        })
    }

    fun getCookie() : String {
        var cookie = ""

        cookie += "PCID=" + MelonStatus.pcid + ";"
        if (MelonStatus.cookies != null && MelonStatus.cookies!!.size > 0) {
            for (i in 0..(MelonStatus.cookies!!.size - 1)) {
                cookie += " " + MelonStatus.cookies!!.get(i) + ";"
            }
        }

        return cookie
    }

    inner class WriteResponseBodyToDisk(var fileName : String, var body : ResponseBody, var callback : DownloadCallback?) : Thread() {
        var isStop = false

        var inputStream : InputStream? = null
        var outputStream : FileOutputStream? = null

        var fileLength : Long = 0L

        override fun run() {
            super.run()

            Log.e("SG2","writeResponseBodyToDisk is Main Looper? ${Looper.myLooper() == Looper.getMainLooper()}")
            try {
                val folderpath =
                    AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH
                val folderFile = File(folderpath);

                if (!folderFile.exists()) {
                    try {
                        folderFile.mkdirs();
                    } catch (e : Exception) {
                        Log.e(TAG,"mkdirs() Error : ", e);
                    }
                }

                val filepath = folderpath + fileName;
                val mp3File = File(filepath);

                if (!mp3File.exists()) {
                    try {
                        mp3File.createNewFile();
                    } catch (e : IOException) {
                        Log.e(TAG,"createNewFile() Error : ", e);
                    }
                }

                try {
                    try {
                        outputStream = FileOutputStream(mp3File);
                    } catch (e : FileNotFoundException) {
                        Log.e(TAG,"FileNotFoundException : ", e)
                    }

                    var sumLength = 0L;
                    fileLength = body.contentLength()

                    Log.e("SG2","filelength : " + fileLength);
                    inputStream = BufferedInputStream(body?.byteStream())
                    val bufferSize = (fileLength / 9).toInt()      //11%씩?
//                    val bufferSizeAdd = 1024 * 16

                    val fileReader = ByteArray(bufferSize)
                    var count = 0
                    isStop = false
                    while (!isStop) {
                        if (outputStream != null && inputStream != null) {
                            val read = inputStream!!.read(fileReader);

                            if (read == -1 || isStop) {
                                break;
                            }
//                            outputStream!!.write(fileReader, 0, read);
                            callback?.onUpdate(fileReader, read, fileLength);
                            count++
                            sumLength += read;
                        }
                    }

                    outputStream?.flush();


                    Log.e(TAG,"writeToDisk()");
                } catch (e : Exception) {
                    Log.e(TAG,"writeToDisk Error : ", e);
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                    inputStream = null
                    outputStream = null
                }
            } catch (e : Exception) {
            }

            callback?.onResult(!isStop, fileName, fileLength)
        }

        fun setStop() {
            isStop = true
            try {
                inputStream?.close()
                outputStream?.close()
            }catch (e : Exception) {}

            interrupt()
        }
    }
}