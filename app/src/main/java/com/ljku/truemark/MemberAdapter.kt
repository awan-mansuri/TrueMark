package com.ljku.truemark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ljku.truemark.database.UserEntity

class MemberAdapter(
    private var users: List<UserEntity>,
    private val initialSelectedIds: List<Int> = emptyList()
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private val selectedIds = mutableSetOf<Int>().apply { addAll(initialSelectedIds) }

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(android.R.id.text1)
        val roleText: TextView = view.findViewById(android.R.id.text2)
        val checkBox: CheckBox = CheckBox(view.context) // Using standard layouts for simplicity or custom
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        // Using simple_list_item_multiple_choice would require a CheckBox. Let's use a simple custom layout.
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val user = users[position]
        holder.nameText.text = user.name
        holder.roleText.text = "${user.role} - ${user.email}"
        
        // Use itemView as the toggle
        holder.itemView.setOnClickListener {
            if (selectedIds.contains(user.id)) {
                selectedIds.remove(user.id)
                holder.itemView.setBackgroundColor(0)
            } else {
                selectedIds.add(user.id)
                holder.itemView.setBackgroundColor(0x330000FF.toInt()) // Light blue tint
            }
        }
        
        if (selectedIds.contains(user.id)) {
            holder.itemView.setBackgroundColor(0x330000FF.toInt())
        } else {
            holder.itemView.setBackgroundColor(0)
        }
    }

    override fun getItemCount() = users.size

    fun getSelectedMemberIds(): String {
        return selectedIds.joinToString(",")
    }

    fun updateUsers(newUsers: List<UserEntity>) {
        users = newUsers
        notifyDataSetChanged()
    }
}