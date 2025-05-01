package com.example.deliverybox.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.databinding.ItemBoxBinding
import com.example.deliverybox.model.BoxInfo

class BoxListAdapter(private val boxList: List<BoxInfo>) : RecyclerView.Adapter<BoxListAdapter.BoxViewHolder>() {

    inner class BoxViewHolder(val binding: ItemBoxBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoxViewHolder {
        val binding = ItemBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BoxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BoxViewHolder, position: Int) {
        val box = boxList[position]
        holder.binding.tvBoxAlias.text = box.alias
        holder.binding.tvBoxName.text = box.boxName  // ✅ 수정 완료: 대소문자 정확히 맞춤
    }

    override fun getItemCount(): Int = boxList.size
}
