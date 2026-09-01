import 'dart:io';

import 'package:flutter/services.dart';

class CarWindowState {
  const CarWindowState({
    required this.supported,
    required this.isAutomotive,
    required this.isInMultiWindowMode,
    required this.isHostFullScreen,
    required this.width,
    required this.height,
    required this.maximumWidth,
    required this.maximumHeight,
    required this.widthRatio,
    required this.insetLeft,
    required this.insetTop,
    required this.insetRight,
    required this.insetBottom,
  });

  final bool supported;
  final bool isAutomotive;
  final bool isInMultiWindowMode;
  final bool isHostFullScreen;
  final double width;
  final double height;
  final double maximumWidth;
  final double maximumHeight;
  final double widthRatio;
  final double insetLeft;
  final double insetTop;
  final double insetRight;
  final double insetBottom;

  factory CarWindowState.unsupported() => const CarWindowState(
    supported: false,
    isAutomotive: false,
    isInMultiWindowMode: false,
    isHostFullScreen: false,
    width: 0,
    height: 0,
    maximumWidth: 0,
    maximumHeight: 0,
    widthRatio: 0,
    insetLeft: 0,
    insetTop: 0,
    insetRight: 0,
    insetBottom: 0,
  );

  factory CarWindowState.fromMap(Map<Object?, Object?> map) {
    bool boolean(String key) => map[key] == true;
    double number(String key) => (map[key] as num?)?.toDouble() ?? 0;

    return CarWindowState(
      supported: true,
      isAutomotive: boolean('isAutomotive'),
      isInMultiWindowMode: boolean('isInMultiWindowMode'),
      isHostFullScreen: boolean('isHostFullScreen'),
      width: number('width'),
      height: number('height'),
      maximumWidth: number('maximumWidth'),
      maximumHeight: number('maximumHeight'),
      widthRatio: number('widthRatio'),
      insetLeft: number('insetLeft'),
      insetTop: number('insetTop'),
      insetRight: number('insetRight'),
      insetBottom: number('insetBottom'),
    );
  }

  String get diagnosticText =>
      '''
Android Automotive：${isAutomotive ? "是" : "否/厂商未声明"}
系统多窗口：${isInMultiWindowMode ? "是" : "否"}
车机当前整屏：${isHostFullScreen ? "是" : "否"}
当前窗口：${width.toStringAsFixed(0)} × ${height.toStringAsFixed(0)} dp
最大窗口：${maximumWidth.toStringAsFixed(0)} × ${maximumHeight.toStringAsFixed(0)} dp
窗口宽度占比：${(widthRatio * 100).toStringAsFixed(0)}%
系统安全区：左 ${insetLeft.toStringAsFixed(0)} / 上 ${insetTop.toStringAsFixed(0)} / 右 ${insetRight.toStringAsFixed(0)} / 下 ${insetBottom.toStringAsFixed(0)} dp''';
}

class CarWindowDiagnosticEvent {
  const CarWindowDiagnosticEvent({
    required this.timestamp,
    required this.event,
    required this.state,
  });

  final DateTime timestamp;
  final String event;
  final CarWindowState state;

  String get reportLine =>
      '${timestamp.toIso8601String()} event=$event, '
      'hostFull=${state.isHostFullScreen}, '
      'multiWindow=${state.isInMultiWindowMode}, '
      'window=${state.width.toStringAsFixed(0)}x'
      '${state.height.toStringAsFixed(0)}dp, '
      'widthRatio=${(state.widthRatio * 100).toStringAsFixed(1)}%, '
      'insets=${state.insetLeft.toStringAsFixed(0)}/'
      '${state.insetTop.toStringAsFixed(0)}/'
      '${state.insetRight.toStringAsFixed(0)}/'
      '${state.insetBottom.toStringAsFixed(0)}dp';
}

class CarWindowService {
  static const MethodChannel _channel = MethodChannel(
    'com.xycz.simple_live/car_window',
  );
  static final List<CarWindowDiagnosticEvent> _recentEvents = [];

  static List<CarWindowDiagnosticEvent> get recentEvents =>
      List.unmodifiable(_recentEvents);

  static void recordEvent(String event, CarWindowState state) {
    _recentEvents.insert(
      0,
      CarWindowDiagnosticEvent(
        timestamp: DateTime.now(),
        event: event,
        state: state,
      ),
    );
    if (_recentEvents.length > 16) {
      _recentEvents.removeRange(16, _recentEvents.length);
    }
  }

  static Future<Map<Object?, Object?>> getDiagnostics() async {
    if (!Platform.isAndroid) {
      return const {};
    }

    try {
      return await _channel.invokeMethod<Map<Object?, Object?>>(
            'getDiagnostics',
          ) ??
          const {};
    } on PlatformException {
      return const {};
    } on MissingPluginException {
      return const {};
    }
  }

  static Future<CarWindowState> getWindowState() async {
    if (!Platform.isAndroid) {
      return CarWindowState.unsupported();
    }

    try {
      final result = await _channel.invokeMethod<Map<Object?, Object?>>(
        'getWindowState',
      );
      if (result == null) {
        return CarWindowState.unsupported();
      }
      return CarWindowState.fromMap(result);
    } on PlatformException {
      return CarWindowState.unsupported();
    } on MissingPluginException {
      return CarWindowState.unsupported();
    }
  }
}
