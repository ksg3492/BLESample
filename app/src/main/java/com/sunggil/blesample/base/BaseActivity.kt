package com.sunggil.blesample.base

import android.os.Bundle
import android.util.Log
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider

abstract class BaseActivity<T : ViewDataBinding, V : BaseViewModel> : AppCompatActivity() {
    private lateinit var viewDataBinding : T
    private lateinit var viewModel : V

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewDataBinding = DataBindingUtil.setContentView(this, getLayout())

        viewModel = ViewModelProvider(this).get(initViewModel()::class.java)

        bindingLiveData()
    }

    fun getDataBinding() : T {
        return viewDataBinding
    }

    fun getViewModel() : V {
        return viewModel
    }

    override fun onDestroy() {
        getViewModel().onDestroy()
        super.onDestroy()
    }

    @NonNull abstract fun getLayout() : Int
    @NonNull abstract fun initViewModel() : V
    abstract fun bindingLiveData()
}