package com.example.audio_text_demo

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class MainActivity : AppCompatActivity() {

    private val notes = mutableListOf<Note>()
    private lateinit var adapter: NoteAdapter
    private lateinit var db: FirebaseFirestore
    private var mediaPlayer: MediaPlayer? = null
    private var recorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val database = FirebaseDatabase.getInstance("https://voicenoteapp-example-default-rtdb.firebaseio.com/")
    val notesRef: DatabaseReference = database.getReference("notes")
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissionsAndStartRecording()
        progressBar = findViewById(R.id.progressBar)
        db = FirebaseFirestore.getInstance()
        adapter = NoteAdapter(notes, ::playNote, ::deleteNote)
        findViewById<RecyclerView>(R.id.notesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.btnVoiceInput).setOnClickListener { promptSpeech() }
        findViewById<Button>(R.id.btnCrash).setOnClickListener { getCrash() }

        loadNotes()
    }

    private fun showLoader() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        progressBar.visibility = View.GONE
    }

    private fun loadNotes() {
        showLoader()
        db.collection("notes").get()
            .addOnSuccessListener { result ->
                notes.clear()
                for (doc in result) {
                    val note = doc.toObject(Note::class.java).copy(id = doc.id)
                    notes.add(note)
                }
                adapter.notifyDataSetChanged()
                hideLoader()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load notes", Toast.LENGTH_SHORT).show()
                hideLoader()
            }
    }

    private fun checkPermissionsAndStartRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Permission is there", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        startActivityForResult(intent, 1001)
    }

    private fun getCrash() {
        // Add audio recording logic here (see previous message)
        // Then upload to Firebase Storage and add note
        // Creates a button that mimics a crash when pressed
        Toast.makeText(this, "Got Crash", Toast.LENGTH_SHORT).show()
        throw RuntimeException("Test Crash") // Force a crash

    }

    private fun playNote(note: Note) {
        note.audioUrl?.let { url ->
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener {
                        start()
                    }
                    setOnErrorListener { _, what, extra ->
                        Toast.makeText(this@MainActivity, "Playback error: $what", Toast.LENGTH_SHORT).show()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } ?: Toast.makeText(this, "No audio URL", Toast.LENGTH_SHORT).show()
    }

    private fun deleteNote(note: Note) {
        db.collection("notes").document(note.id).delete().addOnSuccessListener {
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
            }
            loadNotes()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = result?.get(0) ?: return

            startRecording()
            Toast.makeText(this, "Recording started. Press Stop when done.", Toast.LENGTH_SHORT).show()

            val stopBtn = findViewById<Button>(R.id.btnStopRecording)
            stopBtn.visibility = View.VISIBLE
            stopBtn.setOnClickListener {
                stopBtn.visibility = View.GONE
                stopRecordingAndUpload(text)
            }
        }
    }

    private fun startRecording() {
        val outputDir = cacheDir
        val outputFile = File.createTempFile("note_audio_", ".3gp", outputDir)
        audioFilePath = outputFile.absolutePath

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFilePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }
    }

    private fun stopRecordingAndUpload(text: String) {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        val audioUri = Uri.fromFile(File(audioFilePath))
        uploadAudio(audioUri, text)
    }

    private fun uploadAudio(audioUri: Uri, text: String) {
        showLoader()
        val storageRef = FirebaseStorage.getInstance().reference
        val audioRef = storageRef.child("notes_audio/${System.currentTimeMillis()}.3gp")

        audioRef.putFile(audioUri)
            .addOnSuccessListener {
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    saveNote(text, uri.toString()) // <-- Fix: use download URL here
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
                saveNote(text, "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3") // <-- Fix: use download URL here
                hideLoader()
                loadNotes()
            }
    }

    private fun saveNote(text: String, audioUrl: String?) {
        val newNote = hashMapOf(
            "text" to text,
            "audioUrl" to audioUrl
        )

        db.collection("notes")
            .add(newNote)
            .addOnSuccessListener {
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                loadNotes()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Note is not saved", Toast.LENGTH_SHORT).show()
                hideLoader()
            }
    }

}
