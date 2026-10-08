package com.callbox.app

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

class WebRtcManager(private val appContext: Context) {

    interface Listener {
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onLocalDescription(sdp: SessionDescription)
        fun onConnected()
        fun onDisconnected()
    }

    var listener: Listener? = null
    private lateinit var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun createConnection(iceServers: List<PeerConnection.IceServer>) {
        Breadcrumb.mark(appContext, "createConnection: building RTCConfiguration (${iceServers.size} ice servers)")
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        Breadcrumb.mark(appContext, "createConnection: calling factory.createPeerConnection")
        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) { listener?.onLocalIceCandidate(c) }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> listener?.onConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> listener?.onDisconnected()
                    else -> {}
                }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        Breadcrumb.mark(appContext, "createConnection: pc created=${pc != null}, creating audio source")
        val audioSource = factory.createAudioSource(MediaConstraints())
        Breadcrumb.mark(appContext, "createConnection: creating audio track")
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        Breadcrumb.mark(appContext, "createConnection: adding track to pc")
        pc?.addTrack(localAudioTrack, listOf("stream0"))
        Breadcrumb.mark(appContext, "createConnection: done")
    }

    fun createOffer() {
        Breadcrumb.mark(appContext, "createOffer: calling pc.createOffer")
        pc?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Breadcrumb.mark(appContext, "createOffer: onCreateSuccess, setting local description")
                sdp ?: return
                pc?.setLocalDescription(SdpObserverAdapter(), sdp)
                listener?.onLocalDescription(sdp)
            }
            override fun onCreateFailure(p0: String?) {
                Breadcrumb.mark(appContext, "createOffer: onCreateFailure $p0")
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        Breadcrumb.mark(appContext, "createAnswer: calling pc.createAnswer")
        pc?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Breadcrumb.mark(appContext, "createAnswer: onCreateSuccess, setting local description")
                sdp ?: return
                pc?.setLocalDescription(SdpObserverAdapter(), sdp)
                listener?.onLocalDescription(sdp)
            }
            override fun onCreateFailure(p0: String?) {
                Breadcrumb.mark(appContext, "createAnswer: onCreateFailure $p0")
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        pc?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun addIceCandidate(c: IceCandidate) {
        pc?.addIceCandidate(c)
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerphoneOn(on: Boolean) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }

    fun close() {
        pc?.close()
        pc = null
        localAudioTrack = null
    }
}
