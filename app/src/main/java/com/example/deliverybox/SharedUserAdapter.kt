package com.example.deliverybox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class SharedUserAdapter(
    private val userList: List<Pair<String, String>>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<SharedUserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emailText: TextView = view.findViewById(R.id.tv_user_email)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete_user)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (uid, email) = userList[position]
        holder.emailText.text = email
        holder.deleteButton.setOnClickListener { onDeleteClick(uid) }
    }

    override fun getItemCount(): Int = userList.size
}
