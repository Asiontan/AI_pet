# Pet Desktop 项目架构文档

## 项目概述

基于 Android 平台的智能桌面宠物应用，融合强化学习（Q-Learning）、情感计算、A* 路径规划、用户行为预测等多种算法，实现宠物的智能交互、自主移动和个性化情感反馈。

---

## 整体架构层次

```
┌──────────────────────────────────────────────────────────┐
│                    App Layer（应用层）                    │
│           MainActivity / PetApplication                  │
│     ModelSwitchActivity / MotionPreviewActivity          │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│                  Pet Layer（功能层）                      │
│  pet-float     ──  悬浮窗显示、触摸交互、自主移动         │
│  pet-render    ──  Live2D 模型渲染（OpenGL ES 3）         │
│  pet-behavior  ──  行为状态机（集成 RL 算法）              │
│  pet-service   ──  前台服务 + 模块协调器                  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│                Algorithm Layer（算法层）                  │
│  algorithm-rl          ──  Q-Learning 强化学习            │
│  algorithm-sentiment   ──  情感计算（文本 + 行为）         │
│  algorithm-path        ──  A* 路径规划                    │
│  algorithm-prediction  ──  用户行为预测                   │
│  algorithm-cv          ──  手势识别（MediaPipe 预留接口）  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│                  Core Layer（核心层）                     │
│  core-common    ──  常量、日志、工具类、Result 封装        │
│  core-domain    ──  领域模型、事件定义、仓储接口、用例     │
│  core-data      ──  数据持久化（SharedPreferences）        │
│  core-eventbus  ──  轻量级事件总线                        │
└──────────────────────────────────────────────────────────┘
```

---

## 模块详细说明

### 自主移动流程
```
triggerAutonomousMove()
  ├─▶ PetFloatManager.getCurrentPosition()  →  当前坐标
  ├─▶ 随机生成目标点（留 150px 边距）
  ├─▶ PathPlanner.findPath(start, end)      →  A* 路径点列表
  ├─▶ PathPlanner.smoothPath(rough)         →  贝塞尔曲线平滑
  └─▶ autonomousMoveLoop()（每 60ms）
        └─▶ PetFloatManager.movePetTo(x, y) →  更新 WindowManager 坐标
```

---

## 性能优化措施

| 优化点 | 手段 |
|--------|------|
| 算法轻量化 | Q-Learning 替代 DQN；状态离散化压缩状态空间至 500 个 |
| 渲染优化 | Live2D 仅在 GLSurfaceView 的 GL 线程操作，避免主线程阻塞 |
| 情绪分析 | 基于 UsageStatsManager 聚合统计，无需实时采集 |
| CV 帧采样 | 手势检测每 3 帧处理 1 次，降低 CPU 开销 |
| 协程调度 | IO 密集操作用 Dispatchers.IO，UI 更新用 Dispatchers.Main |
| Q 表持久化 | ObjectOutputStream 序列化，仅在学习后写盘 |
| 移动步进 | 路径点分帧执行（60ms/步），避免大幅跳跃 |

---

## 扩展性设计

### 算法可替换
- `TextSentimentAnalyzer`：当前关键词匹配，可无缝替换为 MobileBERT TFLite 模型
- `BehaviorPredictor`：当前统计规则，可替换为 LSTM TFLite 时序模型
- `GestureDetector`：预留 MediaPipe Hands 接入接口

### 模型可扩展
- `ModelManager` 支持内置（Assets）和外部（文件系统）两种模型来源
- `Live2DPetView.switchModel()` 运行时热切换模型

### 数据层可扩展
- `IPetRepository` 接口隔离，可替换底层存储（Room 数据库、云同步等）
- `PetPreferences` 已预留情绪值、点击统计、交互时间戳等扩展字段

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示（必须） |
| `FOREGROUND_SERVICE` | 前台服务保活 |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |
| `PACKAGE_USAGE_STATS` | UsageStatsManager 获取 App 使用数据（情绪分析 + 行为预测） |

