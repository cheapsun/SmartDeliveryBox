package com.example.deliverybox.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.model.Box
import com.example.deliverybox.ui.box.BoxDetailActivity

class BoxAdapter(private val boxList: List<Box>) : RecyclerView.Adapter<BoxAdapter.BoxViewHolder>() {

    class BoxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBoxName: TextView = itemView.findViewById(R.id.tv_box_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoxViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_box, parent, false)
        return BoxViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoxViewHolder, position: Int) {
        val box = boxList[position]
        holder.tvBoxName.text = box.boxName

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, BoxDetailActivity::class.java)
            intent.putExtra("boxName", box.boxName)
            intent.putExtra("boxId", box.boxId)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = boxList.size
}
