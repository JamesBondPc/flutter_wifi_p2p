import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_wifi_p2p_method_channel.dart';

abstract class FlutterWifiP2pPlatform extends PlatformInterface {
  /// Constructs a FlutterWifiP2pPlatform.
  FlutterWifiP2pPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterWifiP2pPlatform _instance = MethodChannelFlutterWifiP2p();

  /// The default instance of [FlutterWifiP2pPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterWifiP2p].
  static FlutterWifiP2pPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterWifiP2pPlatform] when
  /// they register themselves.
  static set instance(FlutterWifiP2pPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
