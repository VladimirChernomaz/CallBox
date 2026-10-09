package com.callbox.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callbox.app.databinding.ActivityMainBinding
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class MainActivity : AppCompatActivity(), MeteredSignaling.Listener, WebRtcManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var signaling: MeteredSignaling
    private lateinit var webRtc: WebRtcManager

    private var otherPeerId: String? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private var inCall = false
    private var muted = false
    private var speakerOn = false
    private var ringtone: Ringtone? = null

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        } catch (_: Throwable) {
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
        ringtone = null
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startUp() else {
            binding.textStatus.text = getString(R.string.need_mic_permission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lastCrash = CrashLog.getAndClear(this)
        val lastBreadcrumb = Breadcrumb.getAndClear(this)
        if (lastCrash != null || lastBreadcrumb != null) {
            val text = buildString {
                if (lastBreadcrumb != null) append("Последний шаг перед вылетом:\n$lastBreadcrumb\n\n")
                if (lastCrash != null) append("Исключение:\n${lastCrash.take(1500)}")
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Прошлый вылет")
                .setMessage(text)
                .setPositiveButton("Ок", null)
                .show()
        }

        webRtc = WebRtcManager(applicationContext)
        webRtc.init()
        webRtc.listener = this

        signaling = MeteredSignaling(Prefs.getPublishableKey(this), Prefs.getChannel(this))
        signaling.listener = this

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FORCE_EDIT, true))
        }
        binding.buttonCall.setOnClickListener { startCall() }
        binding.buttonAccept.setOnClickListener { acceptCall() }
        binding.buttonDecline.setOnClickListener { declineCall() }
        binding.buttonHangup.setOnClickListener { hangUp() }
        binding.buttonMute.setOnClickListener { toggleMute() }
        binding.buttonSpeaker.setOnClickListener { toggleSpeaker() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startUp()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startUp() {
        binding.textStatus.text = getString(R.string.connecting)
        signaling.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
        signaling.disconnect()
        webRtc.close()
    }

    // ---------- UI state ----------

    private fun showIdle() {
        stopRinging()
        binding.buttonCall.visibility = if (otherPeerId != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.incomingBar.visibility = android.view.View.GONE
        binding.inCallBar.visibility = android.view.View.GONE
        binding.textStatus.text = if (otherPeerId != null)
            getString(R.string.partner_online) else getString(R.string.waiting_for_partner)
    }

    private fun showIncoming(fromName: String) {
        binding.buttonCall.visibility = android.view.View.GONE
        binding.incomingBar.visibility = android.view.View.VISIBLE
        binding.inCallBar.visibility = android.view.View.GONE
        binding.textStatus.text = getString(R.string.incoming_call)
        startRinging()
    }

    private fun showInCall() {
        stopRinging()
        binding.buttonCall.visibility = android.view.View.GONE
        binding.incomingBar.visibility = android.view.View.GONE
        binding.inCallBar.visibility = android.view.View.VISIBLE
        binding.textStatus.text = getString(R.string.in_call)
    }

    // ---------- Call actions ----------

    private fun startCall() {
        val peer = otherPeerId ?: return
        try {
            Breadcrumb.mark(this, "startCall: begin, iceServers=${iceServers.size}")
            webRtc.createConnection(iceServers)
            Breadcrumb.mark(this, "startCall: after createConnection, setting speakerphone")
            webRtc.setSpeakerphoneOn(speakerOn)
            inCall = true
            Breadcrumb.mark(this, "startCall: before createOffer")
            webRtc.createOffer()
            Breadcrumb.mark(this, "startCall: before showInCall")
            showInCall()
            Breadcrumb.mark(this, "startCall: done")
        } catch (e: Throwable) {
            inCall = false
            binding.textStatus.text = "Ошибка (startCall): ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private var pendingOfferSdp: String? = null

    private fun acceptCall() {
        val peer = otherPeerId ?: return
        val sdp = pendingOfferSdp ?: return
        try {
            Breadcrumb.mark(this, "acceptCall: begin, iceServers=${iceServers.size}")
            webRtc.createConnection(iceServers)
            Breadcrumb.mark(this, "acceptCall: after createConnection, setting speakerphone")
            webRtc.setSpeakerphoneOn(speakerOn)
            Breadcrumb.mark(this, "acceptCall: before setRemoteDescription")
            webRtc.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
            Breadcrumb.mark(this, "acceptCall: before createAnswer")
            webRtc.createAnswer()
            inCall = true
            showInCall()
            Breadcrumb.mark(this, "acceptCall: done")
        } catch (e: Throwable) {
            inCall = false
            binding.textStatus.text = "Ошибка (acceptCall): ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun declineCall() {
        val peer = otherPeerId
        pendingOfferSdp = null
        if (peer != null) signaling.sendTo(peer, JSONObject().put("kind", "hangup"))
        showIdle()
    }

    private fun hangUp() {
        val peer = otherPeerId
        if (peer != null) signaling.sendTo(peer, JSONObject().put("kind", "hangup"))
        webRtc.close()
        inCall = false
        showIdle()
    }

    private fun toggleMute() {
        muted = !muted
        webRtc.setMuted(muted)
        binding.buttonMute.text = if (muted) getString(R.string.unmute) else getString(R.string.mute)
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        webRtc.setSpeakerphoneOn(speakerOn)
        binding.buttonSpeaker.text = if (speakerOn) getString(R.string.speaker_off) else getString(R.string.speaker_on)
    }

    // ---------- MeteredSignaling.Listener ----------

    override fun onWelcome(iceServers: List<PeerConnection.IceServer>) {
        this.iceServers = iceServers
        showIdle()
    }

    override fun onPeerJoined(peerId: String) {
        otherPeerId = peerId
        if (!inCall) showIdle()
    }

    override fun onPeerLeft(peerId: String) {
        if (peerId == otherPeerId) {
            otherPeerId = null
            if (inCall) {
                webRtc.close()
                inCall = false
            }
            showIdle()
        }
    }

    override fun onDirect(from: String, data: JSONObject) {
        when (data.optString("kind")) {
            "offer" -> {
                otherPeerId = from
                pendingOfferSdp = data.optString("sdp")
                showIncoming(from)
            }
            "answer" -> {
                webRtc.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")))
            }
            "candidate" -> {
                webRtc.addIceCandidate(
                    IceCandidate(data.optString("sdpMid"), data.optInt("sdpMLineIndex"), data.optString("candidate"))
                )
            }
            "hangup" -> {
                webRtc.close()
                inCall = false
                pendingOfferSdp = null
                showIdle()
            }
        }
    }

    override fun onError(message: String) {
        binding.textStatus.text = getString(R.string.signaling_error, message)
    }

    override fun onClosed() {
        binding.textStatus.text = getString(R.string.disconnected)
    }

    // ---------- WebRtcManager.Listener ----------

    override fun onLocalIceCandidate(candidate: IceCandidate) {
        val peer = otherPeerId ?: return
        signaling.sendTo(peer, JSONObject().apply {
            put("kind", "candidate")
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        })
    }

    override fun onLocalDescription(sdp: SessionDescription) {
        val peer = otherPeerId ?: return
        val kind = if (sdp.type == SessionDescription.Type.OFFER) "offer" else "answer"
        signaling.sendTo(peer, JSONObject().apply {
            put("kind", kind)
            put("sdp", sdp.description)
        })
    }

    override fun onConnected() {
        runOnUiThread { binding.textStatus.text = getString(R.string.in_call) }
    }

    override fun onDisconnected() {
        runOnUiThread {
            inCall = false
            showIdle()
        }
    }
}
