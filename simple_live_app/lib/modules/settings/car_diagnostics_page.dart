import 'dart:io';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:simple_live_app/app/controller/app_settings_controller.dart';
import 'package:simple_live_app/app/utils.dart';
import 'package:simple_live_app/services/car_window_service.dart';
import 'package:simple_live_app/widgets/settings/settings_card.dart';

class _TouchSample {
  const _TouchSample({
    required this.timestamp,
    required this.target,
    required this.expected,
    required this.actual,
    required this.global,
    required this.areaSize,
    required this.pointerKind,
  });

  final DateTime timestamp;
  final String target;
  final Offset expected;
  final Offset actual;
  final Offset global;
  final Size areaSize;
  final String pointerKind;

  String get reportLine {
    final delta = actual - expected;
    return '${timestamp.toIso8601String()} target=$target, '
        'area=${areaSize.width.toStringAsFixed(1)}x'
        '${areaSize.height.toStringAsFixed(1)}dp, '
        'expected=${expected.dx.toStringAsFixed(1)}/'
        '${expected.dy.toStringAsFixed(1)}, '
        'actual=${actual.dx.toStringAsFixed(1)}/'
        '${actual.dy.toStringAsFixed(1)}, '
        'global=${global.dx.toStringAsFixed(1)}/'
        '${global.dy.toStringAsFixed(1)}, '
        'delta=${delta.dx.toStringAsFixed(1)}/'
        '${delta.dy.toStringAsFixed(1)}dp, kind=$pointerKind';
  }
}

class CarDiagnosticsPage extends StatefulWidget {
  const CarDiagnosticsPage({super.key});

  @override
  State<CarDiagnosticsPage> createState() => _CarDiagnosticsPageState();
}

class _CarDiagnosticsPageState extends State<CarDiagnosticsPage> {
  Map<Object?, Object?> _nativeData = const {};
  Map<String, Object?> _deviceData = const {};
  List<ConnectivityResult> _connectivity = const [];
  List<String> _errors = const [];
  final List<_TouchSample> _touchSamples = [];
  DateTime? _collectedAt;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    if (mounted) {
      setState(() => _loading = true);
    }

    final errors = <String>[];
    Map<Object?, Object?> nativeData = const {};
    Map<String, Object?> deviceData = const {};
    List<ConnectivityResult> connectivity = const [];

    try {
      nativeData = await CarWindowService.getDiagnostics();
      if (Platform.isAndroid && nativeData.isEmpty) {
        errors.add('Android 原生诊断通道未返回数据');
      }
    } catch (error) {
      errors.add('读取原生窗口信息失败：$error');
    }

    try {
      if (Platform.isAndroid) {
        final info = await DeviceInfoPlugin().androidInfo;
        final data = info.data;
        final version = data['version'] is Map
            ? data['version'] as Map<Object?, Object?>
            : const <Object?, Object?>{};
        deviceData = {
          'manufacturer': data['manufacturer'],
          'brand': data['brand'],
          'model': data['model'],
          'device': data['device'],
          'product': data['product'],
          'hardware': data['hardware'],
          'board': data['board'],
          'androidRelease': version['release'],
          'sdkInt': version['sdkInt'],
          'securityPatch': version['securityPatch'],
          'buildDisplay': data['display'],
          'buildFingerprint': data['fingerprint'],
          'supportedAbis': data['supportedAbis'],
          'isPhysicalDevice': data['isPhysicalDevice'],
        };
      }
    } catch (error) {
      errors.add('读取设备信息失败：$error');
    }

    try {
      connectivity = await Connectivity().checkConnectivity();
    } catch (error) {
      errors.add('读取网络类型失败：$error');
    }