> **注意**：`PACKAGE_USAGE_STATS` 为特殊权限，需引导用户在「设置 → 有权查看使用情况的应用」中手动授予。

---

## 待扩展功能

1. **MediaPipe 手势识别**：集成摄像头实时手势交互
2. **MobileBERT 文本情感**：替换关键词匹配，提升情感分析准确率
3. **LSTM 行为预测**：替换统计规则，实现更精准的时序预测
4. **AccessibilityService 障碍物检测**：获取桌面图标位置，传入 PathPlanner 实现真实避障
5. **云端数据同步**：Q 表和用户偏好多设备同步
6. **音效系统**：PetAction.PLAY_SOUND 对应音效播放实现
Core 模块组

#### core-common
- `PetLogger`：统一日志工具
- `Result<T>`：Success / Error 结果封装
- `PetConstants`：全局常量（尺寸、时间间隔、Pref Key 等）
- `DensityUtils`、`ThreadUtils`：工具类

#### core-domain
- 领域模型：`PetState`、`PetPosition`、`BehaviorState`、`AnimationState`
- 事件定义：`UserInteractionEvent`（CLICK / DOUBLE_CLICK / LONG_PRESS / DRAG / SWIPE）、`PetBehaviorFeedbackEvent`
- 仓储接口：`IPetRepository`
- 用例：`CheckPermissionsUseCase`（悬浮窗 + 通知权限）

#### core-data
- `PetPreferences`：SharedPreferences 封装，持久化宠物位置、情绪值、今日点击次数、最后交互时间
- `PetRepository`：实现 `IPetRepository`，新增情绪值和点击统计的读写

#### core-eventbus
- `PetEventBus`：基于协程 SharedFlow 的轻量级事件总线

---

### Algorithm 模块组

#### algorithm-rl
- `PetRLAgent`：Q-Learning 算法核心
  - 状态空间：4 维离散化（点击频率 × 交互间隔 × 时段 × 情绪）
  - 动作空间：5 种（IDLE / MOVE / WAG_TAIL / POPUP / PLAY_SOUND）
  - 奖励机制：正反馈 +3~+10，负反馈 -20
  - Q 表本地持久化（ObjectOutputStream）
- `RLBehaviorManager`：RL 管理器
  - `updateEmotion(emotion)`：接收外部情绪分析结果，影响行为决策
  - `handleUserInteraction()`：处理触摸事件，返回推荐 `BehaviorState`
  - `updatePeriodic()`：无交互时自主行为决策；夜间（22:00–07:00）自动返回 SLEEP

#### algorithm-sentiment
- `BehaviorEmotionAnalyzer`：基于 `UsageStatsManager` 的情绪推断
  - 分析最近 1 小时的 App 切换次数、总使用时长、社交类 App 占比、娱乐类 App 占比
  - 返回情绪分 0–10
- `TextSentimentAnalyzer`：关键词匹配文本情感分析，返回积极度 0.0–1.0（可替换为 MobileBERT TFLite）

#### algorithm-path
- `PathPlanner`：A* 路径规划
  - 网格化屏幕（默认 30px 格）
  - `findPath(start, end)`：8 方向 A* 搜索
  - `smoothPath(roughPath)`：贝塞尔曲线平滑
  - `checkCollision(position, size)`：实时碰撞检测
  - `updateObstacles(rects)`：障碍物热更新
  - 暴露 `screenWidth` / `screenHeight` 供协调器读取

#### algorithm-prediction
- `BehaviorPredictor`：基于 `UsageStatsManager` 的行为预测
  - 分析过去 7 天同时段行为，统计最常用 App
  - 返回未来 1 小时内的 `PredictedBehavior`（可替换为 LSTM TFLite）

