# Pet Desktop 项目架构文档

## 项目概述

基于Android的桌面宠物应用，融合多种算法实现智能交互、情感计算、路径规划等功能。

## 架构设计

### 整体架构层次

```
┌─────────────────────────────────────────┐
│           App Layer (应用层)            │
│  MainActivity, PetApplication           │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│        Pet Layer (功能层)               │
│  pet-float, pet-render, pet-behavior,  │
│  pet-service                           │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      Algorithm Layer (算法层)          │
│  algorithm-rl, algorithm-sentiment,   │
│  algorithm-cv, algorithm-path,          │
│  algorithm-prediction                  │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│        Core Layer (核心层)             │
│  core-common, core-domain, core-data,  │
│  core-eventbus                         │
└─────────────────────────────────────────┘
```

## 模块说明

### Core 模块组

#### core-common
- **功能**: 公共基础库
- **包含**: 常量、工具类、日志、结果封装
- **关键类**:
  - `PetLogger`: 日志工具
  - `Result`: 结果封装（Success/Error）
  - `DensityUtils`, `ThreadUtils`: 工具类

#### core-domain
- **功能**: 领域层（业务模型和接口）
- **包含**: 数据模型、仓储接口、用例、事件定义
- **关键类**:
  - `PetState`, `PetPosition`, `BehaviorState`, `AnimationState`: 领域模型
  - `IPetRepository`: 仓储接口
  - `CheckPermissionsUseCase`: 权限检查用例
  - `UserInteractionEvent`, `PetBehaviorFeedbackEvent`: 事件定义

#### core-data
- **功能**: 数据层（数据持久化）
- **包含**: Preferences实现、Repository实现
- **关键类**:
  - `PetPreferences`: SharedPreferences封装
  - `PetRepository`: 数据仓储实现

#### core-eventbus
- **功能**: 事件总线（模块间通信）
- **关键类**:
  - `PetEventBus`: 轻量级事件总线实现

### Algorithm 模块组

#### algorithm-rl
- **功能**: 强化学习模块（Q-Learning行为自适应）
- **关键类**:
  - `PetRLAgent`: Q-Learning算法实现
  - `RLBehaviorManager`: RL行为管理器
- **算法**: Q-Learning（状态离散化，轻量化设计）

#### algorithm-sentiment
- **功能**: 情感计算模块
- **关键类**:
  - `TextSentimentAnalyzer`: 文本情感分析
  - `BehaviorEmotionAnalyzer`: 行为情绪推断
- **算法**: 关键词匹配 + 规则推断（可扩展为MobileBERT）

#### algorithm-cv
- **功能**: 计算机视觉模块（手势识别）
- **关键类**:
  - `GestureDetector`: 手势检测器
- **算法**: MediaPipe Hands（预留接口）

#### algorithm-path
- **功能**: 路径规划模块（A*避障算法）
- **关键类**:
  - `PathPlanner`: A*路径规划实现
- **算法**: A*算法 + 贝塞尔曲线平滑

#### algorithm-prediction
- **功能**: 行为预测模块（LSTM时序预测）
- **关键类**:
  - `BehaviorPredictor`: 行为预测器
- **算法**: LSTM（简化实现，可扩展为TensorFlow Lite模型）

### Pet 模块组

#### pet-float
- **功能**: 悬浮窗模块
- **关键类**:
  - `PetFloatView`: 悬浮窗视图
  - `PetFloatManager`: 悬浮窗管理器

#### pet-render
- **功能**: 动画渲染模块
- **关键类**:
  - `PetAnimationView`: 动画视图

#### pet-behavior
- **功能**: 行为系统模块（集成RL算法）
- **关键类**:
  - `PetBehaviorStateMachine`: 行为状态机

#### pet-service
- **功能**: 前台服务模块（整合所有模块）
- **关键类**:
  - `PetForegroundService`: 前台服务
  - `ServiceLifecycleCoordinator`: 服务生命周期协调器
  - `PetServiceManager`: 服务管理器