    if (!mounted) return;
    setState(() {
      _nativeData = nativeData;
      _deviceData = deviceData;
      _connectivity = connectivity;
      _errors = errors;
      _collectedAt = DateTime.now();
      _loading = false;
    });
  }

  String _edgeInsets(EdgeInsets insets) =>
      'L=${insets.left.toStringAsFixed(1)}, '
      'T=${insets.top.toStringAsFixed(1)}, '
      'R=${insets.right.toStringAsFixed(1)}, '
      'B=${insets.bottom.toStringAsFixed(1)}';

  String _size(Size size) =>
      '${size.width.toStringAsFixed(1)} x ${size.height.toStringAsFixed(1)}';

  String _value(Object? value) {
    if (value == null || value.toString().isEmpty) return 'unknown';
    if (value is Iterable) return value.join(', ');
    return value.toString();
  }

  Map<String, Offset> _touchTargets(Size size) => {
    '左上': Offset(size.width * 0.12, size.height * 0.20),
    '右上': Offset(size.width * 0.88, size.height * 0.20),
    '中心': Offset(size.width * 0.50, size.height * 0.50),
    '左下': Offset(size.width * 0.12, size.height * 0.80),
    '右下': Offset(size.width * 0.88, size.height * 0.80),
  };

  void _recordTouch(PointerDownEvent event, Size areaSize) {
    final targets = _touchTargets(areaSize);
    final nearest = targets.entries.reduce(
      (current, candidate) =>
          (candidate.value - event.localPosition).distance <
              (current.value - event.localPosition).distance
          ? candidate
          : current,
    );
    setState(() {
      _touchSamples.insert(
        0,
        _TouchSample(
          timestamp: DateTime.now(),
          target: nearest.key,
          expected: nearest.value,
          actual: event.localPosition,
          global: event.position,
          areaSize: areaSize,
          pointerKind: event.kind.name,
        ),
      );
      if (_touchSamples.length > 20) {
        _touchSamples.removeRange(20, _touchSamples.length);
      }
    });
  }

  Widget _buildTouchTest() {
    const areaHeight = 220.0;
    return SettingsCard(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    '触控偏移测试',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                TextButton(
                  onPressed: _touchSamples.isEmpty
                      ? null
                      : () => setState(_touchSamples.clear),
                  child: const Text('清空'),
                ),
              ],
            ),
            const Text('依次点击五个十字中心。诊断报告会记录落点与目标的偏差。'),
            const SizedBox(height: 8),
            LayoutBuilder(
              builder: (context, constraints) {
                final areaSize = Size(constraints.maxWidth, areaHeight);
                final targets = _touchTargets(areaSize);
                final latest = _touchSamples.isEmpty
                    ? null
                    : _touchSamples.first;
                return Listener(
                  behavior: HitTestBehavior.opaque,
                  onPointerDown: (event) => _recordTouch(event, areaSize),
                  child: Container(
                    width: areaSize.width,
                    height: areaSize.height,
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surfaceContainer,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Theme.of(context).dividerColor),
                    ),
                    child: Stack(
                      children: [
                        for (final target in targets.entries)
                          Positioned(
                            left: target.value.dx - 24,
                            top: target.value.dy - 24,
                            width: 48,
                            height: 48,
                            child: IgnorePointer(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  const Icon(
                                    Icons.add_circle_outline,
                                    size: 28,
                                  ),
                                  Text(
                                    target.key,
                                    style: const TextStyle(fontSize: 10),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        if (latest != null)
                          Positioned(
                            left: latest.actual.dx - 8,
                            top: latest.actual.dy - 8,
                            child: const IgnorePointer(
                              child: Icon(
                                Icons.circle,
                                size: 16,
                                color: Colors.red,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                );
              },
            ),
            const SizedBox(height: 8),
            Text(
              _touchSamples.isEmpty
                  ? '尚未记录触控点'
                  : '已记录 ${_touchSamples.length} 个点；红点为最近一次落点。',
            ),
          ],
        ),
      ),
    );
  }

  String _buildReport(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final view = View.of(context);
    final rawMediaQuery = MediaQueryData.fromView(view);
    final settings = AppSettingsController.instance;
    final nativeEntries =
        _nativeData.entries.where((entry) => entry.key is String).toList()
          ..sort((a, b) => a.key.toString().compareTo(b.key.toString()));
    final deviceEntries = _deviceData.entries.toList()
      ..sort((a, b) => a.key.compareTo(b.key));
    final scaleModeNames = <int, String>{
      0: '适应',
      1: '拉伸',
      2: '铺满',
      3: '16:9',
      4: '4:3',
    };
    final displayFeatures = rawMediaQuery.displayFeatures.isEmpty
        ? 'none'
        : rawMediaQuery.displayFeatures
              .map(
                (feature) =>
                    '${feature.runtimeType}(bounds=${feature.bounds}, '
                    'state=${feature.state})',
              )
              .join('; ');
    final textScale = mediaQuery.textScaler.scale(14) / 14;
    final connectivityText = _connectivity.isEmpty
        ? 'unknown'
        : _connectivity
              .map((item) => item.toString().split('.').last)
              .join(', ');

    final buffer = StringBuffer()
      ..writeln('Simple Live 设备与窗口诊断')
      ..writeln('reportSchema=1')
      ..writeln('collectedAt=${_collectedAt?.toIso8601String() ?? 'unknown'}')
      ..writeln('说明：不包含账号、Cookie、直播地址、IP、SSID或设备序列号。')
      ..writeln()
      ..writeln('[App]')
      ..writeln(
        'version=${Utils.packageInfo.version}+${Utils.packageInfo.buildNumber}',
      )
      ..writeln('packageName=${Utils.packageInfo.packageName}')
      ..writeln(
        'buildMode=${kReleaseMode ? 'release' : (kProfileMode ? 'profile' : 'debug')}',
      )
      ..writeln('platform=${Platform.operatingSystem}')
      ..writeln('platformVersion=${Platform.operatingSystemVersion}')
      ..writeln('locale=${Platform.localeName}')
      ..writeln('network=$connectivityText')
      ..writeln()
      ..writeln('[Device]');

    if (deviceEntries.isEmpty) {
      buffer.writeln('unavailable');
    } else {
      for (final entry in deviceEntries) {
        buffer.writeln('${entry.key}=${_value(entry.value)}');
      }
    }

    buffer
      ..writeln()
      ..writeln('[Flutter window]')
      ..writeln('rawLogicalSizeDp=${_size(rawMediaQuery.size)}')
      ..writeln('viewPhysicalSizePx=${_size(view.physicalSize)}')
      ..writeln(
        'devicePixelRatio=${rawMediaQuery.devicePixelRatio.toStringAsFixed(3)}',
      )
      ..writeln('orientation=${rawMediaQuery.orientation.name}')
      ..writeln('rawPaddingDp=${_edgeInsets(rawMediaQuery.padding)}')
      ..writeln('rawViewPaddingDp=${_edgeInsets(rawMediaQuery.viewPadding)}')
      ..writeln('rawViewInsetsDp=${_edgeInsets(rawMediaQuery.viewInsets)}')
      ..writeln(
        'rawSystemGestureInsetsDp='
        '${_edgeInsets(rawMediaQuery.systemGestureInsets)}',
      )
      ..writeln('effectiveLogicalSizeDp=${_size(mediaQuery.size)}')
      ..writeln('effectivePaddingDp=${_edgeInsets(mediaQuery.padding)}')
      ..writeln('effectiveViewPaddingDp=${_edgeInsets(mediaQuery.viewPadding)}')
      ..writeln('textScale=${textScale.toStringAsFixed(3)}')
      ..writeln('platformBrightness=${mediaQuery.platformBrightness.name}')
      ..writeln('navigationMode=${mediaQuery.navigationMode.name}')
      ..writeln('accessibleNavigation=${mediaQuery.accessibleNavigation}')
      ..writeln('displayFeatures=$displayFeatures')
      ..writeln()
      ..writeln('[Touch test]');

    if (_touchSamples.isEmpty) {
      buffer.writeln('none');
    } else {
      for (final sample in _touchSamples) {
        buffer.writeln(sample.reportLine);
      }
    }

    buffer
      ..writeln()
      ..writeln('[App settings]')
      ..writeln('carMode=${settings.carMode.value}')
      ..writeln('carTopSafePaddingDp=${settings.carTopSafePadding.value}')
      ..writeln('carBottomSafePaddingDp=${settings.carBottomSafePadding.value}')
      ..writeln('hardwareDecode=${settings.hardwareDecode.value}')
      ..writeln('playerCompatMode=${settings.playerCompatMode.value}')
      ..writeln('playerAutoPause=${settings.playerAutoPause.value}')
      ..writeln('autoFullScreen=${settings.autoFullScreen.value}')
      ..writeln(
        'scaleMode=${settings.scaleMode.value} '
        '(${scaleModeNames[settings.scaleMode.value] ?? 'unknown'})',
      )
      ..writeln()
      ..writeln('[Recent player/window events]');

    if (CarWindowService.recentEvents.isEmpty) {
      buffer.writeln('none (本次启动尚未发生播放器全屏或窗口切换)');
    } else {
      for (final event in CarWindowService.recentEvents) {
        buffer.writeln(event.reportLine);
      }
    }

    buffer
      ..writeln()
      ..writeln('[Android native raw]');
    if (nativeEntries.isEmpty) {
      buffer.writeln('unavailable');
    } else {
      for (final entry in nativeEntries) {
        buffer.writeln('${entry.key}=${_value(entry.value)}');
      }
    }

    if (_errors.isNotEmpty) {
      buffer
        ..writeln()
        ..writeln('[Collection errors]');
      for (final error in _errors) {
        buffer.writeln(error);
      }
    }

    return buffer.toString().trimRight();
  }

  @override
  Widget build(BuildContext context) {
    final report = _buildReport(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('设备与窗口诊断'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: _loading ? null : _refresh,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: '复制全部',
            onPressed: _loading ? null : () => Utils.copyToClipboard(report),
            icon: const Icon(Icons.copy_all_outlined),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(12),
          children: [
            const SettingsCard(
              child: ListTile(
                leading: Icon(Icons.privacy_tip_outlined),
                title: Text('用于手机和车机适配'),
                subtitle: Text(
                  '请在出现问题的窗口状态下刷新并复制。建议分别采集分屏、'
                  '车机整屏和播放器全屏状态；报告不包含账号和播放地址。',
                ),
              ),
            ),
            const SizedBox(height: 12),
            _buildTouchTest(),
            const SizedBox(height: 12),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 32),
                child: Center(child: CircularProgressIndicator()),
              )
            else
              SettingsCard(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: SelectableText(
                    report,
                    style: const TextStyle(
                      fontFamily: 'monospace',
                      fontSize: 12,
                      height: 1.45,
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _loading ? null : () => Utils.copyToClipboard(report),
              icon: const Icon(Icons.copy_all_outlined),
              label: const Text('复制全部诊断信息'),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(56),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
