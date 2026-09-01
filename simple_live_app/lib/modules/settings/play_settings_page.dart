import 'dart:io';

import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:simple_live_app/app/app_style.dart';
import 'package:simple_live_app/app/controller/app_settings_controller.dart';
import 'package:simple_live_app/widgets/settings/settings_card.dart';
import 'package:simple_live_app/widgets/settings/settings_menu.dart';
import 'package:simple_live_app/widgets/settings/settings_number.dart';
import 'package:simple_live_app/widgets/settings/settings_switch.dart';
import 'package:simple_live_app/services/car_window_service.dart';

class PlaySettingsPage extends GetView<AppSettingsController> {
  const PlaySettingsPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("直播间设置"),
      ),
      body: ListView(
        padding: AppStyle.edgeInsetsA12,
        children: [
          Padding(
            padding: AppStyle.edgeInsetsA12.copyWith(top: 0),
            child: Text(
              "播放器",
              style: Get.textTheme.titleSmall,
            ),
          ),
          SettingsCard(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Obx(
                  () => SettingsSwitch(
                    title: "硬件解码",
                    value: controller.hardwareDecode.value,
                    subtitle: "播放失败可尝试关闭此选项",
                    onChanged: (e) {
                      controller.setHardwareDecode(e);
                    },
                  ),
                ),
                if (Platform.isAndroid) AppStyle.divider,
                Obx(
                  () => Visibility(
                    visible: Platform.isAndroid,
                    child: SettingsSwitch(
                      title: "兼容模式",
                      subtitle: "若播放卡顿可尝试打开此选项",
                      value: controller.playerCompatMode.value,
                      onChanged: (e) {
                        controller.setPlayerCompatMode(e);
                      },
                    ),
                  ),
                ),
                // AppStyle.divider,
                // Obx(
                //   () => SettingsNumber(
                //     title: "缓冲区大小",
                //     subtitle: "若播放卡顿可尝试调高此选项",
                //     value: controller.playerBufferSize.value,
                //     min: 32,
                //     max: 1024,
                //     step: 4,
                //     unit: "MB",
                //     onChanged: (e) {
                //       controller.setPlayerBufferSize(e);
                //     },
                //   ),
                // ),
                AppStyle.divider,
                Obx(
                  () => SettingsSwitch(
                    title: "进入后台自动暂停",
                    value: controller.playerAutoPause.value,
                    onChanged: (e) {
                      controller.setPlayerAutoPause(e);
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsMenu<int>(
                    title: "画面尺寸",
                    value: controller.scaleMode.value,
                    valueMap: const {
                      0: "适应",
                      1: "拉伸",
                      2: "铺满",
                      3: "16:9",
                      4: "4:3",
                    },
                    onChanged: (e) {
                      controller.setScaleMode(e);
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsSwitch(
                    title: "使用HTTPS链接",
                    subtitle: "将http链接替换为https",
                    value: controller.playerForceHttps.value,
                    onChanged: (e) {
                      controller.setPlayerForceHttps(e);
                    },
                  ),
                ),
              ],
            ),
          ),
          if (Platform.isAndroid)
            Padding(
              padding: AppStyle.edgeInsetsA12.copyWith(top: 24),
              child: Text(
                "车机适配",
                style: Get.textTheme.titleSmall,
              ),
            ),
          if (Platform.isAndroid)
            SettingsCard(
              child: Obx(
                () => Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SettingsSwitch(
                      title: "车机模式",
                      subtitle: "应用内全屏只铺满当前窗口，不强制车机退出分屏",
                      value: controller.carMode.value,
                      onChanged: controller.setCarMode,
                    ),
                    if (controller.carMode.value) ...[
                      AppStyle.divider,
                      SettingsNumber(
                        title: "顶部额外安全区",
                        subtitle: "系统栏仍遮挡或无法点击时逐步增加",
                        value: controller.carTopSafePadding.value,
                        min: 0,
                        max: 160,
                        step: 4,
                        unit: "dp",
                        onChanged: controller.setCarTopSafePadding,
                      ),
                      AppStyle.divider,
                      SettingsNumber(
                        title: "底部额外安全区",
                        subtitle: "空调控制条遮挡内容时逐步增加",
                        value: controller.carBottomSafePadding.value,
                        min: 0,
                        max: 160,
                        step: 4,
                        unit: "dp",
                        onChanged: controller.setCarBottomSafePadding,
                      ),
                      AppStyle.divider,
                      ListTile(
                        minTileHeight: 56,
                        title: const Text("检测当前窗口"),
                        subtitle: const Text("查看分屏、窗口尺寸和系统安全区"),
                        trailing: const Icon(Icons.monitor_outlined),
                        onTap: () async {
                          final state =
                              await CarWindowService.getWindowState();
                          if (!context.mounted) return;
                          showDialog<void>(
                            context: context,
                            builder: (dialogContext) => AlertDialog(
                              title: const Text("车机窗口状态"),
                              content: SelectableText(
                                state.supported
                                    ? state.diagnosticText
                                    : "无法读取窗口状态，请重新启动应用后再试。",
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () =>
                                      Navigator.of(dialogContext).pop(),
                                  child: const Text("确定"),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    ],
                  ],
                ),
              ),
            ),
          Padding(
            padding: AppStyle.edgeInsetsA12.copyWith(top: 24),
            child: Text(
              "直播间",
              style: Get.textTheme.titleSmall,
            ),
          ),
          SettingsCard(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Obx(
                  () => SettingsSwitch(
                    title: "进入直播间自动全屏",
                    value: controller.autoFullScreen.value,
                    onChanged: (e) {
                      controller.setAutoFullScreen(e);
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => Visibility(
                    visible: Platform.isAndroid,
                    child: SettingsSwitch(
                      title: "进入小窗隐藏弹幕",
                      value: controller.pipHideDanmu.value,
                      onChanged: (e) {
                        controller.setPIPHideDanmu(e);
                      },
                    ),
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsSwitch(
                    title: "播放器中显示SC",
                    value: controller.playershowSuperChat.value,
                    onChanged: (e) {
                      controller.setPlayerShowSuperChat(e);
                    },
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: AppStyle.edgeInsetsA12.copyWith(top: 24),
            child: Text(
              "清晰度",
              style: Get.textTheme.titleSmall,
            ),
          ),
          SettingsCard(
            child: Column(
              children: [
                Obx(
                  () => SettingsMenu<int>(
                    title: "默认清晰度",
                    value: controller.qualityLevel.value,
                    valueMap: const {
                      0: "最低",
                      1: "中等",
                      2: "最高",
                    },
                    onChanged: (e) {
                      controller.setQualityLevel(e);
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsMenu<int>(
                    title: "数据网络清晰度",
                    value: controller.qualityLevelCellular.value,
                    valueMap: const {
                      0: "最低",
                      1: "中等",
                      2: "最高",
                    },
                    onChanged: (e) {
                      controller.setQualityLevelCellular(e);
                    },
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: AppStyle.edgeInsetsA12.copyWith(top: 24),
            child: Text(
              "聊天区",
              style: Get.textTheme.titleSmall,
            ),
          ),
          SettingsCard(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Obx(
                  () => SettingsNumber(
                    title: "文字大小",
                    value: controller.chatTextSize.value.toInt(),
                    min: 8,
                    max: 36,
                    onChanged: (e) {
                      controller.setChatTextSize(e.toDouble());
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsNumber(
                    title: "上下间隔",
                    value: controller.chatTextGap.value.toInt(),
                    min: 0,
                    max: 12,
                    onChanged: (e) {
                      controller.setChatTextGap(e.toDouble());
                    },
                  ),
                ),
                AppStyle.divider,
                Obx(
                  () => SettingsSwitch(
                    title: "气泡样式",
                    value: controller.chatBubbleStyle.value,
                    onChanged: (e) {
                      controller.setChatBubbleStyle(e);
                    },
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