## 数据流

### 用户交互流程

```
用户交互
  ↓
PetFloatView (触摸事件)
  ↓
PetBehaviorStateMachine (状态机)
  ↓
RLBehaviorManager (RL算法决策)
  ↓
PetRLAgent (Q-Learning选择动作)
  ↓
PetAnimationView (渲染动画)
```

### 算法协同流程

```
ServiceLifecycleCoordinator
  ├─→ RLBehaviorManager (行为决策)
  ├─→ TextSentimentAnalyzer (文本情感)
  ├─→ BehaviorEmotionAnalyzer (行为情绪)
  ├─→ BehaviorPredictor (行为预测)
  └─→ PathPlanner (路径规划)
```

## 依赖关系

### 模块依赖图

```
app
  ├─→ core-common
  ├─→ core-domain
  ├─→ core-data
  ├─→ core-eventbus
  ├─→ pet-float
  ├─→ pet-render
  ├─→ pet-behavior
  ├─→ pet-service
  └─→ algorithm-* (所有算法模块)

pet-service
  ├─→ core-*
  ├─→ pet-*
  └─→ algorithm-*

pet-behavior
  ├─→ core-*
  ├─→ pet-render
  └─→ algorithm-rl

pet-float
  ├─→ core-*
  ├─→ pet-render
  └─→ algorithm-path
```

## 关键技术点

### 1. 强化学习（Q-Learning）
- **状态空间**: 离散化用户交互特征（点击频率、交互间隔、时段、情绪）
- **动作空间**: 5种基础动作（静止、移动、摇尾巴、弹窗、音效）
- **奖励机制**: 基于用户反馈（正面+10，负面-20）
- **优化**: 状态离散化减少计算量，Q表本地持久化

### 2. 情感计算
- **文本分析**: 关键词匹配（可扩展为MobileBERT）
- **行为推断**: 基于使用统计（解锁频率、应用使用时长等）
- **情绪映射**: 情绪值（0-10）映射到宠物行为

### 3. 路径规划
- **A*算法**: 网格化桌面环境，实时避障
- **路径平滑**: 贝塞尔曲线插值
- **碰撞检测**: 实时检测与桌面图标碰撞

### 4. 行为预测
- **时序分析**: 基于历史行为序列
- **模式识别**: 同时间段行为模式匹配
- **预测输出**: 未来1小时内的行为预测

## 性能优化

### 1. 算法轻量化
- Q-Learning替代DQN（减少计算量）
- 状态离散化（减少状态空间）
- 帧采样（CV模块每3帧处理1次）

### 2. 资源管理
- 模型按需加载
- 非交互时段降低计算频率
- 摄像头非活跃时关闭

### 3. 内存优化
- 及时回收图片/帧数据
- Q表本地持久化
- 事件总线使用CopyOnWriteArrayList

## 扩展性

### 1. 算法模块可插拔
- 各算法模块独立，可单独替换
- 接口标准化，便于扩展

### 2. 模型可升级
- 文本情感分析可替换为MobileBERT
- 行为预测可替换为LSTM-TFLite模型
- 手势识别可集成MediaPipe

### 3. 功能模块化
- 各pet模块职责清晰，易于维护
- 事件总线支持松耦合通信

## 待完善功能

1. **动画资源**: 需要添加实际的动画资源文件
2. **MediaPipe集成**: CV模块需要实际集成MediaPipe Hands
3. **TensorFlow Lite模型**: 情感分析和行为预测需要实际的TFLite模型
4. **桌面图标检测**: 路径规划需要AccessibilityService获取桌面图标位置
5. **权限引导**: 需要完善权限申请的用户引导流程

## 论文价值点

1. **多算法协同**: 融合RL、情感计算、CV、路径规划等多种算法
2. **移动端优化**: 针对Android设备算力限制的轻量化设计
3. **自适应交互**: 基于强化学习的个性化行为进化
4. **工程实践**: 完整的模块化架构和代码实现

