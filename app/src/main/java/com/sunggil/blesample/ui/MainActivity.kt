package com.sunggil.blesample.ui

import android.Manifest
import android.bluetooth.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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
import com.sunggil.blesample.databinding.ActivityMainBinding
import com.sunggil.blesample.player.service.*
import com.sunggil.blesample.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() , SurfaceHolder.Callback {
    lateinit var tv_status : TextView
    lateinit var tv_logcat : TextView
    lateinit var rv_melon : RecyclerView
    lateinit var rv_device : RecyclerView
    lateinit var surfaceHolder : SurfaceHolder

    lateinit var adapterMelon: AdapterMelon
    lateinit var adapterYoutube: AdapterYoutube
    lateinit var adapterBluetooth: AdapterBluetooth

    var mBound = false


    lateinit var playerServiceMediaPlayer : ExoPlayerServiceMediaPlayer

    override fun getLayout(): Int {
        return R.layout.activity_main
    }

    override fun initViewModel(): MainViewModel {
        return MainViewModel(application)
    }

    val serviceConnectionMediaPlayer = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!mBound) {
                val binder = service as ExoPlayerServiceMediaPlayer.LocalBinder
                playerServiceMediaPlayer = binder.service
                getViewModel().setPlayerService(playerServiceMediaPlayer)
                mBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        adapterMelon = AdapterMelon(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                if (!::playerServiceMediaPlayer.isInitialized) {
                    Toast.makeText(baseContext, "Player is not ready!", Toast.LENGTH_SHORT).show()
                    return
                }

                getViewModel().onMelonItemClick(adapterMelon.listDatas?.get(position))
            }
        })
        adapterYoutube = AdapterYoutube(object : OnItemClickCallback {
            override fun onClick(position: Int) {
                if (!::playerServiceMediaPlayer.isInitialized) {
                    Toast.makeText(baseContext, "Player is not ready!", Toast.LENGTH_SHORT).show()
                    return
                }

                getViewModel().onMelonItemClick(adapterMelon.listDatas?.get(position))
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
        rv_melon = findViewById<RecyclerView>(R.id.rv_melon)
        rv_melon.layoutManager = LinearLayoutManager(baseContext)
        rv_melon.adapter = adapterMelon

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
            rv_melon.visibility = visibility
        }

        bt_playpause.setOnClickListener {
            if (::playerServiceMediaPlayer.isInitialized && AppConst.COMMON.IS_CLIENT) {
                if (playerServiceMediaPlayer.isPlaying) {
                    playerServiceMediaPlayer.setPause()
                } else {
                    playerServiceMediaPlayer.setPlay()
                }
            }
        }

        bt_melon_chart.setOnClickListener {
            getViewModel().getMelonChart()
        }

        bt_next.setOnClickListener {
            if (getViewModel().getMelonPage(true)) {
                adapterMelon.updateDatas(ArrayList())
            }
        }
        bt_prev.setOnClickListener {
            if (getViewModel().getMelonPage(false)) {
                adapterMelon.updateDatas(ArrayList())
            }
        }

        val bt_scan = findViewById<Button>(R.id.bt_scan)
        bt_scan.setOnClickListener {
            tv_status.text = "WAITING..."

            if (AppConst.COMMON.IS_CLIENT) {
                bindPlayerServiceMediaPlayer()
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

        v_minbuffer.visibility = View.VISIBLE

        val constraintLayout = findViewById(R.id.layout_player) as ConstraintLayout
        val minView = findViewById(R.id.v_minbuffer) as View

        val cs = ConstraintSet()
        cs.clone(constraintLayout)
        cs.setHorizontalBias(minView.id, AppConst.COMMON.MIN_PRELOAD_BUFFER * 0.01f)
        cs.applyTo(constraintLayout);

        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
    }

    override fun bindingLiveData() {
        val toastMsgObserver = Observer<String> {
            Toast.makeText(baseContext, it, Toast.LENGTH_SHORT).show()
        }
        val logMsgObserver = Observer<String> { tv_logcat.text = it }
        val statusMsgObserver = Observer<String> {
            when(it) {
                "PIPE BROKEN...RETRY CONNECT SOCKET!!" -> tv_status.setTextColor(Color.RED)
                "DISCONNECTED...",
                "CONNECTED..." -> tv_status.setTextColor(Color.BLACK)

            }
            tv_status.text = it
        }
        val progressObserver = Observer<String> { tv_progress.text = it }
        val durationObserver = Observer<String> { tv_progress.text = it }
        val playpauseObserver = Observer<Int> { bt_playpause.background = getDrawable(it) }
        val pageObserver = Observer<String> { tv_page.text = it }
        val loadingObserver = Observer<Int> { layout_loading.visibility = it }
        val downloadObserver = Observer<Int> { pb_download.progress = it }
        val preloadObserver = Observer<Int> { pb_preload.progress = it }
        val melonListObserver = Observer<ArrayList<MelonItem>> { adapterMelon.updateDatas(it) }
        val thumbListObserver = Observer<HashMap<Int, ByteArray>> { adapterMelon.updateThumbs(it.keys.first(), it.values.first()) }


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
        getViewModel().melonListValue.observe(this, melonListObserver)
        getViewModel().thumbListValue.observe(this, thumbListObserver)
    }

    private fun init() {

    }

    fun bindPlayerServiceMediaPlayer() {
        if (!mBound) {
            val intent = Intent(applicationContext, ExoPlayerServiceMediaPlayer::class.java)
            bindService(intent, serviceConnectionMediaPlayer, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        surfaceHolder.removeCallback(this)

        try {
            if (mBound) {
                playerServiceMediaPlayer.release()
                unbindService(serviceConnectionMediaPlayer)
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

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Log.e("SG2","surfaceChanged")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.e("SG2","surfaceDestroyed")
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.e("SG2","surfaceCreated")
    }
}