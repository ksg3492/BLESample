package com.sunggil.blesample

import android.Manifest
import android.bluetooth.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.sunggil.blesample.adapter.AdapterBluetooth
import com.sunggil.blesample.adapter.AdapterMelon
import com.sunggil.blesample.adapter.OnItemClickCallback
import com.sunggil.blesample.data.MelonDomain
import com.sunggil.blesample.data.MelonItem
import com.sunggil.blesample.data.MelonStreamingItem
import com.sunggil.blesample.data.StreamingCallback
import com.sunggil.blesample.network.*
import com.sunggil.blesample.player.PlayerCallback
import com.sunggil.blesample.player.service.*
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() , PlayerCallback {
    var IS_CLIENT = true

    var isDestroy = false

    lateinit var tv_status : TextView
    lateinit var tv_logcat : TextView
    lateinit var rv_melon : RecyclerView
    lateinit var rv_device : RecyclerView

    var melonChartData = ArrayList<MelonItem>()
    lateinit var adapterMelon: AdapterMelon
    lateinit var adapterBluetooth: AdapterBluetooth
    var thumbsTransferThread : ThumbsTransferThread? = null

    lateinit var mServerSocket : BluetoothServerSocket
    var mSocket : BluetoothSocket? = null

    var logcat = 0L
    var prepareLogcat = 0L
    var startPage = 0
    var endPage = 0

    //thread
    var mDataTransferThread : DataTransferThread? = null
    var mConnectionThread : ConnectionThread? = null

    lateinit var vmMainViewModel : MainViewModel

    var mBound = false
    var fileDownloadStop = false
    var isPrepare = false
    var prevPercent = 0
    var wrote: Long = 0


    lateinit var playerService : ExoPlayerService
    lateinit var playerServiceAudioTrack : ExoPlayerServiceAudioTrack
    lateinit var playerServiceMediaPlayer : ExoPlayerServiceMediaPlayer
    var mDownloadOutputStream : FileOutputStream? = null


    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!mBound) {
                val binder = service as ExoPlayerService.LocalBinder
                playerService = binder.service
                playerService.addPlayerCallback(this@MainActivity)
                mBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    val serviceConnectionAudioTrack = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!mBound) {
                val binder = service as ExoPlayerServiceAudioTrack.LocalBinder
                playerServiceAudioTrack = binder.service
                playerServiceAudioTrack.addPlayerCallback(this@MainActivity)
                mBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }
    val serviceConnectionMediaPlayer = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!mBound) {
                val binder = service as ExoPlayerServiceMediaPlayer.LocalBinder
                playerServiceMediaPlayer = binder.service
                playerServiceMediaPlayer.addPlayerCallback(this@MainActivity)
                mBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TedPermission.with(this)
                    .setPermissionListener(permissionListener)
                    .setDeniedMessage("해당 앱을 이용하시려면 권한이 필요합니다.\\n[설정] > [권한]에서 설정하세요.")
                    .setPermissions(
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    .check()
        }

        vmMainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        adapterMelon = AdapterMelon(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                if (!::playerServiceMediaPlayer.isInitialized) {
                    Toast.makeText(baseContext, "Player is not ready!", Toast.LENGTH_SHORT).show()
                    return
                }

                uiHandler.sendEmptyMessage(AppConst.COMMON.LOADING_DIALOG_SHOW)
                prepareLogcat = System.currentTimeMillis()

                playerServiceMediaPlayer.setPause()
                playerServiceMediaPlayer.setStop()

                try {
                    fileDownloadStop = true
                    mDownloadOutputStream?.close()
                    mDownloadOutputStream = null
                } catch (e: Exception) {

                }

                Thread{
                    val melonItem = adapterMelon.listDatas?.get(position)
                    val itemData = Gson().toJson(melonItem)

                    val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_STREAMING)
                    ble.content = itemData.toByteArray()

                    mDataTransferThread?.write(ble)
                }.start()
            }
        })
        adapterBluetooth = AdapterBluetooth(object : OnItemClickCallback {
            override fun onClick(position: Int) {

                makeThread()
                mConnectionThread?.setDevice(adapterBluetooth.getListData(position))
                mConnectionThread?.start()

                rv_device.visibility = View.GONE
            }
        })

        tv_status = findViewById<TextView>(R.id.tv_status)
        tv_logcat = findViewById<TextView>(R.id.tv_logcat)
        rv_melon = findViewById<RecyclerView>(R.id.rv_melon)
        rv_melon.layoutManager = LinearLayoutManager(baseContext)
        rv_melon.adapter = adapterMelon

        rv_device = findViewById<RecyclerView>(R.id.rv_device)
        rv_device.layoutManager = LinearLayoutManager(baseContext)
        rv_device.adapter = adapterBluetooth

        val tv_client_server = findViewById<TextView>(R.id.tv_client_server)

        val sw_client_server = findViewById<Switch>(R.id.sw_client_server)
        sw_client_server.isChecked = IS_CLIENT
        sw_client_server.setOnCheckedChangeListener { bt: CompoundButton, b: Boolean ->
            IS_CLIENT = b

            var visibility = View.VISIBLE
            if (IS_CLIENT) {
                tv_client_server.text = "CLIENT"
            } else {
                tv_client_server.text = "SERVER"
                visibility = View.GONE
            }

            layout_loading.visibility = visibility
            bt_chart.visibility = visibility
            rv_melon.visibility = visibility
        }

        bt_playpause.setOnClickListener {
//            if (playerService.isPlaying) {
//                playerService.setPause()
//            } else {
//                playerService.setPlay()
//            }
            if (IS_CLIENT) {
                if (playerServiceMediaPlayer.isPlaying) {
                    playerServiceMediaPlayer.setPause()
                } else {
                    playerServiceMediaPlayer.setPlay()
                }
            }else {
                if (playerServiceMediaPlayer.isPlaying) {
                    playerServiceMediaPlayer.setPause()
                } else {
                    playerServiceMediaPlayer.setPlay()
                }
            }

        }

        bt_chart.setOnClickListener {
            Log.e("SG2","CLIENT REQUEST CHART : INIT")
            logcat = System.currentTimeMillis()

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST)
            ble.arg1 = 0

            mDataTransferThread?.write(ble)
        }

        bt_next.setOnClickListener {
            Log.e("SG2","CLIENT REQUEST CHART : NEXT")

            if (startPage + 1 > endPage) {
                Toast.makeText(baseContext, "마지막 페이지", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            adapterMelon.updateDatas(ArrayList<MelonItem>())
            logcat = System.currentTimeMillis()

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST)
            ble.arg1 = startPage + 1
            mDataTransferThread?.write(ble)
        }
        bt_prev.setOnClickListener {
            Log.e("SG2","CLIENT REQUEST CHART : PREV")

            if (startPage - 1 < 0) {
                Toast.makeText(baseContext, "첫 페이지", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            adapterMelon.updateDatas(ArrayList<MelonItem>())
            logcat = System.currentTimeMillis()

            val ble = BLEProtocol(AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST)
            ble.arg1 = startPage - 1
            mDataTransferThread?.write(ble)
        }

        val bt_scan = findViewById<Button>(R.id.bt_scan)
        bt_scan.setOnClickListener {
            tv_status.text = "WAITING..."

            if (IS_CLIENT) {
//                bindPlayerServiceClient()
                bindPlayerServiceMediaPlayer()
//                bindPlayerService()
            } else {
//                bindPlayerServiceAudioTrack()
                bindPlayerService()
//                bindPlayerServiceServer()
            }


            val mBluetoothAdapter : BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (mBluetoothAdapter.isEnabled) {
                val bondedDevices = mBluetoothAdapter.bondedDevices

                if (bondedDevices.size > 0) {
                    val devices = bondedDevices.toList()
                    adapterBluetooth.updateDatas(devices)
                }
            }


            rv_device.visibility = View.VISIBLE
        }
    }

    fun makeThread() {
        mDataTransferThread = DataTransferThread()
        mConnectionThread = ConnectionThread()
    }

    val exoPlayerHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            val filePath : String = msg.obj as String
            val pathes = filePath.split("/")
            var fileName = ""
            if (pathes.size > 0) {
                fileName = pathes[pathes.size - 1]
            }

            Log.e("SG2","exoPlayerHandler filePath : $filePath")
            Log.e("SG2","exoPlayerHandler fileName : $fileName")

            if (::playerService.isInitialized) {
                playerService.prepare(true, fileName, filePath)
            }
        }
    }

    val exoPlayerHandlerAudioTrack = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            val fileLength = msg.arg2
            val filePath : String = msg.obj as String
            val pathes = filePath.split("/")
            var fileName = ""
            if (pathes.size > 0) {
                fileName = pathes[pathes.size - 1]
            }

            Log.e("SG2","exoPlayerHandler filePath : $filePath")
            Log.e("SG2","exoPlayerHandler fileName : $fileName")

            playerServiceAudioTrack.prepare(true, fileName, filePath)
        }
    }

    val exoPlayerHandlerMediaPlayer = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            if (msg.what == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START) {
                playerServiceMediaPlayer.setStop()
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
                    playerServiceMediaPlayer.addByteData()
                } else {
                    isPrepare = true
                    playerServiceMediaPlayer.prepare(true, "$fileLength", filePath)
                }

            }

        }
    }


    val statusHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            if (mDataTransferThread != null && mDataTransferThread!!.isActive()) {
                tv_status.text = "CONNECTED..."
            } else {
                tv_status.text = "DISCONNECTED..."
            }

            sendEmptyMessageDelayed(0, 1000)
        }
    }

    private fun init() {

    }

    fun bindPlayerService() {
        if (!mBound) {
            val intent = Intent(applicationContext, ExoPlayerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    fun bindPlayerServiceAudioTrack() {
        if (!mBound) {
            val intent = Intent(applicationContext, ExoPlayerServiceAudioTrack::class.java)
            bindService(intent, serviceConnectionAudioTrack, Context.BIND_AUTO_CREATE)
        }
    }
    fun bindPlayerServiceMediaPlayer() {
        if (!mBound) {
            val intent = Intent(applicationContext, ExoPlayerServiceMediaPlayer::class.java)
            bindService(intent, serviceConnectionMediaPlayer, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroy = true

        try {
            if (mBound) {
                playerService.release()
                unbindService(serviceConnection)
                mBound = false
            }
        }catch (e: Exception) {}
        try {
            playerServiceAudioTrack.release()
            unbindService(serviceConnectionAudioTrack)
            mBound = false
        }catch (e: Exception) {}

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

            if (IS_CLIENT) {
                mSocket = mDevice?.createInsecureRfcommSocketToServiceRecord(uuid)

                mBluetoothAdapter.cancelDiscovery()

                try {
                    mSocket?.connect()
                    Log.e("SG2", "Bluetooth socket connect success")
                }catch (e : Exception) {
                    Log.e("SG2", "Bluetooth socket connect fail : " , e)
                    return
                }

                sleep(1000)
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
    }

    inner class DataTransferThread : Thread {
        var mOutputStream : DataOutputStream? = null
        var mInStream : DataInputStream? = null

        constructor() {
            priority = MAX_PRIORITY
        }

        fun isActive() : Boolean {
            return mOutputStream != null  && mInStream != null
        }

        fun setSocket(socket : BluetoothSocket?) {
            mOutputStream = DataOutputStream(socket!!.outputStream)
            mInStream = DataInputStream(socket!!.inputStream)
        }

        override fun run() {
            super.run()

            var isLoopStop = false

            while (!isLoopStop && !isDestroy && mInStream != null) {

//                synchronized(mInStream!!) {


                    try {
                        //byte array to BLE Protocol
                    var length = mInStream!!.readInt()
                    Log.e("SG2","DataInputStream START! : $length")

                    if (length <= 0) {
                        sleep(10)
                        continue
                    }

                    var buffer = ByteArray(length)

                    mInStream!!.readFully(buffer)
                    Log.e("SG2","DataInputStream END!" )

                    val result = buffer

                    val readMessage = String(result, 0, result.size);
                    val bleProtocol = Gson().fromJson<BLEProtocol>(readMessage, object: TypeToken<BLEProtocol>() {}.type)

                        val command = bleProtocol.command

                        if (command == AppConst.CLIENT_TO_SERVER.GET_MELON_CHART_LIST) {
                            val ITEMS_SIZE = 10
                            val startPage = bleProtocol.arg1 //page  0,1,2,3...
                            var startIndex = ITEMS_SIZE * startPage

                            //image 가공 stop
                            thumbsTransferThread?.stopGlide()
                            thumbsTransferThread = null

                            if (melonChartData.size == 0) {
                                val call = RetrofitService.getInstance.getMelonChartList()
                                call.enqueue(object : Callback<MelonDomain> {
                                    override fun onResponse(
                                        call: Call<MelonDomain>,
                                        response: Response<MelonDomain>
                                    ) {
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

                                            var ble =
                                                BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST)
                                            ble.arg1 = startPage
                                            if (origin.size / ITEMS_SIZE > 0 && origin.size % ITEMS_SIZE == 0) {
                                                ble.arg2 = (origin.size / ITEMS_SIZE) - 1
                                            } else {
                                                ble.arg2 = (origin.size / ITEMS_SIZE)
                                            }
                                            ble.content = byteData.toByteArray()
                                            write(ble)

                                            //image 가공
                                            thumbsTransferThread = ThumbsTransferThread(
                                                baseContext,
                                                items,
                                                object : ThumbsTransferThread.ThumbsListener {
                                                    override fun onResult(
                                                        position: Int,
                                                        data: ByteArray
                                                    ) {
                                                        val ble =
                                                            BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB)
                                                        ble.arg1 = startPage
                                                        ble.arg2 = position  //position
                                                        ble.content = data
                                                        write(ble)
                                                    }
                                                })
                                            thumbsTransferThread?.start()
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

                                var ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST)
                                ble.arg1 = startPage
                                if (melonChartData.size / ITEMS_SIZE > 0 && melonChartData.size % ITEMS_SIZE == 0) {
                                    ble.arg2 = (melonChartData.size / ITEMS_SIZE) - 1
                                } else {
                                    ble.arg2 = (melonChartData.size / ITEMS_SIZE)
                                }
                                ble.content = byteData.toByteArray()
                                write(ble)

                                //logcat
                                val stamp = System.currentTimeMillis() - logcat

                                Handler(Looper.getMainLooper()).post {
                                    tv_logcat.text = getTimeStamp(stamp)
                                }

                                //image 가공
                                thumbsTransferThread = ThumbsTransferThread(
                                    baseContext,
                                    items,
                                    object : ThumbsTransferThread.ThumbsListener {
                                        override fun onResult(position: Int, data: ByteArray) {
                                            val ble =
                                                BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB)
                                            ble.arg1 = startPage
                                            ble.arg2 = position  //maxPage 대신 position
                                            ble.content = data
                                            write(ble)
                                        }
                                    })
                                thumbsTransferThread?.start()
                            }
                        } else if (command == AppConst.CLIENT_TO_SERVER.GET_MELON_STREAMING) {
                            var byteLength = bleProtocol.contentLength

                            if (byteLength > 0) {
                                val readMessage = String(bleProtocol.content!!, 0, byteLength)
                                val melonItem = Gson().fromJson<MelonItem>(
                                    readMessage,
                                    object : TypeToken<MelonItem>() {}.type
                                )

                                if (::vmMainViewModel.isInitialized) {

                                    vmMainViewModel.stopDownload()

                                    prepareLogcat = System.currentTimeMillis()
                                    vmMainViewModel.startStreaming(melonItem, object : StreamingCallback {
                                        override fun onSuccess(item: MelonStreamingItem) {
                                            val name = "${item.ALBUMID}_${item.PERIOD}_${item.BITRATE}"

                                            val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_START)
                                            ble.message = name
                                            write(ble)

                                            Log.e("SG2", "BLE_CTS_GET_MELON_STREAMING : ${item.PATH}")

                                            vmMainViewModel.downloadStreamingFile(name, item.PATH, object : DownloadCallback {
                                                override fun onResult(success: Boolean, fileName: String, size: Long) {
                                                    if (!success) {
                                                        return
                                                    }
                                                    val filePath = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName

                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DONE)
                                                    ble.arg2 = size.toInt()
                                                    ble.message = fileName
                                                    write(ble)
                                                }

                                                override fun onUpdate(data: ByteArray, read: Int, length: Long) {
                                                    if (prepareLogcat != -1L) {
                                                        val stamp = System.currentTimeMillis() - prepareLogcat
                                                        val time = getTimeStamp(stamp)
                                                        Log.e("SG2", "downloadStreamingFile logcat : ${time}")
                                                        prepareLogcat = -1L
                                                        Handler(Looper.getMainLooper()).post {
                                                            tv_logcat.text = time
                                                        }
                                                    }
                                                    Log.e("SG2","${Util.isMainLooper()} ] MELON_STREAMING_FILE_DOWNLOADING  : ${read} sent"
                                                    )
                                                    val ble = BLEProtocol(AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING)
                                                    ble.message = name
                                                    ble.arg2 = length.toInt()
                                                    ble.content = data
                                                    ble.contentLength = read    //별도로 입력
                                                    write(ble)
                                                }
                                            })

                                        }

                                        override fun onFail(error: String) {
                                            Handler(Looper.getMainLooper()).post {
                                                Toast.makeText(baseContext, "$error", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    })
                                } else { }
                            } else {

                            }
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
//                                mDownloadOutputStream = RandomAccessFile(mp3File.absoluteFile, "rw")
                                mDownloadOutputStream = FileOutputStream(mp3File)
                            } catch (e: Exception) {
                                Log.e("SG2", "RandomAccessFile error : ", e)
                            }

//                        prepareLogcat = System.currentTimeMillis()

                            isPrepare = false
                            prevPercent = 0
                            fileDownloadStop = false
                            wrote = 0
                        } else if (command == AppConst.SERVER_TO_CLIENT.MELON_STREAMING_FILE_DOWNLOADING) {
                            val read = bleProtocol.contentLength
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
                                    percent5 = percent / 20

                                        Log.e("SG2", "${Util.isMainLooper()} ] ${wrote}byte 다운로드 완료 / total bytes : $fileLength")
                                    if (percent != 100 && prevPercent != percent5) {
                                        prevPercent = percent5

                                        val filePath = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName

                                        val dataValue = exoPlayerHandlerMediaPlayer.obtainMessage(command, wrote.toInt(), fileLength, filePath)
                                        exoPlayerHandlerMediaPlayer.sendMessage(dataValue)
                                    }

                                } else { }


//
//                            if (isPrepare) {
//                                if (percent != 100 && percent5 > prevPercent) {
//                                    Log.e("SG2","${Util.isMainLooper()} ] ${percent}% 다운로드 완료 / total bytes : $fileLength")
//                                    prevPercent = percent5
//                                    playerServiceMediaPlayer.addByteData()
//                                }
//                            } else {
//                                if (percent5 >= 1) {    //10%
//                                    prevPercent = percent5
//                                    Log.e("SG2","${Util.isMainLooper()} ] ${percent}% 다운로드 완료 / total bytes : $fileLength")
//                                    isPrepare = true
//                                    playerServiceMediaPlayer.prepare(true, "$fileLength", AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName)
//                                }
//                            }

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

//                        playerServiceMediaPlayer.addByteData()


                            val filePath = AppConst.RETROFIT.DOWNLOAD_MP3_FOLDER_PATH + fileName
                            val dataValue = exoPlayerHandlerMediaPlayer.obtainMessage(command, 0, fileLength, filePath)
                            exoPlayerHandlerMediaPlayer.removeCallbacks(null)
                            exoPlayerHandlerMediaPlayer.sendMessage(dataValue)

                        } else if (command == AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST) {
                            var startPage = bleProtocol.arg1  //startPage
                            var endPage = bleProtocol.arg2 //endPage
                            val byteLength = bleProtocol.contentLength

                            if (byteLength > 0) {
                                val logcat = System.currentTimeMillis()
                                val stamp = logcat - this@MainActivity.logcat
                                val time = getTimeStamp(stamp)
                                Handler(Looper.getMainLooper()).post {
                                    tv_logcat.text = "$time , $byteLength bytes"
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
                                if (startPage == this@MainActivity.startPage) {
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
                        if (!IS_CLIENT) {
                            Log.e("SG2", "서버 ack 대기")
                            uiHandler.removeMessages(AppConst.COMMON.RESTART_CONNECTION_SERVER)
                            uiHandler.sendEmptyMessage(AppConst.COMMON.RESTART_CONNECTION_SERVER)
                        } else {}
//                        break
                        isLoopStop = true
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
//            synchronized(mOutputStream!!) {
                try {
                    val byteData = Gson().toJson(ble)

                    mOutputStream?.writeInt(byteData.toByteArray().size)
                    mOutputStream?.write(byteData.toByteArray())

                    Log.e("SG2","SENT BLEProtocol")
                    mOutputStream?.flush()
                } catch (e : IOException) {
                    Log.e("SG2","write error : ", e)
                }
//            }
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

    fun run(socket : BluetoothSocket?) {
        mDataTransferThread!!.setSocket(socket)
        mDataTransferThread!!.start()
    }

    val uiHandler = object:Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            try {
                when (msg.what) {
                    AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST -> {
                        if (msg!= null) {
                            var obj = msg.obj as ArrayList<MelonItem>

                            adapterMelon.updateDatas(obj)
                        }
                    }
                    AppConst.SERVER_TO_CLIENT.PAGE_INDEX -> {
                        if (msg!= null) {
                            startPage = msg.arg1
                            endPage = msg.arg2

                            tv_page.text = "${startPage + 1} / ${endPage + 1}"
                        }
                    }
                    AppConst.SERVER_TO_CLIENT.MELON_CHART_LIST_THUMB -> {
                        if (msg!= null) {
                            startPage = msg.arg1
                            var position = msg.arg2
                            var obj = msg.obj as ByteArray

                            adapterMelon.updateThumbs(position, obj)
                        }
                    }
                    AppConst.COMMON.RESTART_CONNECTION_SERVER -> {
                        makeThread()
                        mConnectionThread?.start()
                    }
                    AppConst.COMMON.LOADING_DIALOG_SHOW -> {
                        layout_loading.visibility = View.VISIBLE
                    }
                    AppConst.COMMON.LOADING_DIALOG_DISMISS -> {
                        layout_loading.visibility = View.GONE
                    }
                }
            } catch (e : Exception) {
                Log.e("SG2","uiHandler : ", e)
            }
        }
    }

    private val permissionListener : PermissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            init()
        }

        override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
            Toast.makeText(applicationContext, "권한 설정 실패", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    fun getTimeStamp(time : Long) :String {
        val dateFormat = SimpleDateFormat("mm:ss.SSS")
        return dateFormat.format(time)
    }

    override fun onPlayed() {

    }

    override fun onPrepared(duration: Int) {
        Handler(Looper.getMainLooper()).post {
            tv_duration.text = Util.convertMMSS(duration)
            val stamp = System.currentTimeMillis() - prepareLogcat
            tv_logcat.text = getTimeStamp(stamp)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            playerServiceMediaPlayer.setPlay()
        }, 500)


        onBuffering(false)
    }

    override fun onCompletion(duration: Int) {
    }

    override fun onProgress(sec: Int) {
        Handler(Looper.getMainLooper()).post {
            tv_progress.text = Util.convertMMSS(sec)
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

    override fun onPaused() {
    }


    override fun onPreload(percent: Int) {
        Handler(Looper.getMainLooper()).post {
            pb_download.secondaryProgress = percent
        }
    }
}