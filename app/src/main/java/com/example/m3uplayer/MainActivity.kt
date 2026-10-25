package com.example.m3uplayer

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var all = listOf<Channel>()
    private var groups = listOf("Tümü")
    private var list = listOf<Channel>()
    private var gi = 0
    private var sel = 0
    private var playing = -1
    private var started = false
    private var listOpen = true
    private var settingsOpen = false

    private lateinit var player: ExoPlayer
    private lateinit var listPanel: View
    private lateinit var settingsPanel: View
    private lateinit var gname: TextView
    private lateinit var rv: RecyclerView
    private lateinit var urlInput: EditText
    private val adapter = Adapter()
    private val prefs by lazy { getSharedPreferences("m3u", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listPanel = findViewById(R.id.listPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        gname = findViewById(R.id.gname)
        rv = findViewById(R.id.rv)
        urlInput = findViewById(R.id.urlInput)

        player = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.playerView).player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toast("Oynatma hatası: ${error.errorCodeName}")
            }
        })

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            val u = urlInput.text.toString().trim()
            if (u.isNotEmpty()) load(u)
        }
        findViewById<Button>(R.id.cancelBtn).setOnClickListener { showSettings(false) }

        val saved = prefs.getString("url", "") ?: ""
        if (saved.isNotEmpty()) load(saved) else showSettings(true)
    }

    override fun onStop() { super.onStop(); player.pause() }
    override fun onDestroy() { super.onDestroy(); player.release() }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun load(url: String) {
        toast("Playlist yükleniyor...")
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000
                c.instanceFollowRedirects = true
                val text = c.inputStream.bufferedReader().use { it.readText() }
                val ch = M3uParser.parse(text)
                runOnUiThread {
                    if (ch.isEmpty()) { toast("Playlistte kanal bulunamadı"); return@runOnUiThread }
                    all = ch
                    groups = listOf("Tümü") + ch.map { it.group }.distinct()
                    prefs.edit().putString("url", url).apply()
                    setGroup(0)
                    showSettings(false)
                    toast("${ch.size} kanal yüklendi")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Playlist indirilemedi: ${e.message}") }
            }
        }.start()
    }

    private fun setGroup(n: Int) {
        gi = (n + groups.size) % groups.size
        list = if (gi == 0) all else all.filter { it.group == groups[gi] }
        sel = 0; playing = -1
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        updateHeader()
    }

    private fun updateHeader() {
        gname.text = "◀ ${groups[gi]} (${if (list.isEmpty()) 0 else sel + 1}/${list.size}) ▶"
    }

    private fun move(d: Int) {
        if (list.isEmpty()) return
        val old = sel
        sel = (sel + d + list.size) % list.size
        adapter.notifyItemChanged(old); adapter.notifyItemChanged(sel)
        rv.scrollToPosition(sel)
        updateHeader()
    }

    private fun playIndex(i: Int) {
        if (list.isEmpty()) return
        val old = playing
        playing = (i + list.size) % list.size
        sel = playing
        started = true
        player.setMediaItem(MediaItem.fromUri(list[playing].url))
        player.prepare(); player.playWhenReady = true
        toast(list[playing].name)
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(sel)
        updateHeader()
    }

    private fun step(d: Int) = playIndex((if (playing >= 0) playing else sel) + d)

    private fun showList(b: Boolean) { listOpen = b; listPanel.visibility = if (b) View.VISIBLE else View.GONE }

    private fun showSettings(b: Boolean) {
        settingsOpen = b
        settingsPanel.visibility = if (b) View.VISIBLE else View.GONE
        if (b) {
            urlInput.setText(prefs.getString("url", ""))
            urlInput.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (settingsOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { showSettings(false); return true }
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (listOpen && started) showList(false)
                else if (!listOpen) showList(true)
                else finish()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_SETTINGS -> { showSettings(true); return true }
            KeyEvent.KEYCODE_CHANNEL_UP -> { step(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true }
        }
        if (listOpen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> move(-1)
                KeyEvent.KEYCODE_DPAD_DOWN -> move(1)
                KeyEvent.KEYCODE_DPAD_LEFT -> setGroup(gi - 1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> setGroup(gi + 1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { playIndex(sel); showList(false) }
                else -> return super.onKeyDown(keyCode, event)
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> step(-1)
                KeyEvent.KEYCODE_DPAD_DOWN -> step(1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> showList(true)
                else -> return super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel, p, false) as TextView)
        override fun getItemCount() = list.size
        override fun onBindViewHolder(h: VH, i: Int) {
            h.tv.text = (if (i == playing) "▶ " else "") + list[i].name
            h.tv.setBackgroundColor(if (i == sel) Color.parseColor("#1E88E5") else Color.TRANSPARENT)
        }
    }
}
