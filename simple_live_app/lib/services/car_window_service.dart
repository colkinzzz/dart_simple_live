import 'dart:io';

import 'package:flutter/services.dart';

/// State of the window owned by the vehicle launcher.
///
/// This is intentionally separate from the player's in-app fullscreen state.
/// Unknown is the safe value: callers must not hide system bars for it.
class CarWindowState {
  const CarWindowState({
    required this.supported,
    required this.isAutomotive,
    required this.isInMultiWindowMode,
    required this.hostWindowState,
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
  final String hostWindowState;
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
    hostWindowState: 'unknown',
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

    final rawState = map['hostWindowState'] as String?;
    final hostWindowState = switch (rawState) {
      'split' => 'split',
      'full' => 'full',
      _ when boolean('isInMultiWindowMode') => 'split',
      _ => 'unknown',
    };
    return CarWindowState(
      supported: true,
      isAutomotive: boolean('isAutomotive'),
      isInMultiWindowMode: boolean('isInMultiWindowMode'),
      hostWindowState: hostWindowState,
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
}

class CarWindowService {
  static const MethodChannel _channel = MethodChannel(
    'com.xycz.simple_live/car_window',
  );

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
