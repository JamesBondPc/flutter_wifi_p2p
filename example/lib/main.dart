import 'package:flutter/material.dart';
import 'package:flutter_wifi_p2p/flutter_wifi_p2p.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Wifi P2P Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: WifiP2pTestPage(),
    );
  }
}

class WifiP2pTestPage extends StatefulWidget {
  @override
  _WifiP2pTestPageState createState() => _WifiP2pTestPageState();
}

class _WifiP2pTestPageState extends State<WifiP2pTestPage> {
  String _status = 'Idle';
  List<String> _deviceList = [];
  // 调用发现设备的方法
  Future<void> _discoverPeers() async {
    try {
      String? result = await FlutterWifiP2p.discoverPeers();
      setState(() {
        _status = 'Discovering peers: $result';
      });
    } catch (e) {
      setState(() {
        _status = 'Error discovering peers: $e';
      });
    }
  }

  // 连接到设备
  Future<void> _connect(String address) async {
    try {
      String? result = await FlutterWifiP2p.connect(address);
      setState(() {
        _status = 'Connected to $address: $result';
      });
    } catch (e) {
      setState(() {
        _status = 'Error connecting to $address: $e';
      });
    }
  }

  // 断开连接
  Future<void> _disconnect() async {
    try {
      String? result = await FlutterWifiP2p.disconnect();
      setState(() {
        _status = 'Disconnected: $result';
      });
    } catch (e) {
      setState(() {
        _status = 'Error disconnecting: $e';
      });
    }
  }

  Future<void> _startScan() async {
    setState(() {
      _status = "Scanning...";
    });

    // Call the startScan method
    final Map<String, dynamic>? result = await FlutterWifiP2p.startScan();
    print(result);
  }

  @override
  initState() {
    super.initState();
    requestPermissions();

    FlutterWifiP2p.listenToPeers((devices) {
      setState(() {
        _deviceList = devices;
      });
    });
  }

  Future<void> requestPermissions() async {
    await [
      Permission.location,
      Permission.storage,
      Permission.nearbyWifiDevices,
      Permission.manageExternalStorage,
      Permission.accessMediaLocation,
      Permission.nearbyWifiDevices,
      Permission.locationWhenInUse,
      Permission.locationAlways,
    ].request();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('WiFi P2P Test'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              'Status: $_status',
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 20),
            ElevatedButton(
              onPressed: _discoverPeers,
              child: Text('Discover Peers'),
            ),
            SizedBox(height: 10),
            ElevatedButton(
              onPressed: () => _connect('device_address'), // 替换为实际的设备地址
              child: Text('Connect to Device'),
            ),
            SizedBox(height: 10),
            ElevatedButton(
              onPressed: _disconnect,
              child: Text('Disconnect'),
            ),
            ElevatedButton(
              onPressed: _startScan,
              child: Text('Start Scan'),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: _deviceList.length,
                itemBuilder: (context, index) {
                  final device = _deviceList[index];
                  return ListTile(
                    title: Text(_deviceList[index]),
                    onTap: () {
                      // 从设备信息中提取设备地址
                      final address = device.split('(')[1].replaceAll(')', '');
                      _connect(address); // 调用连接方法
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