#### algorithm-cv
- `GestureDetector`：手势识别框架接口（预留 MediaPipe Hands 接入点）
  - 支持：WAVE / THUMBS_UP / FIST / POINT / PEACE
  - 帧采样：每 3 帧处理 1 次，降低 CPU 开销

---

### Pet 模块组

#### pet-float
- `PetFloatView`：悬浮窗视图
  - 触摸事件处理：CLICK（含双击检测，350ms 内第二次点击触发 DOUBLE_CLICK）、LONG_PRESS、DRAG
  - 点击缩放动画（ScaleX/Y 1.0 → 1.15 → 1.0，160ms）
  - 内嵌 `Live2DPetView`，支持模型切换 / 表情 / 动作播放
- `PetFloatManager`：悬浮窗管理器
  - `show()` / `hide()`：WindowManager 动态添加 / 移除
  - `movePetTo(x, y)`：供路径规划自主移动调用
  - `getCurrentPosition()`：返回当前 WindowManager.LayoutParams 坐标
  - `updatePosition(PetPosition)`：恢复上次保存的位置

#### pet-render
- `Live2DPetView`：基于 OpenGL ES 3 + Cubism SDK 的 Live2D 渲染
  - 支持 Asset / External 两种模型源
  - 表情（exp3）持久模式 + 0.3s 过渡动画
  - 动作（motion3）关键帧插值播放
  - 持久参数（Add 模式），用于去水印等永久效果
  - 眨眼（CubismEyeBlink）自动驱动

#### pet-behavior
- `PetBehaviorStateMachine`：行为状态机
  - SLEEP 状态下任意交互自动切换到 WAKE_UP
  - DRAG 交互直接映射为 DRAG 状态
  - 夜间（22:00–07:00）强制 SLEEP；07:00 自动 WAKE_UP
  - 其他状态由 `RLBehaviorManager` Q-Learning 决策

#### pet-service
- `PetForegroundService`：前台服务，整合所有模块，处理模型切换 / 表情 / 动作 Intent
- `ServiceLifecycleCoordinator`：核心调度器
  - 情绪分析（每分钟）→ 同步到 RLBehaviorManager → 驱动状态机
  - RL 决策为 WALK 时，用 A* 规划随机目标路径并驱动悬浮窗移动
  - 自主移动循环：每 60ms 移动一个路径点（平滑动画效果）
  - 用户交互时立即中断自主移动
  - 启动时从 Preferences 恢复情绪值；停止时持久化情绪值
- `PetServiceManager`：服务启动 / 停止封装

---

## 核心数据流

### 用户交互流程
```
用户触摸
  └─▶ PetFloatView（识别 CLICK / DOUBLE_CLICK / LONG_PRESS / DRAG）
        └─▶ ServiceLifecycleCoordinator.handleUserInteraction()
              ├─▶ 中断当前自主移动路径
              └─▶ PetBehaviorStateMachine.handleInteraction()
                    └─▶ RLBehaviorManager.handleUserInteraction()
                          └─▶ PetRLAgent.chooseAction()  →  返回 BehaviorState
```

### 定期更新流程（每分钟）
```
ServiceLifecycleCoordinator.periodicUpdate()
  ├─▶ BehaviorEmotionAnalyzer.analyzeBehaviorEmotion()  →  emotion (0-10)
  ├─▶ RLBehaviorManager.updateEmotion(emotion)           →  注入情绪
  ├─▶ PetBehaviorStateMachine.updatePeriodic()           →  BehaviorState
  │     ├─▶ 夜间 → SLEEP
  │     ├─▶ 早晨 → WAKE_UP
  │     └─▶ 其他 → RLBehaviorManager.updatePeriodic() → Q-Learning 决策
  ├─▶ 若 state == WALK → triggerAutonomousMove()
  │     └─▶ PathPlanner.findPath() + smoothPath()  →  path 点列表
  └─▶ BehaviorPredictor.predictNextHour()               →  行为预测日志
```

### 