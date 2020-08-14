package com.sunggil.blesample.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sunggil.blesample.*
import com.sunggil.blesample.base.BaseViewModel
import com.sunggil.blesample.data.*
import com.sunggil.blesample.network.DownloadCallback
import com.sunggil.blesample.network.RetrofitService
import com.sunggil.blesample.player.PlayerCallback
import com.sunggil.blesample.player.PlayerController
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : BaseViewModel(application), PlayerCallback {
    val TAG : String = "SG2"

    val toastMsg : MutableLiveData<String> = MutableLiveData()
    val logMsg : MutableLiveData<String> = MutableLiveData()
    val statusMsg : MutableLiveData<String> = MutableLiveData()
    val progressValue : MutableLiveData<String> = MutableLiveData()
    val durationValue : MutableLiveData<String> = MutableLiveData()
    val playpauseValue : MutableLiveData<Int> = MutableLiveData()
    val pageValue : MutableLiveData<String> = MutableLiveData()
    val loadingValue : MutableLiveData<Int> = MutableLiveData()
    val downloadValue : MutableLiveData<Int> = MutableLiveData()
    val preloadValue : MutableLiveData<Int> = MutableLiveData()
    val melonListValue : MutableLiveData<ArrayList<MelonItem>> = MutableLiveData()
    val thumbListValue : MutableLiveData<HashMap<Int, ByteArray>> = MutableLiveData()

    companion object {
        const val STATUS_CONNECT = 0
        const val STATUS_DISCONNECT = 1
        const val STATUS_BROKEN_PIPE = 2
    }

    //player controller
    lateinit var playerController : PlayerController

    //thread
    var isBrokenPipe = false
    var mConnectionStatus = STATUS_DISCONNECT
    var mDataTransferThread : DataTransferThread? = null
    var mConnectionThread : ConnectionThread? = null
    var mThumbThread : ThumbsnailThread? = null
    var isDestroy = false

    var fileDownloadStop = false
    var isPrepare = false
    var prevPercent = 0
    var wrote: Long = 0

    lateinit var mServerSocket : BluetoothServerSocket
    var mSocket : BluetoothSocket? = null

    var logcat = 0L
    var prepareLogcat = 0L
    var startPage = 0
    var endPage = 0

    var melonChartData = ArrayList<MelonItem>()

    fun setPlayerService(controller : PlayerController) {
        playerController = controller
        playerController.addPlayerCallback(this)
    }

    fun onMelonItemClick(item : MelonItem?) {
        uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
        prepareLogcat = System.currentTimeMillis()

        playerController.setPause()
        playerController.setStop()

        try {
            fileDownloadStop = true

            mDataTransferThread?.setDownloadStop()
        } catch (e: Exception) { }

        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progressValue)

        val progress2Value = uiHandler.obtainMessage(AppConst.COMMON.PRELOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.PRELOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progress2Value)

        Thread{
            val itemData = Gson().toJson(item)

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_STREAMING).apply {
                content = itemData.toByteArray()
            }

            mDataTransferThread?.write(ble)
        }.start()
    }

    fun onYoutubeItemClick(item : YoutubeItem?) {
        uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
        prepareLogcat = System.currentTimeMillis()

        playerController.setPause()
        playerController.setStop()

        try {
            fileDownloadStop = true

            mDataTransferThread?.setDownloadStop()
        } catch (e: Exception) { }

        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progressValue)

        val progress2Value = uiHandler.obtainMessage(AppConst.COMMON.PRELOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.PRELOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progress2Value)

        Thread{
            val itemData = Gson().toJson(item)

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_STREAMING).apply {
                content = itemData.toByteArray()
            }

            mDataTransferThread?.write(ble)
        }.start()
    }

    fun onBluetoothItemClick(device : BluetoothDevice) {
        makeThread()
        mConnectionThread?.setDevice(device)
        mConnectionThread?.start()
    }

    fun getMelonChart() {
        logcat = System.currentTimeMillis()

        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST).apply {
            arg1 = 0
        }

        mDataTransferThread?.write(ble)
    }

    fun getMelonPage(next : Boolean) : Boolean {
        val operate = if(next) 1 else -1
        if (next) {
            if (startPage + 1 > endPage) {
                toastMsg.value = "마지막 페이지"
                return false
            }
        } else {
            if (startPage - 1 < 0) {
                toastMsg.value = "첫 페이지"
                return false
            }
        }

        logcat = System.currentTimeMillis()

        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST).apply {
            arg1 = startPage + operate
        }
        mDataTransferThread?.write(ble)

        return true
    }

    fun makeThread() {
        mDataTransferThread = DataTransferThread()
        mConnectionThread = ConnectionThread()
    }

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


    lateinit var streamTask : StreamTask
    fun startYoutube(item : YoutubeItem) {
        val urlPath = "https://www.youtube.com/watch?v=${item.id}"

        if (item.url == null || "".equals(item.url)) {
            return;
        }

        streamTask = StreamTask(urlPath, object :
            StreamListener {
            override fun onResult(result: Boolean, isLive: Boolean, streams: List<VideoStream>?, liveUrl: String) {
                try {
                    if (result) {

                    } else {

                    }
                } catch (e : Exception) {

                }
            }
        })
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

    interface StreamListener {
        fun onResult(result : Boolean, isLive : Boolean, streams : List<VideoStream>?, liveUrl : String)
    }

    inner class StreamTask(val url : String, val callback : StreamListener) : Thread() {

        override fun run() {
            super.run()

            var list: List<VideoStream> = ArrayList()
            var liveUrl = ""
            var isLive = false

            try {
                NewPipe.init(DownloaderNewPipe.init(null), Localization("KR", "ko"))

                val infos = StreamInfo.getInfo(url)

                if (infos.getStreamType() == StreamType.LIVE_STREAM) {
                    isLive = true;
                    liveUrl = infos.getHlsUrl();
                    if (liveUrl == null) {
                        liveUrl = ""
                    }
                } else if (infos.getStreamType() == StreamType.VIDEO_STREAM) {
                    list = infos.getVideoStreams();
                }
            } catch (e : Exception) {
                Log.e("SG2","유튜브 파싱실패")
            }

            if (list != null && list!!.size > 0) {
                callback?.onResult(true, isLive, list, liveUrl);
            } else if (liveUrl != null && !liveUrl.equals("")) {
                callback?.onResult(true, isLive, list, liveUrl);
            } else {
                callback?.onResult(false, isLive, null, "")
            }
        }
    }

    fun getTimeStamp(time : Long) :String {
        val dateFormat = SimpleDateFormat("mm:ss.SSS")
        return dateFormat.format(time)
    }


    //innder classes

    inner class ConnectionThread : Thread {
        val mBluetoothAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        var mDevice : BluetoothDevice? = null

        constructor() {
            priority = MAX_PRIORITY
        }

        fun setDevice(d : BluetoothDevice) {
            mDevice = d
        }

        override fun run() {
            super.run()
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

            if (AppConst.COMMON.IS_CLIENT) {
                mSocket = mDevice?.createInsecureRfcommSocketToServiceRecord(uuid)

                mBluetoothAdapter.cancelDiscovery()

                try {
                    mSocket?.connect()
                    Log.e("SG2", "Bluetooth socket connect success")
                }catch (e : Exception) {
                    Log.e("SG2", "Bluetooth socket connect fail : " , e)
                    return
                }

                if (mSocket!= null && mSocket!!.isConnected) {
                    statusHandler.removeMessages(0)
                    statusHandler.sendEmptyMessage(0)
                    run(mSocket)
                }
            } else {
                mServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("SERVER", uuid)
                var shouldLoop = true

                while(shouldLoop) {
                    synchronized(this) {
                        mSocket = try {
                            mServerSocket.accept()
                        } catch (e: IOException) {
                            Log.e("SG2", "Socket's accept() method failed", e)
                            shouldLoop = false
                            null
                        }
                        mSocket?.also {
                            if (mSocket != null && mSocket!!.isConnected) {
                                statusHandler.removeMessages(0)
                                statusHandler.sendEmptyMessage(0)

                                mServerSocket.close()
                                shouldLoop = false

                                run(mSocket)
                                Log.e("SG2", "Bluetooth server socket connect success")
                            }

                        }
                    }

                }
            }
        }

        fun run(socket : BluetoothSocket?) {
            mDataTransferThread!!.setSocket(socket)
            mDataTransferThread!!.start()
        }
    }

    inner class DataTransferThread : Thread {
        var mOutputStream : ObjectOutputStream? = null
        var mInStream : ObjectInputStream? = null

        var mDownloadOutputStream : FileOutputStream? = null

        constructor() {
            priority = MAX_PRIORITY
        }

        fun setDownloadStop() {
            try {
                mDownloadOutputStream?.close()
                mDownloadOutputStream = null
            }catch (e: Exception) { }
        }

        fun isActive() : Boolean {
            return mOutputStream != null  && mInStream != null
        }

        fun setSocket(socket : BluetoothSocket?) {
            try {
                mOutputStream = ObjectOutputStream(socket!!.outputStream)
                mInStream = ObjectInputStream(socket!!.inputStream)
                isBrokenPipe = false
            } catch (e : Exception) {
                isBrokenPipe = true
            }
        }

        override fun run() {
            super.run()

            var isLoopStop = false

            while (!isLoopStop && !isDestroy && mInStream != null) {

//                synchronized(mInStream!!) {


                try {
                    //byte array to BLE Protocol
                    val bleProtocol = mInStream!!.readObject() as BLEProtocol

                    val command = bleProtocol.command

                    if (command == AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST) {
                        val ITEMS_SIZE = 10
                        val startPage = bleProtocol.arg1 //page  0,1,2,3...
                        var startIndex = ITEMS_SIZE * startPage

                        //image 가공 stop
                        mThumbThread?.stopGlide()
                        mThumbThread = null

                        if (melonChartData.size == 0) {
                            val call = RetrofitService.getInstance.getMelonChartList()
                            call.enqueue(object : Callback<MelonDomain> {
                                override fun onResponse(call: Call<MelonDomain>, response: Response<MelonDomain>) {
                                    if (!response.isSuccessful) {
                                        return
                                    }

                                    if (response.body() != null && response.body()!!.content != null) {
                                        melonChartData = response.body()!!.content!!
                                        val origin = response.body()!!.content!!
                                        var maxIndex = startIndex + ITEMS_SIZE - 1

                                        if (origin.size <= startIndex) {
                                            return
                                        } else if (origin.size <= maxIndex) {
                                            maxIndex = origin.size - 1
                                        }

                                        var items = ArrayList<MelonItem>()
                                        for (i in startIndex..maxIndex) {
                                            items.add(origin.get(i))
                                        }

                                        //list data 먼저 보냄
                                        val byteData = Gson().toJson(items)

                                        var ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST).apply {
                                            arg1 = startPage
                                            arg2 = if (origin.size / ITEMS_SIZE > 0 && origin.size % ITEMS_SIZE == 0) (origin.size / ITEMS_SIZE) - 1
                                                else (origin.size / ITEMS_SIZE)
                                            content = byteData.toByteArray()
                                        }
                                        write(ble)

                                        //image 가공
                                        mThumbThread = ThumbsnailThread(getApplication(), items,
                                            object : ThumbsnailThread.ThumbsListener {
                                                override fun onResult(position: Int, data: ByteArray) {
                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB).apply {
                                                        arg1 = startPage
                                                        arg2 = position
                                                        content = data
                                                    }
                                                    write(ble)
                                                }
                                            })
                                        mThumbThread?.start()
                                    }
                                }

                                override fun onFailure(call: Call<MelonDomain>, t: Throwable) {

                                }
                            })
                        } else {
                            //local
                            var maxIndex = startIndex + ITEMS_SIZE - 1

                            if (melonChartData.size <= startIndex) {
                                return
                            } else if (melonChartData.size <= maxIndex) {
                                maxIndex = melonChartData.size - 1
                            }

                            var items = ArrayList<MelonItem>()
                            for (i in startIndex..maxIndex) {
                                items.add(melonChartData.get(i))
                            }

                            //list data 먼저 보냄
                            val byteData = Gson().toJson(items)

                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST).apply {
                                arg1 = startPage
                                arg2 =
                                    if (melonChartData.size / ITEMS_SIZE > 0 && melonChartData.size % ITEMS_SIZE == 0) (melonChartData.size / ITEMS_SIZE) - 1
                                    else (melonChartData.size / ITEMS_SIZE)
                                content = byteData.toByteArray()
                            }
                            write(ble)

                            //logcat
                            val stamp = System.currentTimeMillis() - logcat

                            Handler(Looper.getMainLooper()).post {
                                logMsg.value = getTimeStamp(stamp)
                            }

                            //image 가공
                            mThumbThread = ThumbsnailThread(getApplication(), items,
                                object :
                                    ThumbsnailThread.ThumbsListener {
                                    override fun onResult(position: Int, data: ByteArray) {
                                        val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB).apply {
                                            arg1 = startPage
                                            arg2 = position  //maxPage 대신 position
                                            content = data
                                        }
                                        write(ble)
                                    }
                                })
                            mThumbThread?.start()
                        }
                    } else if (command == AppConst.CLIENT_TO_SERVER.GET_MELON_STREAMING) {
                        var byteLength = bleProtocol.contentLength

                        if (byteLength > 0) {
                            val readMessage = String(bleProtocol.content!!, 0, byteLength)
                            val melonItem = Gson().fromJson<MelonItem>(
                                readMessage,
                                object : TypeToken<MelonItem>() {}.type
                            )


                            stopDownload()

                            prepareLogcat = System.currentTimeMillis()
                            startStreaming(melonItem, object : StreamingCallback {
                                override fun onSuccess(item: MelonStreamingItem) {
                                    val name = "${item.ALBUMID}_${item.PERIOD}_${item.BITRATE}"

                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START).apply {
                                        message = name
                                    }
                                    write(ble)

                                    Log.e("SG2", "BLE_CTS_GET_MELON_STREAMING : ${item.PATH}")
                                    downloadStreamingFile(name, item.PATH, object : DownloadCallback {
                                        override fun onResult(success: Boolean, fileName: String, size: Long) {
                                            if (!success) {
                                                return
                                            }
                                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DONE).apply {
                                                arg2 = size.toInt()
                                                message = fileName
                                            }
                                            write(ble)
                                        }

                                        override fun onUpdate(data: ByteArray, read: Int, length: Long) {
                                            if (prepareLogcat != -1L) {
                                                val stamp = System.currentTimeMillis() - prepareLogcat
                                                val time = getTimeStamp(stamp)
                                                Log.e("SG2", "downloadStreamingFile logcat : ${time}")
                                                prepareLogcat = -1L
                                                Handler(Looper.getMainLooper()).post {
                                                    logMsg.value = time
                                                }
                                            }
                                            Log.e("SG2","${Util.isMainLooper()} ] MELON_STREAMING_FILE_DOWNLOADING  : ${read} sent"
                                            )
                                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING).apply {
                                                message = name
                                                arg1 = read
                                                arg2 = length.toInt()
                                                content = data
                                            }
                                            write(ble)
                                        }
                                    })

                                }

                                override fun onFail(error: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        toastMsg.value = "$error"
                                    }
                                }
                            })
                        } else {

                        }
                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START) {
                        Log.e("SG2", "BLE_STC_MELON_STREAMING_FILE_START")
                        val fileName = bleProtocol.message

                        val folderName =
                            AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH
                        val filepath = folderName + fileName
                        val folder = File(folderName)

                        if (!folder.exists()) {
                            folder.mkdirs()
                        }

                        val mp3File = File(filepath)
                        if (mp3File.exists()) {
                            Log.e("SG2", "download file already exist")
                            mp3File.delete()
                        }

                        mp3File.createNewFile()

                        try {
                            mDownloadOutputStream = FileOutputStream(mp3File)
                        } catch (e: Exception) {
                            Log.e("SG2", "RandomAccessFile error : ", e)
                        }

                        isPrepare = false
                        prevPercent = 0
                        fileDownloadStop = false
                        wrote = 0
                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING) {
                        val read = bleProtocol.arg1
                        val fileLength = bleProtocol.arg2
                        val fileName = bleProtocol.message
                        val dataStr = bleProtocol.content

                        try {
                            var percent = 0
                            var percent5: Int = 0


                            if (mDownloadOutputStream != null && !fileDownloadStop) {

                                mDownloadOutputStream!!.write(dataStr, 0, read)
                                wrote += read
                                percent = (wrote * 100L / fileLength).toInt()
                                percent5 = percent / AppConst.COMMON.MIN_PRELOAD_BUFFER

//                                    Log.e("SG2", "${Util.isMainLooper()} ] $count 번째 : ${wrote}byte 다운로드 완료 / total bytes : $fileLength")
                                if (percent != 100 && prevPercent != percent5) {
                                    prevPercent = percent5

                                    val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, percent, 0, null)
                                    uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
                                    uiHandler.sendMessage(progressValue)

                                    val filePath = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName

                                    val dataValue = exoPlayerHandlerMediaPlayer.obtainMessage(command, wrote.toInt(), fileLength, filePath)
                                    exoPlayerHandlerMediaPlayer.sendMessage(dataValue)
                                }

                            } else { }


                        } catch (e: Exception) {
                            Log.e("SG2", "download Error : ", e)
                        }
                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DONE) {
                        Log.e("SG2", "BLE_STC_MELON_STREAMING_FILE_DONE")


                        val fileName = bleProtocol.message
                        val fileLength = bleProtocol.arg2

                        Log.e("SG2", "${Util.isMainLooper()} ] 100% 다운로드 완료")

                        try {
                            mDownloadOutputStream?.flush()
                            mDownloadOutputStream?.close()
                        } catch (e: Exception) {

                        }

                        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, 100, 0, null)
                        uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
                        uiHandler.sendMessage(progressValue)

                        val filePath = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName
                        val dataValue = exoPlayerHandlerMediaPlayer.obtainMessage(command, 0, fileLength, filePath)
                        exoPlayerHandlerMediaPlayer.removeCallbacks(null)
                        exoPlayerHandlerMediaPlayer.sendMessageDelayed(dataValue, 0)

                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST) {
                        var startPage = bleProtocol.arg1  //startPage
                        var endPage = bleProtocol.arg2 //endPage
                        val byteLength = bleProtocol.contentLength

                        if (byteLength > 0) {
                            val logcat = System.currentTimeMillis()
                            val stamp = logcat - this@MainViewModel.logcat
                            val time = getTimeStamp(stamp)
                            Handler(Looper.getMainLooper()).post {
                                logMsg.value = "$time , $byteLength bytes"
                            }

                            //buffer -> Json Array
                            val readMessage = String(bleProtocol.content!!, 0, byteLength)
                            val json = Gson().fromJson<ArrayList<MelonItem>>(
                                readMessage,
                                object : TypeToken<ArrayList<MelonItem>>() {}.type
                            )

                            val pageValue = uiHandler.obtainMessage(
                                AppConst.SERVER_TO_CLIENT.PAGE_INDEX,
                                startPage,
                                endPage,
                                json
                            )
                            uiHandler.removeMessages(AppConst.SERVER_TO_CLIENT.PAGE_INDEX)
                            uiHandler.sendMessage(pageValue)

                            val dataValue = uiHandler.obtainMessage(
                                AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST,
                                0,
                                0,
                                json
                            )
                            uiHandler.removeMessages(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST)
                            uiHandler.sendMessage(dataValue)
                        } else {}
                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB) {
                        var startPage = bleProtocol.arg1  //startPage
                        var position = bleProtocol.arg2 //position
                        val byteLength = bleProtocol.contentLength

                        if (byteLength > 0) {
                            if (startPage == this@MainViewModel.startPage) {
                                //같은 페이지인지 확인
                                val thumbValue = uiHandler.obtainMessage(
                                    AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB,
                                    startPage,
                                    position,
                                    bleProtocol.content
                                )
                                uiHandler.sendMessage(thumbValue)
                            } else {}
                        } else {}
                    } else {}

                } catch (e: IOException) {
                    Log.e("SG2", "run() IOException : ", e)
                    if (!AppConst.COMMON.IS_CLIENT) {
                        Log.e("SG2", "서버 ack 대기")
                        uiHandler.removeMessages(AppConst.COMMON.RESTART_CONNECTION_SERVER)
                        uiHandler.sendEmptyMessage(AppConst.COMMON.RESTART_CONNECTION_SERVER)
                    } else {
                        uiHandler.sendEmptyMessage(AppConst.COMMON.RESTART_DATATRANSFER_THREAD)
                        uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_DISMISS)
                    }
                    isLoopStop = true
                    break
                } catch (e: Exception) {
                    Log.e("SG2", "run() Exception : ", e)
                }

