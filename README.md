# Minis-Mod

基于 [OpenMinis/OpenMinis](https://github.com/OpenMinis/OpenMinis) **1.14** 的个人修改版。
实现了 5 项此前未实现的功能改动，并把包名改为 `com.openminis.app.mine`，
使它可以与官方版**同时安装、并存运行**。

> **许可证：GPL-3.0（继承自上游）。**
> 本仓库是 OpenMinis 的衍生作品，按 GPL-3.0 发布。
> 所有版权归原作者所有；本仓库保留上游的 `LICENSE` 与全部版权声明。
> **本仓库包含对上游源码的修改**，修改点见下方「改动清单」。

---

## 为什么需要这个版本

上游的 5 项能力在 1.14 中尚未实现。这个版本把它们做了出来，并且解决了
「官方版与修改版无法共存」这一硬约束 —— 见下方「多开」一节。

---

## 改动清单

以下每一项都对应一个在上游 issue 追踪中被提出但尚未实现的需求。

### 1. Token 用量面板不再推移内容（#414）

上游的面板插入在正常文档流中：展开一次，下方全部内容被顶走，滚动位置丢失，
长回复读完需要反复上下滑。

本版改为**恒定高度 + alpha 过渡** —— 面板显示与否不改变文档的任何一个字节，
滚动位置天然保持。

> 实现说明：最初尝试过「零高度锚点 + 浮层」和「绝对定位」两种方案，
> 两者在 Compose `LazyColumn` 中均无效 —— LazyColumn 按顺序绘制，
> 零高度行的浮层会被下一条消息覆盖。恒定高度是唯一可靠的解法。

### 2. 工具调用自动折叠（#337）

一轮 agentic 交互可能产生数十张工具调用卡片，把最终答案挤到屏幕之外。

本版把**第一条正文出现之前的所有已结算工具调用**聚合成一行摘要
（如「12 tool calls · 1 failed」），点击可展开原列表。

- 进行中的调用（pending / streaming / running）永远保持平铺 —— 那是用户正在看的
- 设置 → 外观 → 深度思考 → **自动折叠工具调用** 可关闭（默认开启）

### 3. 选项一键填入（#362）

回复结尾常以选项收尾（「用哪个方案？」）。本版在**完整回复末尾**解析出这些选项
并渲染为按钮，点击**填入输入框**（不直接发送，避免误发；二次点击才发送）。

解析是**纯本地**的，不调用任何模型 —— 零延迟、零 token 成本，且按钮内容
必然与模型实际给出的选项一致（经模型复述反而可能走样）。

支持编号、项目符号、字母三种列表格式。代码块与表格内的行首符号是结构而非列表，
会被自动排除。

### 4. 会话未读标记（#359）

会话列表中，对未读会话的图标左上角显示圆点。

- 打开会话即清除
- 判定基于实体自带的 `updatedAt` 与**创建时间**的对比，**不新增数据库列**
  （避免 Room schema 迁移）
- 存储为 SharedPreferences，卸载即清

### 5. 多开：与官方版并存（关键约束）

Linux **抽象 socket 是设备全局唯一的**，与包名无关。上游把 socket 名写死为
`native-offload`，因此第二个实例会在 `onCreate` 阶段直接崩溃（表现为黑屏）。

本版把 socket 名按 `BuildConfig.APPLICATION_ID` 派生（SHA-256 前 4 字节）。
上游代码已将该名字收口到单一常量，4 个调用点全部引用它 —— **只需修改一处**，
沙箱侧自动跟随，原生代码无需改动。

---

## 构建

```sh
git clone --recurse-submodules https://github.com/<you>/Minis-Mod.git
cd Minis-Mod
```

完整说明见上游 [BUILDING.md](BUILDING.md)。

### 依赖资产

以下资产**不在仓库中**（体积大，且其中两项是上游的构建产物，
不宜由本仓库二次分发），需按 BUILDING.md 获取：

| 路径 | 说明 |
|---|---|
| `src/android/app/libs/vad.aar` | 语音 VAD 库（Silero），JitPack `com.github.helloooideeeeea:RealTimeCutVADLibraryForAndroid:1.0.5` |
| `src/android/app/libs/rclone.aar` | 远程备份的 SMB / WebDAV / SFTP / S3，由 `deps/build_rclone_android.sh` 生成（需 `gomobile bind`，仅 x86_64 主机） |
| `src/android/app/src/main/assets/alpine-minirootfs.tar` | 沙箱的 Alpine 根文件系统，由 `deps/prepare_alpine_rootfs.sh` 生成 |
| `src/android/app/src/main/assets/proot-aarch64` | PRoot 可执行文件 |

### 在 aarch64 主机（如手机）上构建

x86_64-only 的工具链（aapt2 / llvm-strip / cmake / ninja / clang）需用
`qemu-x86_64` 配合 glibc sysroot 运行：

```sh
qemu-x86_64 -L <glibc-sysroot> <tool> <args>
```

Kotlin 编译建议设置 `kotlin.compiler.execution.strategy=in-process`，
避免第二个大内存进程被系统回收。

---

## 与上游的关系

- 本仓库基于 `OpenMinis/OpenMinis` 的 tag `0-beta-build30`
  （该 tag 的 `build.gradle.kts` 标记为 1.14 / code 28）
- 保留上游的 `LICENSE`（GPL-3.0）、`THIRD_PARTY_LICENSES.md` 与全部版权声明
- 所有修改在源码中以 `[local #<issue>]` 标注

## 免责声明

本项目与 OpenMinis 官方无关联。按 GPL-3.0「无任何担保」条款，
本软件按现状提供，使用风险自负。
