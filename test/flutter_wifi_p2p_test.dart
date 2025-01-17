import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_wifi_p2p/flutter_wifi_p2p.dart';
import 'package:flutter_wifi_p2p/flutter_wifi_p2p_platform_interface.dart';
import 'package:flutter_wifi_p2p/flutter_wifi_p2p_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterWifiP2pPlatform
    with MockPlatformInterfaceMixin
    implements FlutterWifiP2pPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterWifiP2pPlatform initialPlatform = FlutterWifiP2pPlatform.instance;

  test('$MethodChannelFlutterWifiP2p is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterWifiP2p>());
  });

  test('getPlatformVersion', () async {
    FlutterWifiP2p flutterWifiP2pPlugin = FlutterWifiP2p();
    MockFlutterWifiP2pPlatform fakePlatform = MockFlutterWifiP2pPlatform();
    FlutterWifiP2pPlatform.instance = fakePlatform;

    expect(await flutterWifiP2pPlugin.getPlatformVersion(), '42');
  });
}
