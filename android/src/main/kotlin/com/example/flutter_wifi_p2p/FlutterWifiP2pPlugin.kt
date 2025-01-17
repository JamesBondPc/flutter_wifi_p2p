package com.example.flutter_wifi_p2p
import android.net.NetworkRequest
import android.net.NetworkRequest.Builder
import android.net.NetworkCapabilities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Vector
import java.net.DatagramSocket
import java.io.IOException 
import java.net.SocketException

class FlutterWifiP2pPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private var manager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private lateinit var context: Context
    private val CHUNK_MAX_LENGTH = 4 * 1024
    private val mNetworks = Vector<Network>()
    private val mAvailableNetworks: MutableMap<InetAddress, Network> = HashMap()
    private val mMacAddress: MutableMap<InetAddress, String> = HashMap()
    private val mContext: Context? = null
    var mBebirdList: Map<InetAddress, String>? = null
    // 定义 TAG 常量
     private val TAG = "FlutterWifiP2pPlugin" 

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
             "startScan" -> {
                startScan(58090,context) // Call the method to handle scan on the native side
                result.success("Scan started") // Send a success message back to Flutter
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

     fun getBroadcastIP(): Map<InetAddress, InetAddress> {
        val result = mutableMapOf<InetAddress, InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (ni.isUp && ni.supportsMulticast()) {
                    ni.interfaceAddresses.forEach { interfaceAddress ->
                        val broadcastAddress = interfaceAddress.broadcast
                        val address = interfaceAddress.address
                        if (broadcastAddress != null && address != null) {
                            result[address] = broadcastAddress
                            val mac = getMacAddress(address.hostAddress)
                            if (mac != null && mac != "020000000000") {
                                mMacAddress.putIfAbsent(address, mac)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    private fun getMacAddress(ipAddress: String): String {
        return "00:11:22:33:44:55"
    }

   private fun startScan(port: Int, ctx: Context): Map<InetAddress, String>? {
    val buf = ByteArray(CHUNK_MAX_LENGTH)
    val info = ByteArray(CHUNK_MAX_LENGTH)
    var skt: DatagramSocket? = null
    var devInfo: MutableMap<InetAddress, String>? = null
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val mContext = ctx

    mAvailableNetworks.clear()
    mMacAddress.clear()

    if (mNetworks.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (!mNetworks.contains(network)) {
                    mNetworks.add(network)
                }
            }
        })
    }

    try {
    val ipPair = getBroadcastIP()
    val dpt = DatagramPacket(buf, CHUNK_MAX_LENGTH)

    val BK_START_CONF = byteArrayOf(0x66.toByte(), 0x39.toByte(), 0x01.toByte(), 0x01.toByte())

    for (localIp in ipPair.keys) {
        var tryCnt = 0
        var recvLen = 0
        var skt: DatagramSocket? = null  // Initialize socket to null

        for (network in mNetworks) {
            for (addr in cm.getLinkProperties(network)?.linkAddresses.orEmpty()) { // Using 'orEmpty()' to handle null safely
                when {
                    addr.address == localIp -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            cm.bindProcessToNetwork(network)
                        }
                        skt = DatagramSocket(0, localIp)
                    }
                    localIp.hostAddress == "255.255.255.255" -> {
                        val gateway = addr.address.address
                        if (gateway.size == 4 && gateway[0] == 192.toByte() && gateway[1] == 168.toByte() &&
                            (gateway[2] == 5.toByte() || gateway[2] == 188.toByte())) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                cm.bindProcessToNetwork(network)
                            }
                            skt = DatagramSocket(0, addr.address)
                        }
                    }
                }
            }
        }

        if (skt == null) skt = DatagramSocket(0, localIp)
        skt?.soTimeout = 200

        try {
            val broadcastIp = ipPair[localIp]
            broadcastIp?.let {
                skt?.send(DatagramPacket(BK_START_CONF, BK_START_CONF.size, it, port))
            }

            var addr: InetAddress? = null
            while (recvLen < 1500 && tryCnt++ < 5) {
                try {
                    skt?.receive(dpt)
                } catch (ie: IOException) {
                    ie.printStackTrace()
                    continue
                }
                val rlen = dpt.length
                if (rlen > 5) {
                    System.arraycopy(buf, 0, info, recvLen, rlen)
                    recvLen += rlen
                }
            }

            if (devInfo == null) {
                devInfo = mutableMapOf()
            }

            Log.v(TAG, "=====> 回应总包长 $recvLen try_cnt $tryCnt b_addr ${broadcastIp?.hostAddress}")
            if (recvLen >= 1500 && tryCnt <= 5) {
                addr = dpt.address
                devInfo[addr] = String(info, 0, recvLen, Charsets.UTF_8)
            }

            if (addr != null && (mAvailableNetworks.isEmpty() || !mAvailableNetworks.containsKey(addr))) {
                if (mMacAddress.isNotEmpty() && mMacAddress.containsKey(localIp)) {
                    mMacAddress[addr] = mMacAddress[localIp] ?: ""
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                   val boundNetwork = cm.getBoundNetworkForProcess()
                    if (boundNetwork != null) {
                         mAvailableNetworks[addr] = boundNetwork
                        }
                }
            }
        } catch (ie: IOException) {
            if (devInfo == null && skt != null) {
                try {
                    val gateway = localIp.address
                    gateway[3] = 1.toByte()

                    skt?.apply {
                        broadcast = false
                        recvLen = 0
                        send(DatagramPacket(BK_START_CONF, BK_START_CONF.size, InetAddress.getByAddress(gateway), port))
                    }

                    var addr: InetAddress? = null
                    tryCnt = 0
                    while (recvLen < 1500 && tryCnt++ < 5) {
                        try {
                            skt?.receive(dpt)
                        } catch (ioe: IOException) {
                            continue
                        }
                        val rlen = dpt.length
                        if (rlen > 5) {
                            System.arraycopy(buf, 0, info, recvLen, rlen)
                            recvLen += rlen
                        }
                    }

                    if (devInfo == null) {
                        devInfo = mutableMapOf()
                    }

                    if (recvLen >= 1500 && tryCnt <= 5) {
                        addr = dpt.address
                        devInfo[addr] = String(info, 0, recvLen, Charsets.UTF_8)
                    }

                    if (addr != null && (mAvailableNetworks.isEmpty() || !mAvailableNetworks.containsKey(addr))) {
                        if (mMacAddress.isNotEmpty() && mMacAddress.containsKey(localIp)) {
                            mMacAddress[addr] = mMacAddress[localIp] ?: ""
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                             val boundNetwork = cm.getBoundNetworkForProcess()
                    if (boundNetwork != null) {
                         mAvailableNetworks[addr] = boundNetwork
                        }
                        }
                    }
                } catch (e: IOException) {
                    if (devInfo == null && skt != null) {
                        skt.close()
                        skt = null
                        continue
                    }
                }
            }
        }

        skt?.close()
        skt = null
    }
} catch (se: SocketException) {
    mNetworks.clear()
} finally {
    skt?.close()
}


    return devInfo
}

}
