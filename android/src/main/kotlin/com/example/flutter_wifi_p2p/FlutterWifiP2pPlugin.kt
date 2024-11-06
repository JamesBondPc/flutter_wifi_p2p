package com.example.flutter_wifi_p2p

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import androidx.annotation.NonNull
import android.content.BroadcastReceiver
 import java.net.InetAddress
import java.net.NetworkInterface
import android.content.Intent
import android.content.IntentFilter
class FlutterWifiP2pPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private var manager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private lateinit var context: Context

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flutter_wifi_p2p")
        channel.setMethodCallHandler(this)

        context = binding.applicationContext
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        p2pChannel = manager?.initialize(context, Looper.getMainLooper(), null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            "discoverPeers" -> discoverPeers(result)
            "connect" -> {
                val address = call.argument<String>("address")
                if (address != null) {
                    connect(address, result)
                } else {
                    result.error("ERROR", "Address argument missing", null)
                }
            }
            "disconnect" -> disconnect(result)
            "getConnectionInfo" -> getConnectionInfo(result)
            "getMulticastAddress" -> getMulticastAddress(result)
            else -> result.notImplemented()
        }
    }
    
    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
    val devices = peerList.deviceList.map { device ->
        "${device.deviceName} (${device.deviceAddress})"
    }
    // 返回设备列表到 Flutter
    channel.invokeMethod("onPeersDiscovered", devices)
   }

private val peerListReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
            manager?.requestPeers(p2pChannel, peerListListener)
        }
    }
}

// private fun discoverPeers(result: MethodChannel.Result) {
//     manager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
//         override fun onSuccess() {
//             result.success("Discovery started")
//             // 注册接收器以监听设备列表变化
//             context.registerReceiver(peerListReceiver, IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))
//         }

//         override fun onFailure(reason: Int) {
//             // 处理失败情况
//             handleDiscoverPeersFailure(reason, result)
//         }
//     })
// }
    private fun discoverPeers(result: MethodChannel.Result) {
    manager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            result.success("Discovery started")
            context.registerReceiver(peerListReceiver, IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))
        }

        override fun onFailure(reason: Int) {
            val errorMsg = when (reason) {
                WifiP2pManager.BUSY -> "Framework is busy. Try again."
                WifiP2pManager.ERROR -> "Internal error occurred."
                WifiP2pManager.P2P_UNSUPPORTED -> "P2P is unsupported on this device."
                else -> "Unknown error occurred."
            }
            result.error("ERROR", errorMsg, null)
        }
    })
}

// Function to get multicast-enabled IP address
private fun getMulticastAddress(result: MethodChannel.Result) {
    try {
        // Create a map to store local and broadcast IP pairs
        val broadcastMap: MutableMap<String, String> = HashMap()

        // Iterate through all network interfaces
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()

            // Check if the interface is up and supports multicast
            if (networkInterface.isUp && networkInterface.supportsMulticast() && !networkInterface.isLoopback) {
                // Iterate through each interface address associated with the network interface
                for (address in networkInterface.interfaceAddresses) {
                    val broadcastAddress = address.broadcast
                    val localAddress = address.address

                    // If a broadcast address is found, put it in the map
                    if (broadcastAddress != null) {
                        broadcastMap[localAddress.hostAddress] = broadcastAddress.hostAddress
                    }
                }
            }
        }

        // If no broadcast address is found, use the default broadcast address
       if (broadcastMap.isEmpty()) {
            result.success(emptyMap<String, String>())
        } else {
            result.success(broadcastMap)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        result.error("ERROR", "Failed to retrieve broadcast address", e.message)
    }
}

   private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
    if (info.groupFormed && info.isGroupOwner) {
        // This device is the group owner (acts like a router).
        val groupOwnerAddress = info.groupOwnerAddress.hostAddress
        channel.invokeMethod("onConnectionInfoAvailable", groupOwnerAddress)
    } else if (info.groupFormed) {
        // This device is a client in the Wi-Fi Direct group.
        val clientAddress = info.groupOwnerAddress.hostAddress
        channel.invokeMethod("onConnectionInfoAvailable", clientAddress)
    }
}

private fun getConnectionInfo(result: MethodChannel.Result) {
   manager?.requestConnectionInfo(p2pChannel, WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed) {
            val ipAddress = info.groupOwnerAddress?.hostAddress
            if (ipAddress != null) {
                result.success(ipAddress)
            } else {
                result.success("IP address unavailable.")
            }
        } else {
            result.success("Group not formed yet.")
        }
    })
}


    private fun handleDiscoverPeersFailure(reason: Int, result: MethodChannel.Result) {
    val errorMessage = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "WiFi P2P is not supported on this device."
        WifiP2pManager.ERROR -> "An error occurred."
        WifiP2pManager.BUSY -> "The system is busy."
        else -> "Unknown reason: $reason"
    }
    result.error("ERROR", errorMessage, null)
}

    private fun connect(address: String, result: MethodChannel.Result) {
        val config = WifiP2pConfig().apply {
            deviceAddress = address
        }

        manager?.connect(p2pChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Connected to $address")
            }

            override fun onFailure(reason: Int) {
                result.error("ERROR", "Connection failed", null)
            }
        })
    }

    private fun disconnect(result: MethodChannel.Result) {
        manager?.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Disconnected")
            }

            override fun onFailure(reason: Int) {
                result.error("ERROR", "Disconnection failed", null)
            }
        })
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        manager = null
        p2pChannel = null
    }
}
