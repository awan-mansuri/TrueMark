package com.ljku.truemark

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ljku.truemark.database.UserEntity

class UserAdapter(
    private var users: List<UserEntity>,
    private val onEditClick: (UserEntity) -> Unit = {},
    private val onDeleteClick: (UserEntity) -> Unit = {},
    private val onStudentDetailClick: (UserEntity) -> Unit = {},
    private val onUserClick: (UserEntity) -> Unit = {},
    private val showActions: Boolean = false,
    private val rollNumbers: Map<Int, String> = emptyMap(),
    private val disableRowClick: Boolean = false,
    private val creatorNames: Map<Int, String> = emptyMap(),
    private val currentAdminId: Int? = null,
    private val showAddedBy: Boolean = false,
    private val showOnlyEdit: Boolean = false,
    private val groupAdminIds: List<Int> = emptyList()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView = view.findViewById(R.id.userProfileImageItem)
        val nameText: TextView = view.findViewById(R.id.userNameItem)
        val emailText: TextView = view.findViewById(R.id.userEmailItem)
        val editCard: CardView = view.findViewById(R.id.editButtonCard)
        val studentDetailCard: CardView = view.findViewById(R.id.infoButtonCard)
        val deleteCard: CardView = view.findViewById(R.id.deleteButtonCard)
        val editIcon: ImageView = view.findViewById(R.id.editUserIcon)
        val studentDetailIcon: ImageView = view.findViewById(R.id.studentDetailIcon)
        val deleteIcon: ImageView = view.findViewById(R.id.deleteUserIcon)
        val addedByText: TextView = view.findViewById(R.id.addedByText)
        val groupAdminLabel: TextView = view.findViewById(R.id.groupAdminLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        
        // Show name with roll number for students if available
        val rollNumber = rollNumbers[user.id]
        if (user.role.equals("STUDENT", ignoreCase = true) && !rollNumber.isNullOrBlank()) {
            holder.nameText.text = "${user.name} ($rollNumber)"
        } else {
            holder.nameText.text = user.name
        }
        
        holder.emailText.text = user.email

        // Show Group Admin label if user is a group admin
        if (groupAdminIds.contains(user.id)) {
            holder.groupAdminLabel.visibility = View.VISIBLE
        } else {
            holder.groupAdminLabel.visibility = View.GONE
        }

        // Show added by info for system admin only
        if (showAddedBy) {
            val creatorId = user.createdBy
            val creatorName = creatorNames[creatorId]
            if (creatorName != null) {
                val addedByText = if (creatorId == currentAdminId) {
                    "added by you"
                } else {
                    "added by: $creatorName"
                }
                holder.addedByText.text = addedByText
                holder.addedByText.visibility = View.VISIBLE
            } else {
                holder.addedByText.visibility = View.GONE
            }
        } else {
            holder.addedByText.visibility = View.GONE
        }

        if (!user.profileImage.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.profileImage)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_profile)
        }

        // Make the entire row clickable to show user details (unless disabled)
        if (!disableRowClick) {
            holder.itemView.setOnClickListener {
                onUserClick(user)
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }

        if (showActions) {
            holder.editCard.visibility = View.VISIBLE
            holder.editIcon.setOnClickListener { onEditClick(user) }
            
            if (showOnlyEdit) {
                // Show only edit icon, hide others
                holder.deleteCard.visibility = View.GONE
                holder.studentDetailCard.visibility = View.GONE
            } else {
                // Show all action icons
                holder.deleteCard.visibility = View.VISIBLE
                holder.deleteIcon.setOnClickListener { onDeleteClick(user) }
                
                // Show student detail button only for students
                if (user.role.equals("STUDENT", ignoreCase = true)) {
                    holder.studentDetailCard.visibility = View.VISIBLE
                    holder.studentDetailIcon.setOnClickListener { onStudentDetailClick(user) }
                } else {
                    holder.studentDetailCard.visibility = View.GONE
                }
            }
        } else {
            holder.editCard.visibility = View.GONE
            holder.deleteCard.visibility = View.GONE
            holder.studentDetailCard.visibility = View.GONE
        }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<UserEntity>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
