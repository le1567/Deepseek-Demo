# DeepSeek Demo（Android 套壳版）

把 DeepSeek 官方开源的前端 Demo（`deepseek-ai/chat-demo`）打包成可安装的 Android App。界面与功能与官方 App / 网页端一致，**1:1 复刻**。

> 本项目是一个非官方的技术演示/打包工程。模型能力、前端 Demo 版权归 DeepSeek 所有；套壳部分见根目录 [LICENSE](LICENSE)。

## 功能

全部来自官方 `chat-demo`，非二次魔改：

- 多会话管理（自动标题、重命名、删除、编辑、重新生成、分支切换）
- 流式回复、深度思考、联网搜索 + 引用来源、链接阅读
- 文件 / 图片上传（点击、粘贴、拖拽）、语音输入、朗读回复
- Markdown / 代码高亮 / KaTeX 公式 / Mermaid 图表
- 历史对话导入导出（ZIP）、中英双语、明暗主题

套壳层额外提供：

- 用虚拟 HTTPS 域名 + `shouldInterceptRequest` 托管静态站点，使 localStorage / IndexedDB / getUserMedia 等安全上下文能力可用
- 文件选择、麦克风权限、blob 下载保存到 `下载/DeepSeek/`、返回键、外部链接跳转等 Android 原生桥接
- 崩溃黑匣子（堆栈写入 `Android/data/<包名>/files/crash/crash.log`）

## 手机端交互动效

针对触屏操作补齐了一套轻量动效，全部基于 CSS 合成层（`transform` / `opacity`），不影响列表滚动性能：

- **侧边栏抽屉**：左滑入 + 遮罩淡入，关闭时反向滑出（320ms 缓动），抽屉右缘圆角与投影
- **弹窗 / 设置面板**：遮罩淡入，移动端底部弹层上滑入场
- **触控反馈**：图标按钮、主按钮、会话项、主题选项按下时轻微缩放
- **搜索结果面板**：移动端全屏面板上滑入场
- **附件与欢迎页**：新增附件缩放入场，欢迎页淡入上浮
- **无障碍**：跟随系统「减少动态效果」设置自动关闭动画

## 目录结构

```
.
├── AndroidManifest.xml          # 清单（minSdk 24 / targetSdk 34）
├── src/                         # Java 套壳源码（MainActivity / CrashLog）
├── res/                         # 图标与主题资源
├── assets/site/                 # 官方 chat-demo 构建产物（由 build-web.sh 生成）
├── upstream/chat-demo/          # 官方 chat-demo 原始源码（MIT，完整保留）
├── tools/
│   ├── setup_toolchain.sh       # 一键准备 Android 构建工具链
│   ├── build-web.sh             # 从上游源码重建静态站点
│   ├── build.sh                 # 一键构建 APK（免 Gradle）
│   └── SignApk.java             # apksig 内存签名工具
├── LICENSE                      # 套壳部分 MIT 许可
└── README.md
```

## 构建

### 方式一：使用已构建的站点（最快）

仓库自带 `assets/site/`（官方 demo 的构建产物），直接构建 APK：

```bash
# 1. 准备工具链（首次，或沙箱 /root 被重置后）
bash tools/setup_toolchain.sh

# 2. 构建 APK
bash tools/build.sh
# 产物：dist/DeepSeek-v1.0.0.apk
```

`setup_toolchain.sh` 默认下载到 `/root/atc` 与 `/root/dl`（沙箱路径）。在普通 Linux/macOS 环境请自行准备下列工具并用环境变量指向它们：

| 工具 | 版本 | 环境变量 |
|---|---|---|
| Android build-tools（aapt2 / zipalign / apksigner） | r34 | `BT` |
| Android platform（android.jar） | 35 | `AJ` |
| R8 | 8.3.37 | `R8` |
| apksig | 8.2.2 | `APKSIG` |
| Guava | 31.1-android | `GUAVA` |

示例：

```bash
BT=/path/to/android-sdk/build-tools/34.0.0 \
AJ=/path/to/android-sdk/platforms/android-35/android.jar \
R8=/path/to/r8.jar APKSIG=/path/to/apksig.jar GUAVA=/path/to/guava.jar \
KS_PASS='your-strong-password' \
bash tools/build.sh
```

### 方式二：从上游源码重建站点

```bash
bash tools/build-web.sh   # 需要 Node.js 22.12+ 与 pnpm，产物同步到 assets/site/
bash tools/build.sh
```

### 签名

首次构建会在 `tools/deepseek.keystore` 自动生成密钥库（默认密码 `deepseek123`，仅用于本地演示）。**正式分发请务必通过 `KS_PASS` 设置强密码，并妥善备份 keystore** —— 丢失后无法对已安装用户覆盖升级。keystore 是私钥，不要提交到公开仓库。

## 下载安装

- 已打包 APK：[DeepSeek Demo Releases](https://github.com/le1567/DeepSeek-Demo/releases/latest)
- 支持 Android 7.0+（minSdk 24），安装包约 2 MB

> APK 以演示用签名构建；后续覆盖升级请使用同一 Release 渠道的安装包。

## 使用

安装 APK（Android 7.0+）→ 首次打开在设置中填入自己的 DeepSeek API Key → 开始对话。Key 只保存在本机。API 调用费用按 DeepSeek 官方标准由你的账户承担；联网搜索、语音、文件解析等工具能力由服务端 API 提供。

## 合规提示

- 请遵守 [DeepSeek Open Platform 服务条款](https://api-docs.deepseek.com/)。
- 若对外提供公众服务，请开启 API 的安全校验参数，并自行履行内容安全审核、大模型备案、AI 生成内容标识等义务。
- 本项目为个人技术实践，与 DeepSeek 官方无关，不使用其商标做商业宣传。

## 许可

- 套壳工程（本仓库自有代码）：MIT，见 [LICENSE](LICENSE)。
- 上游 `upstream/chat-demo/`：MIT，Copyright (c) 2026 DeepSeek，见 `upstream/chat-demo/LICENSE`。其构建产物 `assets/site/` 同样适用该许可。
