package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.audio.VoiceCatalog
import com.realsneed.biblereader.data.VoicePrefs

/**
 * Picks the reading voice: engine, gender filter, and the individual voice.
 *
 * Voices are read from the real engine on the device, so whatever the phone
 * actually has (Google's network voices, Samsung's on-device ones) shows up.
 * Choosing one applies it immediately and, if the Bible is being read, you hear
 * the change on the next verse.
 */
class VoicePickerSheet(
    private val context: Context,
    private val prefs: VoicePrefs,
    private val onVoiceChanged: () -> Unit,
    private val onPreview: (String) -> Unit
) {

    private val dialog = Dialog(context)
    private lateinit var adapter: VoiceAdapter
    private lateinit var engineRow: TextView
    private var probe: TextToSpeech? = null
    private var voices: List<Voice> = emptyList()
    private var genderFilter: String? = null

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_voice_picker)

        engineRow = dialog.findViewById(R.id.voiceEngineRow)
        engineRow.setOnClickListener { chooseEngine() }
        refreshEngineLabel()

        adapter = VoiceAdapter { voice -> select(voice) }
        val list = dialog.findViewById<RecyclerView>(R.id.voiceList)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter

        dialog.findViewById<RadioGroup>(R.id.voiceGender)
            .setOnCheckedChangeListener { _, checkedId ->
                genderFilter = when (checkedId) {
                    R.id.voiceGenderFemale -> "female"
                    R.id.voiceGenderMale -> "male"
                    else -> null
                }
                applyFilter()
            }

        dialog.findViewById<TextView>(R.id.voiceDone).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.voicePreview).setOnClickListener {
            onPreview(context.getString(R.string.voice_sample))
        }

        dialog.setOnDismissListener { shutdown() }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * HEIGHT_FRACTION).toInt()
            )
        }
        loadVoices()
    }

    // ------------------------------------------------------------------ voices

    private fun loadVoices() {
        shutdown()
        val engine = prefs.engine
        probe = TextToSpeech(context, { status ->
            if (status != TextToSpeech.SUCCESS) {
                voices = emptyList()
            } else {
                voices = probe?.voices
                    ?.filter { it.locale.language == "en" }
                    ?.sortedWith(VoiceCatalog.best)
                    ?: emptyList()
            }
            applyFilter()
        }, engine)
    }

    private fun applyFilter() {
        val filtered = if (genderFilter == null) {
            voices
        } else {
            voices.filter { VoiceCatalog.genderOf(it) == genderFilter }
        }
        adapter.submit(filtered, prefs.voiceName)
    }

    private fun select(voice: Voice) {
        prefs.voiceName = voice.name
        adapter.submit(adapter.currentItems(), voice.name)
        onVoiceChanged()
    }

    private fun refreshEngineLabel() {
        engineRow.text = context.getString(
            R.string.voice_engine,
            VoiceCatalog.engineLabel(context, prefs.engine)
        )
    }

    private fun chooseEngine() {
        val engines = VoiceCatalog.engines(context)
        if (engines.isEmpty()) return
        val labels = engines.map { it.label }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.voice_change_engine)
            .setItems(labels) { _, which ->
                prefs.engine = engines[which].packageName
                prefs.voiceName = null
                refreshEngineLabel()
                loadVoices()
                onVoiceChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shutdown() {
        probe?.shutdown()
        probe = null
    }

    private companion object {
        const val HEIGHT_FRACTION = 0.7f
    }
}

private class VoiceAdapter(
    private val onClick: (Voice) -> Unit
) : RecyclerView.Adapter<VoiceAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.voiceTitle)
        val subtitle: TextView = view.findViewById(R.id.voiceSubtitle)
        val check: ImageView = view.findViewById(R.id.voiceCheck)
    }

    private var items: List<Voice> = emptyList()
    private var selected: String? = null

    fun currentItems(): List<Voice> = items

    fun submit(newItems: List<Voice>, selectedName: String?) {
        items = newItems
        selected = selectedName
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_voice_choice, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val voice = items[position]
        holder.title.text = VoiceCatalog.title(voice)
        holder.subtitle.text = VoiceCatalog.subtitle(holder.itemView.context, voice)
        holder.check.visibility = if (voice.name == selected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(voice) }
    }
}
