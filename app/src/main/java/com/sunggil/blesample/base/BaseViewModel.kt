package com.sunggil.blesample.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel

abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {
    abstract fun onDestroy()

}