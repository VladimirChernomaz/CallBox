package com.callbox.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.UUID

/**
 * Thin client for the Metered Realtime Messaging raw WebSocket protocol.
 * See https://www.metered.ca/docs/llms-realtime-messaging-raw-websocket.txt
 */
class MeteredSignaling(private val publishableKey: String, private val channel: String) {

    interface Listener {
        fun onWelcome(iceServers: List<PeerConnection.IceServer>)
        fun onPeerJoined(peerId: String)
        fun onPeerLeft(peerId: String)
        fun onDirect(from: String, data: JSONObject)
        fun onError(message: String)
        fun onClosed()
    }

    var listener: Listener? = null
    private var ws: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var myPeerId: String? = null
    private val client = OkHttpClient.Builder().build()

    fun myId(): String? = myPeerId

    fun connect() {
        val url = "wss://rms.metered.ca/v1?key=$publishableKey"
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}

            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post { handleFrame(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.post { listener?.onClosed() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handler.post { listener?.onError(t.message ?: "connection failed") }
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
    }

    private fun handleFrame(text: String) {
        val obj = try { JSONObject(text) } catch (e: Exception) { return }
        when (obj.optString("type")) {
            "welcome" -> {
                myPeerId = obj.optString("peerId")
                val iceArr = obj.optJSONObject("metadata")?.optJSONArray("iceServers")
                listener?.onWelcome(parseIceServers(iceArr))
                subscribe()
            }
            "presence" -> {
                val joined = obj.optJSONArray("joined")
                if (joined != null) {
                    for (i in 0 until joined.length()) {
                        val peerId = joined.getJSONObject(i).optString("peerId")
                        if (peerId.isNotEmpty() && peerId != myPeerId) listener?.onPeerJoined(peerId)
                    }
                }
                val left = obj.optJSONArray("left")
                if (left != null) {
                    for (i in 0 until left.length()) {
                        val peerId = left.getJSONObject(i).optString("peerId")
                        if (peerId.isNotEmpty()) listener?.onPeerLeft(peerId)
                    }
                }
            }
            "direct" -> {
                val from = obj.optString("from")
                val data = obj.optJSONObject("data") ?: JSONObject()
                listener?.onDirect(from, data)
            }
            "error" -> listener?.onError(obj.optString("code", "unknown_error"))
            "ack", "going_away" -> {}
        }
    }

    private fun subscribe() {
        send(JSONObject().apply {
            put("type", "subscribe")
            put("channel", channel)
            put("id", "sub1")
        })
    }

    fun sendTo(peerId: String, data: JSONObject) {
        send(JSONObject().apply {
            put("type", "send")
            put("to", peerId)
            put("data", data)
            put("id", UUID.randomUUID().toString())
        })
    }

    private fun send(obj: JSONObject) {
        ws?.send(obj.toString())
    }

    private fun parseIceServers(arr: JSONArray?): List<PeerConnection.IceServer> {
        val result = mutableListOf<PeerConnection.IceServer>()
        if (arr == null) return result
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val urls = mutableListOf<String>()
            when (val u = o.opt("urls")) {
                is JSONArray -> for (j in 0 until u.length()) urls.add(u.getString(j))
                is String -> urls.add(u)
            }
            if (urls.isEmpty()) continue
            val builder = PeerConnection.IceServer.builder(urls)
            if (o.has("username")) builder.setUsername(o.getString("username"))
            if (o.has("credential")) builder.setPassword(o.getString("credential"))
            result.add(builder.createIceServer())
        }
        return result
    }
}
