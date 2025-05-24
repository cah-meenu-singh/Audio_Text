package com.example.audio_text_demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NoteAdapter(
    private val notes: MutableList<Note>,
    private val onPlayClicked: (Note) -> Unit,
    private val onDeleteClicked: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val noteText: TextView = view.findViewById(R.id.noteText)
        val btnPlay: Button = view.findViewById(R.id.btnPlay)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.noteText.text = note.toString() ?: "No Text"

        holder.btnPlay.visibility = if (!note.audioUrl.isNullOrEmpty()) View.VISIBLE else View.GONE

        holder.btnPlay.setOnClickListener { onPlayClicked(note) }
        holder.btnDelete.setOnClickListener {
            onDeleteClicked(note)
            removeAt(position)
        }
    }

    override fun getItemCount() = notes.size

    fun removeAt(position: Int) {
        notes.removeAt(position)
        notifyItemRemoved(position)
    }
}
