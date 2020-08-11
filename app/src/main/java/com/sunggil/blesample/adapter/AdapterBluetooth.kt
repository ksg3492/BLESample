package com.sunggil.blesample.adapter

import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sunggil.blesample.R
import com.sunggil.blesample.data.MelonItem

class AdapterBluetooth : RecyclerView.Adapter<AdapterBluetooth.ViewHolder> {
    var onClickListener: OnItemClickCallback
    var listDatas : ArrayList<BluetoothDevice>? = null

    constructor(listener : OnItemClickCallback) {
        listDatas = ArrayList()
        onClickListener = listener
    }

    fun getListData(position : Int) : BluetoothDevice {
        return listDatas!!.get(position)
    }

    fun updateDatas(data : List<BluetoothDevice>) {
        if (listDatas == null) {
            listDatas = ArrayList()
        } else {
            listDatas!!.clear()
        }

        if (data == null) {
            listDatas!!.addAll(ArrayList())
        } else {
            listDatas!!.addAll(data)
        }

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)

        return ViewHolder(v, onClickListener)
    }

    override fun getItemCount(): Int {
        if (listDatas == null) {
            return 0
        }
        return listDatas!!.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvTitle.text = listDatas?.get(position)?.name
        holder.ivThumb.visibility = View.GONE
    }

    inner class ViewHolder : RecyclerView.ViewHolder, View.OnClickListener {
        var tvTitle : TextView
        var ivThumb : ImageView
        var listener : OnItemClickCallback

        constructor(v : View, listener : OnItemClickCallback) : super(v) {
            tvTitle = v.findViewById(R.id.tv_title)
            ivThumb = v.findViewById(R.id.iv_thumb)
            this.listener = listener;
            v.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (adapterPosition != -1) {
                listener?.onClick(adapterPosition)
            }
        }
    }
}