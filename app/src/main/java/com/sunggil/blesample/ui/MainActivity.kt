package com.sunggil.blesample.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.*
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.sunggil.blesample.*
import com.sunggil.blesample.adapter.AdapterBluetooth
import com.sunggil.blesample.adapter.AdapterMelon
import com.sunggil.blesample.adapter.AdapterYoutube
import com.sunggil.blesample.adapter.OnItemClickCallback
import com.sunggil.blesample.base.BaseActivity
import com.sunggil.blesample.data.MelonItem
import com.sunggil.blesample.data.YoutubeItem
import com.sunggil.blesample.databinding.ActivityMainBinding
import com.sunggil.blesample.player.service.*
import com.sunggil.blesample.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {
    lateinit var tv_status : TextView
    lateinit var tv_logcat : TextView
    lateinit var rv_list : RecyclerView
    lateinit var rv_device : RecyclerView
    lateinit var surfaceHolder : SurfaceHolder

    lateinit var adapterMelon: AdapterMelon
    lateinit var adapterYoutube: AdapterYoutube
    lateinit var adapterBluetooth: AdapterBluetooth

    var mBound = false


//    lateinit var playerServicePlayer : ExoPlayerServiceMediaPlayer
    lateinit var playerServicePlayer : ExoPlayerService

    override fun getLayout(): Int {
        return R.layout.activity_main
    }

    override fun initViewModel(): MainViewModel {
        return MainViewModel(application)
    }

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!mBound) {
                val binder = service as ExoPlayerService.LocalBinder
                playerServicePlayer = binder.service
                getViewModel().setPlayerService(playerServicePlayer)
//                playerServicePlayer.setSurfaceHolder(surfaceHolder)
                mBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getAction()
            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                val device = intent?.getStringExtra(BluetoothDevice.EXTRA_DEVICE)
                val name = intent?.getStringExtra(BluetoothDevice.EXTRA_NAME)
                val rssi = intent?.getShortExtra(BluetoothDevice.EXTRA_RSSI ,Short.MIN_VALUE)
                Log.e("SG2","BluetoothDevice.ACTION_FOUND DEVICE : $device , NAME : $name , RSSI : ${rssi} dBm")
            }
        }

    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TedPermission.with(this)
                    .setPermissionListener(permissionListener)
                    .setDeniedMessage("해당 앱을 이용하시려면 권한이 필요합니다.\n[설정] > [권한]에서 설정하세요.")
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

        adapterMelon = AdapterMelon(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                if (!::playerServicePlayer.isInitialized) {
                    Toast.makeText(baseContext, "Player is not ready!", Toast.LENGTH_SHORT).show()
                    return
                }

                getViewModel().onMelonItemClick(adapterMelon.listDatas?.get(position))
            }
        })
        adapterYoutube = AdapterYoutube(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                if (!::playerServicePlayer.isInitialized) {
                    Toast.makeText(baseContext, "Player is not ready!", Toast.LENGTH_SHORT).show()
                    return
                }

                getViewModel().onYoutubeItemClick(adapterYoutube.listDatas?.get(position))
            }
        })
        adapterBluetooth = AdapterBluetooth(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                getViewModel().onBluetoothItemClick(adapterBluetooth.getListData(position))

                rv_device.visibility = View.GONE
            }
        })

        tv_status = findViewById<TextView>(R.id.tv_status)
        tv_logcat = findViewById<TextView>(R.id.tv_logcat)
        rv_list = findViewById<RecyclerView>(R.id.rv_list)
        rv_list.layoutManager = LinearLayoutManager(baseContext)

        rv_device = findViewById<RecyclerView>(R.id.rv_device)
        rv_device.layoutManager = LinearLayoutManager(baseContext)
        rv_device.adapter = adapterBluetooth

        val tv_client_server = findViewById<TextView>(R.id.tv_client_server)
        val sw_client_server = findViewById<Switch>(R.id.sw_client_server)

        sw_client_server.isChecked = AppConst.COMMON.IS_CLIENT
        sw_client_server.setOnCheckedChangeListener { bt: CompoundButton, b: Boolean ->
            AppConst.COMMON.IS_CLIENT = b

            var visibility = View.VISIBLE
            if (b) {
                tv_client_server.text = "CLIENT"
            } else {
                tv_client_server.text = "SERVER"
                visibility = View.GONE
            }

            layout_list.visibility = visibility
            bt_melon_chart.visibility = visibility
            bt_youtube_chart.visibility = visibility
            rv_list.visibility = visibility
        }

        bt_playpause.setOnClickListener {
            if (::playerServicePlayer.isInitialized && AppConst.COMMON.IS_CLIENT) {
                if (playerServicePlayer.isPlaying) {
                    playerServicePlayer.setPause()
                } else {
                    playerServicePlayer.setPlay()
                }
            }
        }

        bt_melon_chart.setOnClickListener {
            getViewModel().getMelonChart()
        }

        bt_youtube_chart.setOnClickListener {
            getViewModel().getYoutubeChart()
        }

        bt_next.setOnClickListener {
            if (rv_list.adapter == adapterMelon) {
                if (getViewModel().getMelonPage(true)) {
                    adapterMelon.updateDatas(ArrayList())
                }
            } else if (rv_list.adapter == adapterYoutube) {
                if (getViewModel().getYoutubePage(true)) {
                    adapterYoutube.updateDatas(ArrayList())
                }
            }
        }
        bt_prev.setOnClickListener {
            if (rv_list.adapter == adapterMelon) {
                if (getViewModel().getMelonPage(false)) {
                    adapterMelon.updateDatas(ArrayList())
                }
            } else if (rv_list.adapter == adapterYoutube) {
                if (getViewModel().getYoutubePage(false)) {
                    adapterYoutube.updateDatas(ArrayList())
                }
            }
        }

        bt_scan.setOnClickListener {
            tv_status.text = "WAITING..."

            if (AppConst.COMMON.IS_CLIENT) {
                bindPlayerService()
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

        bt_video_close.setOnClickListener({
            layout_surface.visibility = View.GONE
//            playerServiceMediaPlayer.setPause()
            playerServicePlayer.setStop()
            playerServicePlayer.release()
            pb_progress.progress = 0
            pb_progress.max = 100
            tv_progress.text = Util.convertMMSS(0)
            tv_duration.text = Util.convertMMSS(0)

            getViewModel().stopDownload()
            getViewModel().stopUpload()
        })

        tv_status.setOnClickListener({
            getViewModel().retryConnect()
        })
        tv_downsize.text = "${AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE / 1024} kByte"
        bt_up.setOnClickListener {
            AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE += (50 * 1024)
            tv_downsize.text = "${AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE / 1024} kByte"
            Toast.makeText(baseContext, "${tv_downsize.text}!", Toast.LENGTH_LONG).show()
        }
        bt_down.setOnClickListener {
            AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE -= (50 * 1024)
            tv_downsize.text = "${AppConst.COMMON.MIN_PRELOAD_BUFFER_SIZE / 1024} kByte"
            Toast.makeText(baseContext, "${tv_downsize.text}!", Toast.LENGTH_LONG).show()
        }


        v_minbuffer.visibility = View.VISIBLE

//        val constraintLayout = findViewById(R.id.layout_player) as ConstraintLayout
//        val minView = findViewById(R.id.v_minbuffer) as View
//
//        val cs = ConstraintSet()
//        cs.clone(constraintLayout)
//        cs.setHorizontalBias(minView.id, AppConst.COMMON.MIN_PRELOAD_BUFFER_PERCENT * 0.01f)
//        cs.applyTo(constraintLayout);

        surfaceHolder = surfaceView.holder

        if (AppConst.COMMON.IS_CLIENT) {
            registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        }
    }

    override fun bindingLiveData() {
        val toastMsgObserver = Observer<String> {
            Toast.makeText(baseContext, it, Toast.LENGTH_SHORT).show()
        }
        val logMsgObserver = Observer<String> { tv_logcat.text = it }
        val statusMsgObserver = Observer<String> {
            when(it) {
                "PIPE BROKEN...RETRY CONNECT SOCKET!!" -> {
                    tv_status.setTextColor(Color.RED)
                    tv_status.isClickable = true
                }
                "DISCONNECTED...",
                "CONNECTED..." -> {
                    tv_status.setTextColor(Color.BLACK)
                    tv_status.isClickable = false
                }

            }
            tv_status.text = it
        }
        val progressObserver = Observer<Int> {
            pb_progress.progress = it
            tv_progress.text = Util.convertMMSS(it)
        }
        val durationObserver = Observer<Int> {
            pb_progress.max = it
            tv_duration.text = Util.convertMMSS(it)
        }
        val playpauseObserver = Observer<Int> { bt_playpause.background = getDrawable(it) }
        val pageObserver = Observer<String> { tv_page.text = it }
        val loadingObserver = Observer<Int> { layout_loading.visibility = it }
        val downloadObserver = Observer<Int> { pb_download.progress = it }
        val preloadObserver = Observer<Int> { pb_preload.progress = it }
        val downloadedSizeObserver = Observer<Int> { tv_downloaded.text = "${it} Bytes" }
        val downloadedSpeedObserver = Observer<Float> { tv_downloaded_speed.text = "${it * 1.00f} kBytes/s" }
        val melonListObserver = Observer<ArrayList<MelonItem>> {
            rv_list.adapter = adapterMelon
            adapterMelon.updateDatas(it)
        }
        val melonThumbsObserver = Observer<HashMap<Int, ByteArray>> { adapterMelon.updateThumbs(it.keys.first(), it.values.first()) }
        val youtubeListObserver = Observer<ArrayList<YoutubeItem>> {
            rv_list.adapter = adapterYoutube
            adapterYoutube.updateDatas(it)
        }
        val youtubeThumbsObserver = Observer<HashMap<Int, ByteArray>> { adapterYoutube.updateThumbs(it.keys.first(), it.values.first()) }
        val surfaceVisibilityObserver = Observer<Int> {
            layout_loading.visibility = View.GONE
            layout_surface.visibility = it
        }


        getViewModel().toastMsg.observe(this, toastMsgObserver)
        getViewModel().logMsg.observe(this, logMsgObserver)
        getViewModel().statusMsg.observe(this, statusMsgObserver)
        getViewModel().progressValue.observe(this, progressObserver)
        getViewModel().durationValue.observe(this, durationObserver)
        getViewModel().playpauseValue.observe(this, playpauseObserver)
        getViewModel().pageValue.observe(this, pageObserver)
        getViewModel().loadingValue.observe(this, loadingObserver)
        getViewModel().downloadValue.observe(this, downloadObserver)
        getViewModel().preloadValue.observe(this, preloadObserver)
        getViewModel().downloadedSizeValue.observe(this, downloadedSizeObserver)
        getViewModel().downloadedSpeedValue.observe(this, downloadedSpeedObserver)
        getViewModel().melonListValue.observe(this, melonListObserver)
        getViewModel().melonThumbsValue.observe(this, melonThumbsObserver)
        getViewModel().youtubeListValue.observe(this, youtubeListObserver)
        getViewModel().youtubeThumbsValue.observe(this, youtubeThumbsObserver)
        getViewModel().surfaceVisibility.observe(this, surfaceVisibilityObserver)
    }

    private fun init() {

    }

    fun bindPlayerService() {
        if (!mBound) {
            val intent = Intent(applicationContext, ExoPlayerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            if (mBound) {
                playerServicePlayer.release()
                unbindService(serviceConnection)
                mBound = false
            }
        }catch (e: Exception) {}
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

    override fun onBackPressed() {
        if (layout_loading.visibility == View.VISIBLE) {
            layout_loading.visibility = View.GONE
        } else {
            super.onBackPressed()
            finish()
        }
    }
}