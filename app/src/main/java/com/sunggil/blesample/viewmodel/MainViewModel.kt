package com.sunggil.blesample.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.annotation.NonNull
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
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : BaseViewModel(application), PlayerCallback {
    val TAG : String = "SG2"

    val toastMsg : MutableLiveData<String> = MutableLiveData()
    val logMsg : MutableLiveData<String> = MutableLiveData()
    val statusMsg : MutableLiveData<String> = MutableLiveData()
    val progressValue : MutableLiveData<Int> = MutableLiveData()
    val durationValue : MutableLiveData<Int> = MutableLiveData()
    val playpauseValue : MutableLiveData<Int> = MutableLiveData()
    val pageValue : MutableLiveData<String> = MutableLiveData()
    val loadingValue : MutableLiveData<Int> = MutableLiveData()
    val downloadValue : MutableLiveData<Int> = MutableLiveData()
    val preloadValue : MutableLiveData<Int> = MutableLiveData()
    val downloadedSizeValue : MutableLiveData<Int> = MutableLiveData()
    val downloadedSpeedValue : MutableLiveData<Float> = MutableLiveData()
    val melonListValue : MutableLiveData<ArrayList<MelonItem>> = MutableLiveData()
    val melonThumbsValue : MutableLiveData<HashMap<Int, ByteArray>> = MutableLiveData()
    val youtubeListValue : MutableLiveData<ArrayList<YoutubeItem>> = MutableLiveData()
    val youtubeThumbsValue : MutableLiveData<HashMap<Int, ByteArray>> = MutableLiveData()
    val surfaceVisibility : MutableLiveData<Int> = MutableLiveData()

    companion object {
        const val STATUS_CONNECT = 0
        const val STATUS_DISCONNECT = 1
        const val STATUS_BROKEN_PIPE = 2
    }

    //player controller
    lateinit var playerController : PlayerController

    //thread
    var mSelectedDevice : BluetoothDevice? = null
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
    var youtubeHotData = ArrayList<YoutubeItem>()

    fun setPlayerService(controller : PlayerController) {
        playerController = controller
        playerController.addPlayerCallback(this)
    }

    fun onMelonItemClick(item : MelonItem?) {
        uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
        prepareLogcat = System.currentTimeMillis()

//        playerController.setPause()
        playerController.setStop()

        stopDownload()

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

//        playerController.setPause()
        playerController.setStop()

        stopDownload()

        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progressValue)

        val progress2Value = uiHandler.obtainMessage(AppConst.COMMON.PRELOAD_PERCENTAGE_UI, 0, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.PRELOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progress2Value)

        Thread{
            val itemData = Gson().toJson(item)

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_YOUTUBE_STREAMING).apply {
                content = itemData.toByteArray()
            }

            mDataTransferThread?.write(ble)
        }.start()
    }

    fun stopDownload() {
        try {
            fileDownloadStop = true

            mDataTransferThread?.setDownloadStop()
        } catch (e: Exception) { }
    }

    fun stopUpload() {
        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.STOP_UPLOAD_STREAMING).apply {
            arg1 = 0
        }

        mDataTransferThread?.write(ble)
    }

    fun onBluetoothItemClick(device : BluetoothDevice?) {
        mSelectedDevice = device
        makeThread()
        mConnectionThread?.setDevice(device)
        mConnectionThread?.start()
    }

    fun retryConnect() {
        onBluetoothItemClick(mSelectedDevice)
    }

    fun getMelonChart() {
        logcat = System.currentTimeMillis()

        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST).apply {
            arg1 = 0
        }

        mDataTransferThread?.write(ble)
    }

    fun getYoutubeChart() {
        logcat = System.currentTimeMillis()

        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_YOUTUBE_CHART_LIST).apply {
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

    fun getYoutubePage(next : Boolean) : Boolean {
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

        val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_YOUTUBE_CHART_LIST).apply {
            arg1 = startPage + operate
        }
        mDataTransferThread?.write(ble)

        return true
    }

    fun makeThread() {
        mDataTransferThread = DataTransferThread()
        mConnectionThread = ConnectionThread()
    }

    fun startMelonStreaming(item : MelonItem?, callback : StreamingCallback) {
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
    fun startYoutubeStreaming(item : YoutubeItem, listener : StreamListener) {
        val urlPath = "https://www.youtube.com/watch?v=${item.id}"

        if ("".equals(item.url)) {
            return
        }

        streamTask = StreamTask(urlPath, listener)
        streamTask.start()
    }


    var writeThread : WriteResponseBodyToDisk? = null
    var sendThread : PassToClientData? = null
    fun stopWrite() {
        writeThread?.setStop()
        writeThread = null
        sendThread?.setStop()
        sendThread = null
    }

    fun downloadStreamingFile(fileName : String, url : String, @NonNull callback : DownloadCallback) {
        if (url == null || url.equals(""))
            return

        val call = RetrofitService.getInstance.downloadFileWithDynamicUrl(url)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    makeFile(fileName)

                    writeThread = WriteResponseBodyToDisk(fileName, response.body()!!)
                    writeThread?.start()

                    sendThread = PassToClientData(fileName, response.body()!!.contentLength(), callback)
                    sendThread?.start()
                } else {
                    callback.onResult(false, "", 0)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                callback.onResult(false, "", 0)
            }

        })
    }

    fun makeFile(fileName : String) {
        val folderpath = AppConst.RETROFIT.DOWNLOAD_SERVER_FOLDER_PATH
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

    inner class WriteResponseBodyToDisk(var fileName : String, var body : ResponseBody) : Thread() {
        var isStop = false

        var downloadIS : InputStream? = null

        var fileLength : Long = 0L

        override fun run() {
            super.run()

            try {
                val filePath = AppConst.RETROFIT.DOWNLOAD_SERVER_FOLDER_PATH + fileName
                val file = File(filePath);

                try {
                    fileLength = body.contentLength()

                    Log.e("SG2","filelength : " + fileLength);
                    downloadIS = BufferedInputStream(body?.byteStream())
                    val outputStream = file.outputStream()

                    val fileReader = ByteArray(32 * 1024)
                    isStop = false

                    while (!isStop) {
                        if (downloadIS != null) {
                            val read = downloadIS!!.read(fileReader);

                            if (read == -1 || isStop) {
                                break
                            }
                            outputStream.write(fileReader, 0, read)
                        }
                    }
                    outputStream.flush()
                    outputStream.close()

                    downloadIS?.close()
                    downloadIS = null

                    Log.e(TAG,"writeToDisk()");
                } catch (e : Exception) {
                    Log.e(TAG,"writeToDisk Error : ", e);
                } finally {
                }
            } catch (e : Exception) {
            }
        }

        fun setStop() {
            isStop = true

            try {
                interrupt()
            }catch (e : Exception) {}
        }
    }

    inner class PassToClientData(var fileName : String, var fileLength : Long, var callback : DownloadCallback) : Thread() {
        var isStop = false

        var downloadedIS : InputStream? = null

        override fun run() {
            super.run()

            try {
                val filePath = AppConst.RETROFIT.DOWNLOAD_SERVER_FOLDER_PATH + fileName
                val file = File(filePath);

                downloadedIS = BufferedInputStream(file.inputStream())
                val bufferSize = AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE
                val fileReader = ByteArray(bufferSize)

                var sended = 0
                while (!isStop) {
                    if (downloadedIS != null) {
                        val downloadedSize = file.length()
                        Log.e("SG2","PassToClientData downloadedSize : $downloadedSize")

                        if (downloadedSize != fileLength) {
                            if ((sended + bufferSize) > downloadedSize) {
                                Log.e("SG2","Client Send 대기...")
                                sleep(100)
                                continue
                            }
                        }

                        val read = downloadedIS!!.read(fileReader);

                        if (read == -1 || isStop) {
                            Log.e(TAG,"break read : $read, isStop : $isStop, ");
                            break
                        }

                        if (!callback.onUpdate(fileReader, read, fileLength)) {
                            Log.e(TAG,"onUpdate false");
                            break
                        }

                        sended += read
                    }
                }
                Log.e(TAG,"passToClient out isStop : $isStop, ");

                downloadedIS?.close()
                downloadedIS = null

                Log.e(TAG,"passToClient()");
            } catch (e : Exception) {

                Log.e("SG2","passToClient Error : ${e.message}");
            }

            callback?.onResult(!isStop, fileName, fileLength)
        }

        fun setStop() {
            isStop = true

            try {
                interrupt()
            }catch (e : Exception) {}
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
                Log.e("SG2","유튜브 파싱시작")
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
                Log.e("SG2","유튜브 파싱실패 : ", e)
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

        fun setDevice(d : BluetoothDevice?) {
            mDevice = d
        }

        override fun run() {
            super.run()
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

            if (AppConst.COMMON.IS_CLIENT) {
                initSocket()
                try {
                    mSocket = mDevice?.createInsecureRfcommSocketToServiceRecord(uuid)

//                    var socket = mDevice?.createInsecureRfcommSocketToServiceRecord(uuid)
//                    var clazz = socket?.remoteDevice?.javaClass
//                    var paramTypes = arrayOf<Class<*>>(Integer.TYPE)
//                    var m = clazz?.getMethod("createRfcommSocket", *paramTypes)
//                    mSocket = m?.invoke(socket?.remoteDevice, Integer.valueOf(1)) as BluetoothSocket
                }catch (e : Exception) {
                    Log.e("SG2", "Bluetooth create socket error : ${e.message}")
                }

                mBluetoothAdapter.cancelDiscovery()

                try {
                    mSocket?.connect()

                    Log.e("SG2", "Bluetooth socket connect success")
                }catch (e : Exception) {
                    Log.e("SG2", "Bluetooth socket connect fail : " , e)
                    return
                }

//                if (mSocket!= null && mSocket!!.isConnected) {
                    statusHandler.removeMessages(0)
                    statusHandler.sendEmptyMessage(0)
                    run(mSocket)
//                }
            } else {
                initSocket()

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
//                            if (mSocket != null && mSocket!!.isConnected) {
                                statusHandler.removeMessages(0)
                                statusHandler.sendEmptyMessage(0)

                                mServerSocket.close()
                                shouldLoop = false

                                run(mSocket)
                                Log.e("SG2", "Bluetooth server socket connect success")
//                            }

                        }
                    }

                }
            }
        }

        fun initSocket() {
            try {
                mSocket?.inputStream?.close()
            }catch (e : Exception) { }

            try {
                mSocket?.outputStream?.close()
            }catch (e : Exception) { }

            try {
                mSocket = null
            }catch (e : Exception) { }
        }

        fun run(socket : BluetoothSocket?) {
            mDataTransferThread!!.setSocket(socket)
            mDataTransferThread!!.start()

            pingHandler.removeMessages(0)
            pingHandler.sendEmptyMessageDelayed(0, 5000)
        }
    }

    inner class DataTransferThread : Thread {
        var mOutputStream : ObjectOutputStream? = null
        var mInStream : ObjectInputStream? = null

        var mDownloadOutputStream : FileOutputStream? = null
        var mSocket : BluetoothSocket? = null

        var prevDownloadLocat : Long = 0L
        var prevDownloadTimeLocat : Long = 0L

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
                mSocket = socket
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

            while (!isLoopStop && !isDestroy && mSocket != null && mSocket!!.inputStream != null) {

//                synchronized(mInStream!!) {

                try {
                    //byte array to BLE Protocol
                    val bleProtocol = mInStream!!.readObject() as BLEProtocol

                    val command = bleProtocol.command

                    if (command == AppConst.COMMON.PING) {
//                        Log.e("SG2","[PING CHECK!]")
                        continue
                    }

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

                                        val items = ArrayList<MelonItem>()
                                        val thumbs = ArrayList<String>()
                                        for (i in startIndex..maxIndex) {
                                            items.add(origin.get(i))
                                            thumbs.add(origin.get(i).albumImg)
                                        }

                                        //list data 먼저 보냄
                                        val byteData = Gson().toJson(items)

                                        val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST).apply {
                                            arg1 = startPage
                                            arg2 = if (origin.size / ITEMS_SIZE > 0 && origin.size % ITEMS_SIZE == 0) (origin.size / ITEMS_SIZE) - 1
                                                else (origin.size / ITEMS_SIZE)
                                            content = byteData.toByteArray()
                                        }
                                        write(ble)

                                        //image 가공
                                        mThumbThread = ThumbsnailThread(getApplication(), thumbs,
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

                            val items = ArrayList<MelonItem>()
                            val thumbs = ArrayList<String>()
                            for (i in startIndex..maxIndex) {
                                items.add(melonChartData.get(i))
                                thumbs.add(melonChartData.get(i).albumImg)
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
                            mThumbThread = ThumbsnailThread(getApplication(), thumbs,
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

                            stopWrite()

                            prepareLogcat = System.currentTimeMillis()
                            startMelonStreaming(melonItem, object : StreamingCallback {
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

                                        override fun onUpdate(data: ByteArray, read: Int, length: Long): Boolean {
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
                                            return write(ble)
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
                    } else if (command == AppConst.CLIENT_TO_SERVER.GET_YOUTUBE_CHART_LIST) {
                        val ITEMS_SIZE = 10
                        val startPage = bleProtocol.arg1 //page  0,1,2,3...
                        var startIndex = ITEMS_SIZE * startPage

                        //image 가공 stop
                        mThumbThread?.stopGlide()
                        mThumbThread = null

                        if (youtubeHotData.size == 0) {
                            val call = RetrofitService.getInstance.getYoutubeHotList()
                            call.enqueue(object : Callback<YoutubeDomain> {
                                override fun onResponse(call: Call<YoutubeDomain>, response: Response<YoutubeDomain>) {
                                    if (!response.isSuccessful) {
                                        return
                                    }

                                    if (response.body() != null && response.body()!!.content != null) {
                                        youtubeHotData = response.body()!!.content!!
                                        val origin = response.body()!!.content!!
                                        var maxIndex = startIndex + ITEMS_SIZE - 1

                                        if (origin.size <= startIndex) {
                                            return
                                        } else if (origin.size <= maxIndex) {
                                            maxIndex = origin.size - 1
                                        }

                                        val items = ArrayList<YoutubeItem>()
                                        val thumbs = ArrayList<String>()
                                        for (i in startIndex..maxIndex) {
                                            items.add(origin.get(i))
                                            thumbs.add(origin.get(i).thumbnail)
                                        }

                                        //list data 먼저 보냄
                                        val byteData = Gson().toJson(items)

                                        var ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST).apply {
                                            arg1 = startPage
                                            arg2 = if (origin.size / ITEMS_SIZE > 0 && origin.size % ITEMS_SIZE == 0) (origin.size / ITEMS_SIZE) - 1
                                                    else (origin.size / ITEMS_SIZE)
                                            content = byteData.toByteArray()
                                        }
                                        write(ble)

                                        //image 가공
                                        mThumbThread = ThumbsnailThread(getApplication(), thumbs,
                                            object : ThumbsnailThread.ThumbsListener {
                                                override fun onResult(position: Int, data: ByteArray) {
                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST_THUMB).apply {
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

                                override fun onFailure(call: Call<YoutubeDomain>, t: Throwable) {

                                }
                            })
                        } else {
                            //local
                            var maxIndex = startIndex + ITEMS_SIZE - 1

                            if (youtubeHotData.size <= startIndex) {
                                return
                            } else if (youtubeHotData.size <= maxIndex) {
                                maxIndex = youtubeHotData.size - 1
                            }

                            val items = ArrayList<YoutubeItem>()
                            val thumbs = ArrayList<String>()
                            for (i in startIndex..maxIndex) {
                                items.add(youtubeHotData.get(i))
                                thumbs.add(youtubeHotData.get(i).thumbnail)
                            }

                            //list data 먼저 보냄
                            val byteData = Gson().toJson(items)

                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST).apply {
                                arg1 = startPage
                                arg2 = if (youtubeHotData.size / ITEMS_SIZE > 0 && youtubeHotData.size % ITEMS_SIZE == 0) (youtubeHotData.size / ITEMS_SIZE) - 1
                                        else (youtubeHotData.size / ITEMS_SIZE)
                                content = byteData.toByteArray()
                            }
                            write(ble)

                            //logcat
                            val stamp = System.currentTimeMillis() - logcat

                            Handler(Looper.getMainLooper()).post {
                                logMsg.value = getTimeStamp(stamp)
                            }

                            //image 가공
                            mThumbThread = ThumbsnailThread(getApplication(), thumbs,
                                object :
                                    ThumbsnailThread.ThumbsListener {
                                    override fun onResult(position: Int, data: ByteArray) {
                                        val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST_THUMB).apply {
                                            arg1 = startPage
                                            arg2 = position  //maxPage 대신 position
                                            content = data
                                        }
                                        write(ble)
                                    }
                                })
                            mThumbThread?.start()
                        }
                    }  else if (command == AppConst.CLIENT_TO_SERVER.GET_YOUTUBE_STREAMING) {
                        var byteLength = bleProtocol.contentLength

                        if (byteLength > 0) {
                            val readMessage = String(bleProtocol.content!!, 0, byteLength)
                            val youtubeItem = Gson().fromJson<YoutubeItem>(readMessage, object : TypeToken<YoutubeItem>() {}.type)

                            stopWrite()

                            prepareLogcat = System.currentTimeMillis()
                            startYoutubeStreaming(youtubeItem, object : StreamListener {
                                override fun onResult(result: Boolean, isLive: Boolean, streams: List<VideoStream>?, liveUrl: String) {
                                    try {
                                        if (result) {
//                                            val name = "${youtubeItem.title}_${youtubeItem.id}"
                                            val name = "${youtubeItem.id}"

                                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_START).apply {
                                                message = name
                                            }
                                            write(ble)


                                            for (vs in streams!!) {
                                                val res = vs.resolution
                                                Log.e("SG2","streams.. $res")
                                            }
                                            val fileUrl = if (isLive) liveUrl else { streams?.get(0)?.getUrl() } ?: ""


                                            Log.e("SG2", "BLE_CTS_GET_YOUTUBE_STREAMING : ${fileUrl}")
                                            downloadStreamingFile(name, fileUrl, object : DownloadCallback {
                                                override fun onResult(success: Boolean, fileName: String, size: Long) {
                                                    if (!success) {
                                                        return
                                                    }
                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DONE).apply {
                                                        arg2 = size.toInt()
                                                        message = fileName
                                                    }
                                                    write(ble)
                                                }

                                                override fun onUpdate(data: ByteArray, read: Int, length: Long) : Boolean {
                                                    if (prepareLogcat != -1L) {
                                                        val stamp = System.currentTimeMillis() - prepareLogcat
                                                        val time = getTimeStamp(stamp)
                                                        Log.e("SG2", "downloadStreamingFile logcat : ${time}")
                                                        prepareLogcat = -1L
                                                        Handler(Looper.getMainLooper()).post {
                                                            logMsg.value = time
                                                        }
                                                    }
                                                    Log.e("SG2","${Util.isMainLooper()} ] YOUTUBE_STREAMING_FILE_DOWNLOADING  : ${read} sent")
                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DOWNLOADING).apply {
                                                        message = name
                                                        arg1 = read
                                                        arg2 = length.toInt()
                                                        content = data
                                                    }
                                                    return write(ble)
                                                }
                                            })
                                        } else {

                                        }
                                    } catch (e : Exception) {
                                        Handler(Looper.getMainLooper()).post {
                                            toastMsg.value = "${e.message}"
                                        }
                                    }
                                }
                            })
                        } else {

                        }
                    } else if (command == AppConst.CLIENT_TO_SERVER.STOP_UPLOAD_STREAMING) {
                        stopWrite()
                    } else if (command == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START) {
                        Log.e("SG2", "BLE_STC_MELON_STREAMING_FILE_START")
                        val fileName = bleProtocol.message

                        val folderName = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH
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
                        prevPercent = -1
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
                                percent5 = percent / AppConst.COMMON.MIN_PRELOAD_BUFFER_PERCENT

//                                    Log.e("SG2", "${Util.isMainLooper()} ] $count 번째 : ${wrote}byte 다운로드 완료 / total bytes : $fileLength")
                                if (percent != 100 && prevPercent != percent5) {
                                    prevPercent = percent5

//                                    Log.e("SG2", "${Util.isMainLooper()} ] ${wrote}byte 다운로드 완료 / total bytes : $fileLength")

                                    val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, percent, 0, null)
                                    uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
                                    uiHandler.sendMessage(progressValue)

//                                    val downloadedValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOADED_SIZE_UI, wrote, 0, null)
//                                    uiHandler.removeMessages(AppConst.COMMON.DOWNLOADED_SIZE_UI)
//                                    uiHandler.sendMessage(downloadedValue)

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

                    } else if (command == AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_START) {
                        Log.e("SG2", "BLE_STC_YOUTUBE_STREAMING_FILE_START")
                        val fileName = bleProtocol.message

                        val folderName = AppConst.RETROFIT.DOWNLOAD_MP4_FOLDER_PATH
                        val filepath = folderName + fileName
                        val folder = File(folderName)

                        if (!folder.exists()) {
                            folder.mkdirs()
                        }

                        val mp4File = File(filepath)

                        try {
                            if (mp4File.exists()) {
                                Log.e("SG2", "download file already exist")
                                mp4File.delete()
                            }

                            mp4File.createNewFile()
                        }catch(e2:Exception) {
                            Log.e("SG2", "createNewFile error : ", e2)
                        }


                        try {
                            mDownloadOutputStream = FileOutputStream(mp4File)
                        } catch (e: Exception) {
                            Log.e("SG2", "RandomAccessFile error : ", e)
                        }

                        isPrepare = false
                        prevPercent = 0
                        fileDownloadStop = false
                        wrote = 0
                    } else if (command == AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DOWNLOADING) {
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
                                percent5 = (wrote / AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE).toInt()

                                if (prevDownloadLocat != 0L) {
                                    val diffSize = wrote - prevDownloadLocat
                                    val diffTime = System.currentTimeMillis() - prevDownloadTimeLocat
                                    val speed =  (diffSize * 1.00f) / (diffTime * 1.00f)

                                    val speedValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_SPEED_UI, 0, 0, speed)
                                    uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_SPEED_UI)
                                    uiHandler.sendMessage(speedValue)
                                }

                                prevDownloadLocat = wrote
                                prevDownloadTimeLocat = System.currentTimeMillis()


                                if (percent != 100 && prevPercent != percent5) {
                                    prevPercent = percent5

//                                    Log.e("SG2", "${Util.isMainLooper()} ] ${wrote}byte 다운로드 완료 / total bytes : $fileLength")

                                    val progressValue = uiHandler.obtainMessage(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI, percent, 0, null)
                                    uiHandler.removeMessages(AppConst.COMMON.DOWNLOAD_PERCENTAGE_UI)
                                    uiHandler.sendMessage(progressValue)

                                    val filePath = AppConst.RETROFIT.DOWNLOAD_MP4_FOLDER_PATH + fileName

                                    val dataValue = exoPlayerHandlerMediaPlayer.obtainMessage(command, wrote.toInt(), fileLength, filePath)
                                    exoPlayerHandlerMediaPlayer.sendMessage(dataValue)
                                }

                            } else { }


                        } catch (e: Exception) {
                            Log.e("SG2", "download Error : ", e)
                        }
                    } else if (command == AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DONE) {
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

                        val filePath = AppConst.RETROFIT.DOWNLOAD_MP4_FOLDER_PATH + fileName
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
                    } else if (command == AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST) {
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
                            val json = Gson().fromJson<ArrayList<YoutubeItem>>(readMessage, object : TypeToken<ArrayList<YoutubeItem>>() {}.type)

                            val pageValue = uiHandler.obtainMessage(AppConst.SERVER_TO_CLIENT.PAGE_INDEX, startPage, endPage, json)

                            uiHandler.removeMessages(AppConst.SERVER_TO_CLIENT.PAGE_INDEX)
                            uiHandler.sendMessage(pageValue)

                            val dataValue = uiHandler.obtainMessage(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST, 0, 0, json)

                            uiHandler.removeMessages(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST)
                            uiHandler.sendMessage(dataValue)
                        } else {}
                    } else if (command == AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST_THUMB) {
                        var startPage = bleProtocol.arg1  //startPage
                        var position = bleProtocol.arg2 //position
                        val byteLength = bleProtocol.contentLength

                        if (byteLength > 0) {
                            if (startPage == this@MainViewModel.startPage) {
                                //같은 페이지인지 확인
                                val thumbValue = uiHandler.obtainMessage(AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST_THUMB, startPage, position, bleProtocol.content)

                                uiHandler.sendMessage(thumbValue)
                            } else {}
                        } else {}
                    }

                    SystemClock.sleep(1);
                } catch (e2 : SocketTimeoutException) {
                    Log.e("SG2", "run() SocketTimeoutException : ", e2)
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
                }
                catch (e: Exception) {
                    Log.e("SG2", "run() Exception : ", e)
                }

