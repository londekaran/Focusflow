package com.example.focusflow.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks sentences with the phone's text-to-speech engine, one after another, and calls [onAllDone] when the
 * last one has finished (or when speech is not possible). It asks other audio to lower its volume meanwhile.
 * Create it and call [speak] from the main thread.
 */
class Speaker(context: Context, private val onAllDone: () -> Unit) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var finished = false
    private val queue = ArrayList<String>()
    private var remaining = 0
    private var counter = 0
    private var focus: AudioFocusRequest? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speak(texts: List<String>) {
        if (texts.isEmpty() || finished) {
            if (remaining == 0) finish()
            return
        }
        synchronized(this) {
            remaining += texts.size
            queue.addAll(texts)
        }
        if (tts == null) start() else if (ready) flush()
    }

    private fun start() {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configure()
                ready = true
                flush()
            } else {
                finish()   // no speech engine available: nothing to say aloud, the notification still shows
            }
        }
    }

    private fun configure() {
        val t = tts ?: return
        // The sentences are in English, so use English even if the phone is set to another language.
        val wanted = if (Locale.getDefault().language == "en") Locale.getDefault() else Locale.ENGLISH
        val result = t.setLanguage(wanted)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.US)
        t.setAudioAttributes(attrs)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { oneDone() }
            @Deprecated("Replaced by onError(String, Int)")
            override fun onError(utteranceId: String?) { oneDone() }
            override fun onError(utteranceId: String?, errorCode: Int) { oneDone() }
        })
    }

    private fun flush() {
        val t = tts ?: return
        val batch: List<String>
        synchronized(this) {
            batch = ArrayList(queue)
            queue.clear()
        }
        if (batch.isEmpty()) return
        requestFocus()
        for (text in batch) {
            val r = t.speak(text, TextToSpeech.QUEUE_ADD, null, "ff-${counter++}")
            if (r == TextToSpeech.ERROR) oneDone()
        }
    }

    private fun oneDone() {
        val done = synchronized(this) { --remaining <= 0 }
        if (done) finish()
    }

    private fun requestFocus() {
        try {
            val am = app.getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
            am.requestAudioFocus(req)
            focus = req
        } catch (e: Exception) {
            // Speaking still works without audio focus.
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        try {
            focus?.let { app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        } catch (e: Exception) { /* ignore */ }
        shutdown()
        onAllDone()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) { /* ignore */ }
        tts = null
    }
}
