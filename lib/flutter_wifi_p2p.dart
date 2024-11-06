import 'package:flutter/services.dart';

import 'flutter_wifi_p2p_platform_interface.dart';

class FlutterWifiP2p {
  // 静态的 MethodChannel，用于和原生代码通信
  static const MethodChannel _channel = MethodChannel('flutter_wifi_p2p');

  Future<String?> getPlatformVersion() {
    return FlutterWifiP2pPlatform.instance.getPlatformVersion();
  }

  static Future<String?> discoverPeers() async {
    final String? result = await _channel.invokeMethod('discoverPeers');
    return result;
  }

  static Future<String?> connect(String address) async {
    final String? result =
        await _channel.invokeMethod('connect', {'address': address});
    return result;
  }

  static Future<String?> disconnect() async {
    final String? result = await _channel.invokeMethod('disconnect');
    return result;
  }

  static Future<String?> getConnectionInfo() async {
    final String? ipAddress = await _channel.invokeMethod('getConnectionInfo');
    return ipAddress;
  }

  // Method to get the multicast address from the platform side
  static Future<Map<String, String>?> getMulticastAddress() async {
    try {
      // Invoke the native method and expect a Map from the native side
      final Map<dynamic, dynamic> result =
          await _channel.invokeMethod('getMulticastAddress');

      // Convert the dynamic map to a typed map
      if (result is Map) {
        return Map<String, String>.from(result);
      } else {
        print("Unexpected result type: ${result.runtimeType}");
        return null;
      }
    } on PlatformException catch (e) {
      print("Failed to get multicast address: ${e.message}");
      return null;
    }
  }

  // 添加设备发现监听
  static void listenToPeers(Function(List<String>) onPeersDiscovered) {
    _channel.setMethodCallHandler((MethodCall call) async {
      if (call.method == "onPeersDiscovered") {
        List<String> devices = List<String>.from(call.arguments);
        onPeersDiscovered(devices);
      }
    });
  }
}