//            }
            }
        }

        fun write(ble : BLEProtocol) : Boolean {
            if (mOutputStream == null) {
                return false
            }
            synchronized(mOutputStream!!) {
                try {
                    mOutputStream?.reset()
                    mOutputStream?.writeObject(ble)
                    mOutputStream?.flush()
                    return true
                } catch (e : IOException) {
                    Log.e("SG2","write error : ", e)

                    return false
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

            try {
                mSocket?.close()
                mSocket = null
            } catch (e : Exception) {}

        }
    }

    //handlers

    val pingHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            if (isBrokenPipe) {
                return
            }

            mDataTransferThread?.write(BLEProtocol(AppConst.COMMON.PING))

            removeMessages(0)
            sendEmptyMessageDelayed(0, 2000)
        }
    }

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

            when (msg.what) {
                AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START,
                AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_START -> {
                    playerController.setStop()
                    isPrepare = false
                }

                AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING,
                AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DONE,
                AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DOWNLOADING,
                AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DONE -> {
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

                    downloadedSizeValue.value = fileWrote

                    if (isPrepare) {
                        playerController.addByteData()
                    } else {
                        isPrepare = true
                        if (msg.what == AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DOWNLOADING ||
                            msg.what == AppConst.SERVER_TO_CLIENT.YOUTUBE_STREAMING_FILE_DONE) {
                            playerController.setIsVideo(true)
                            surfaceVisibility.value = View.VISIBLE
                        } else {
                            playerController.setIsVideo(false)
                            surfaceVisibility.value = View.GONE
                        }
                        playerController.prepare(true, "$fileLength", filePath)
                    }
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
                    AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST -> {
                        if (msg!= null) {
                            var obj = msg.obj as ArrayList<YoutubeItem>

                            youtubeListValue.value = obj
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
                            melonThumbsValue.value = map
                        }
                    }
                    AppConst.SERVER_TO_CLIENT.YOUTUBE_HOT_LIST_THUMB -> {
                        if (msg!= null) {
                            startPage = msg.arg1
                            var position = msg.arg2
                            var obj = msg.obj as ByteArray

                            val map = HashMap<Int, ByteArray>()
                            map.put(position, obj)
                            youtubeThumbsValue.value = map
                        }
                    }
                    AppConst.COMMON.RESTART_CONNECTION_SERVER -> {
                        makeThread()
                        mConnectionThread?.start()
                    }
                    AppConst.COMMON.RESTART_DATATRANSFER_THREAD -> {
                        isBrokenPipe = true
//                        try {
//                            mDataTransferThread = DataTransferThread()
//                            mDataTransferThread!!.setSocket(mSocket)
//                            mDataTransferThread!!.start()
//                        } catch (e : Exception ) {
//
//                        }
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
                    AppConst.COMMON.DOWNLOAD_SPEED_UI -> {
                        downloadedSpeedValue.value = msg.obj as Float
                    }
                    AppConst.COMMON.PRELOAD_PERCENTAGE_UI -> {
                        preloadValue.value = msg.arg1
                    }
                    AppConst.COMMON.DOWNLOADED_SIZE_UI -> {
                        downloadedSizeValue.value = msg.arg1
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
            durationValue.value = duration
            val stamp = System.currentTimeMillis() - prepareLogcat
            logMsg.value = getTimeStamp(stamp)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            playerController.setPlay()
            onBuffering(false)
        }, 500)
    }

    override fun onPreload(percent: Int) {
        val progressValue = uiHandler.obtainMessage(AppConst.COMMON.PRELOAD_PERCENTAGE_UI, percent, 0, null)
        uiHandler.removeMessages(AppConst.COMMON.PRELOAD_PERCENTAGE_UI)
        uiHandler.sendMessage(progressValue)
    }

    override fun onPlayed() {
        bufferingHandler.removeMessages(0)

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
            progressValue.value = sec
        }
    }

    override fun onBuffering(buffering : Boolean) {
        if (buffering) {
            //pause 후 1초 뒤 resume
            if (::playerController.isInitialized && playerController.isPlaying) {
                playerController.setPause()
            }
            bufferingHandler.removeMessages(0)
            uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
        } else {
            bufferingHandler.removeMessages(0)
            bufferingHandler.sendEmptyMessageDelayed(0, 1000)
        }
    }

    val bufferingHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_DISMISS)
            if (playerController != null) {
                playerController.setPlay()
            }
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