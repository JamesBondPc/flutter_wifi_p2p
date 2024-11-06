import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_wifi_p2p_platform_interface.dart';

/// An implementation of [FlutterWifiP2pPlatform] that uses method channels.
class MethodChannelFlutterWifiP2p extends FlutterWifiP2pPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_wifi_p2p');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