//            }
            }
        }

        fun write(ble : BLEProtocol) {
            if (mOutputStream == null) {
                return;
            }
            synchronized(mOutputStream!!) {
                try {
                    mOutputStream?.flush()
                    mOutputStream?.reset()
                    mOutputStream?.writeObject(ble)
                } catch (e : IOException) {
                    Log.e("SG2","write error : ", e)
                }
            }
        }

        fun close() {
            try {
                mOutputStream?.close()
                mOutputStream = null
            } catch (e : Exception) {}

            try {
                mInStream?.close()
                mInStream = null
            } catch (e : Exception) {}

        }
    }

    //handlers

    val statusHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            if (!isBrokenPipe) {
                if (mDataTransferThread != null && mDataTransferThread!!.isActive()) {
                    if (mConnectionStatus != STATUS_CONNECT) {
                        mConnectionStatus = STATUS_CONNECT
                        statusMsg.value = "CONNECTED..."
                    }
                } else {
                    if (mConnectionStatus != STATUS_DISCONNECT) {
                        mConnectionStatus = STATUS_DISCONNECT
                        statusMsg.value = "DISCONNECTED..."
                    }
                }

            } else {
                if (mConnectionStatus != STATUS_BROKEN_PIPE) {
                    mConnectionStatus = STATUS_BROKEN_PIPE
                    statusMsg.value = "PIPE BROKEN...RETRY CONNECT SOCKET!!"
                }
            }


            sendEmptyMessageDelayed(0, 500)
        }
    }

    val exoPlayerHandlerMediaPlayer = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            if (msg.what == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START) {
                playerController.setStop()
                isPrepare = false
            } else if (msg.what == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING ||
                msg.what == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DONE) {

                if (fileDownloadStop) {
                    return
                }
                val fileWrote = msg.arg1
                val fileLength = msg.arg2
                val filePath : String = msg.obj as String
                val pathes = filePath.split("/")
                var fileName = ""
                if (pathes.size > 0) {
                    fileName = pathes[pathes.size - 1]
                }

                if (isPrepare) {
                    playerController.addByteData()
                } else {
                    isPrepare = true
                    playerController.prepare(true, "$fileLength", filePath)
                }

            }

        }
    }

    val uiHandler = object:Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            try {
                when (msg.what) {
                    AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST -> {
                        if (msg!= null) {
                            var obj = msg.obj as ArrayList<MelonItem>

                            melonListValue.value = obj
                        }
                    }
                    AppConst.SERVER_TO_CLIENT.PAGE_INDEX -> {
                        if (msg!= null) {
                            startPage = msg.arg1
                            endPage = msg.arg2

                            pageValue.value = "${startPage + 1} / ${endPage + 1}"
                        }
                    }
                    AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB -> {
                        if (msg!= null) {
                            startPage = msg.arg1
                            var position = msg.arg2
                            var obj = msg.obj as ByteArray

                            val map = HashMap<Int, ByteArray>()
                            map.put(position, obj)
                            thumbListValue.value = map
                        }
                    }
                    AppConst.COMMON.RESTART_CONNECTION_SERVER -> {
                        makeThread()
                        mConnectionThread?.start()
                    }
                    AppConst.COMMON.RESTART_DATATRANSFER_THREAD -> {
                        try {
                            mDataTransferThread = DataTransferThread()
                            mDataTransferThread!!.setSocket(mSocket)
                            mDataTransferThread!!.start()
                        } catch (e : Exception ) {
                            isBrokenPipe = true
                        }

                    }
                    AppConst.COMMON.LOADING_DIALOG_SHOW -> {
                        loadingValue.value = View.VISIBLE
                    }
                    AppConst.COMMON.LOADING_DIALOG_DISMISS -> {
                        loadingValue.value = View.GONE
                    }
                    AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI -> {
                        downloadValue.value = msg.arg1
                    }
                    AppConst.COMMON.PRELOAD_PERCENTAGE_UI -> {
                        preloadValue.value = msg.arg1
                    }
                }
            } catch (e : Exception) {
                Log.e("SG2","uiHandler : ", e)
            }
        }
    }

    //player callback

    override fun onPrepared(duration: Int) {
        Handler(Looper.getMainLooper()).post {
            durationValue.value = Util.convertMMSS(duration)
            val stamp = System.currentTimeMillis() - prepareLogcat
            logMsg.value = getTimeStamp(stamp)
        }

        playerController.setPlay()
        onBuffering(false)
    }

    override fun onPreload(percent: Int) {
        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.PRELOAD_PERCENTAGE_UI, percent, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.PRELOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progressValue)
    }

    override fun onPlayed() {
        Handler(Looper.getMainLooper()).post {
            playpauseValue.value = R.drawable.exo_icon_pause
        }
    }

    override fun onPaused() {
        Handler(Looper.getMainLooper()).post {
            playpauseValue.value = R.drawable.exo_icon_play
        }
    }

    override fun onCompletion(duration: Int) {
        Handler(Looper.getMainLooper()).post {
            playpauseValue.value = R.drawable.exo_icon_pause
        }
    }

    override fun onProgress(sec: Int) {
        Handler(Looper.getMainLooper()).post {
            progressValue.value = Util.convertMMSS(sec)
        }
    }

    override fun onBuffering(buffering : Boolean) {
        if (buffering) {
            uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
        } else {
            uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_DISMISS)
        }
    }

    override fun onBufferingEnd(seek : Long) {
    }

    override fun onError(errMsg: String?) {

    }

    override fun onDestroy() {
        Log.e("SG2", "ViewModel onDestroy")
        isDestroy = true

        try {
            mSocket?.close()
        } catch (e: Exception) {
            Log.e("SG2", "Could not close the connect socket", e)
        }

        try {
            if (::mServerSocket.isInitialized) {
                mServerSocket.close()
            }
        } catch (e: Exception) {
            Log.e("SG2", "Could not close the connect socket", e)
        }

        mDataTransferThread?.close()

        statusHandler.removeMessages(0)
        uiHandler.removeCallbacksAndMessages(null)
    }

    override fun onCleared() {
        Log.e("SG2", "ViewModel Cleared")
        super.onCleared()
    }
}