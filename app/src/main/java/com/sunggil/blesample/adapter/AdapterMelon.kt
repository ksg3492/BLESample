package com.sunggil.blesample.adapter

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

class AdapterMelon(val onClickListener: OnItemClickCallback) : RecyclerView.Adapter<AdapterMelon.ViewHolder>() {
    var listDatas : ArrayList<MelonItem>? = null

    init {
        listDatas = ArrayList()
    }

    fun updateDatas(data : ArrayList<MelonItem>?) {
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

    fun updateThumbs(position : Int, data : ByteArray) {
        if (listDatas!!.size > position) {
            listDatas?.get(position)?.albumImgByte = data

            notifyItemChanged(position)
        }
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
        holder.tvTitle.text = listDatas?.get(position)?.songName

        if (listDatas?.get(position)?.albumImgByte != null) {
            Glide.with(holder.itemView.context).asDrawable().placeholder(ColorDrawable(Color.GRAY)).error(ColorDrawable(Color.RED)).override(53).load(listDatas?.get(position)?.albumImgByte).into(holder.ivThumb)
        } else {
            Glide.with(holder.itemView.context).asDrawable().placeholder(ColorDrawable(Color.GRAY)).error(ColorDrawable(Color.RED)).override(53).load(ColorDrawable(Color.GRAY)).into(holder.ivThumb)
        }
    }

    inner class ViewHolder(v: View, var listener: OnItemClickCallback) : RecyclerView.ViewHolder(v), View.OnClickListener {
        var tvTitle : TextView
        var ivThumb : ImageView

        init {
            tvTitle = v.findViewById(R.id.tv_title)
            ivThumb = v.findViewById(R.id.iv_thumb)
            v.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (adapterPosition != -1) {
                listener?.onClick(adapterPosition)
            }
        }
    }
}