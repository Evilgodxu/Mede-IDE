# 目录管理规范

## 目录结构

### 标准结构（适用于有网络请求的应用）

```
app/src/main/kotlin/com/company/app/
├── data/                      # 全局共享数据层（非页面专属，如公共 API、全局 Setting）
│   ├── repository/            # 仓库接口与实现（对外暴露）
│   │   ├── {Feature}Repository.kt       # 仓库接口
│   │   └── {Feature}RepositoryImpl.kt   # 仓库实现
│   └── source/                # 数据源（仅repository内部使用）
│       ├── local/             # 本地数据源（Room、DataStore）
│       │   ├── {Feature}LocalDataSource.kt
│       │   └── {Feature}Dao.kt
│       └── remote/            # 远程数据源（Retrofit、Firebase）
│           ├── {Feature}RemoteDataSource.kt
│           └── {Feature}Api.kt
├── model/                     # 全局共享领域模型（非页面专属）
├── di/                        # 依赖注入模块
├── navigation/                # 导航配置
├── screens/                   # UI 页面层（页面即模块，内容全内聚）
│   └── {page}/                # 页面模块（如 home/、detail/）
│       ├── layouts/           # 布局变体（按窗口尺寸类别细分）
│       │   ├── compact/       # 竖屏/紧凑单栏布局
│       │   │   ├── {Page}CompactScreen.kt   # 组装入口 —— 平铺可见
│       │   │   ├── topbar/                  # 该布局专属的功能子模块
│       │   │   └── usercard/
│       │   ├── medium/        # 横屏/中等宽度布局
│       │   │   ├── {Page}MediumScreen.kt
│       │   │   ├── topbar/
│       │   │   └── sidebar/
│       │   └── expanded/      # 宽屏多栏布局
│       │       ├── {Page}ExpandedScreen.kt
│       │       └── sidebar/
│       ├── searchbar/         # 跨布局共享功能子模块（被多个 layout 复用）
│       │   ├── SearchBar.kt
│       │   └── SearchBarViewModel.kt
│       ├── data/              # 页面专属数据层
│       │   └── {Page}Repository.kt
│       ├── model/             # 页面专属领域模型
│       ├── service/           # 页面后台服务（如前台 Service 或浮动 UI 管理）
│       │   └── {Page}Service.kt
│       ├── logic/             # 页面业务逻辑
│       │   ├── usecases/      # 用例/业务操作
│       │   ├── mappers/       # 数据映射
│       │   ├── validators/    # 输入验证
│       │   ├── extensions/    # 扩展函数
│       │   └── utils/         # 工具类
│       ├── {Page}Screen.kt   # 入口（根据窗口尺寸分派到对应 layout）
│       ├── {Page}UiState.kt
│       └── {Page}ViewModel.kt
├── ui/                        # 全局通用资源（跨页面复用，无业务绑定）
│   ├── theme/                 # 主题配置（颜色、字体、形状）
│   ├── adaptive/              # 自适应布局工具
│   └── {sharedFeature}/       # 全局可复用组件（如 custombutton/、loadingindicator/）
├── MainActivity.kt
└── MyApplication.kt
```

### 单机离线应用结构（无网络请求）

```
app/src/main/kotlin/com/company/app/
├── data/                      # 全局共享数据层
├── model/                     # 全局共享领域模型
├── di/                        # 依赖注入模块
├── navigation/                # 导航配置
├── screens/                   # UI 页面层（页面即模块，内容全内聚）
│   └── {page}/                # 页面模块
│       ├── layouts/           # 布局变体
│       │   ├── compact/
│       │   │   ├── {Page}CompactScreen.kt
│       │   │   └── {feature}/
│       │   ├── medium/
│       │   └── expanded/
│       ├── {feature}/         # 跨布局共享功能子模块
│       ├── data/
│       ├── model/
│       ├── service/
│       ├── logic/
│       ├── {Page}Screen.kt
│       ├── {Page}UiState.kt
│       └── {Page}ViewModel.kt
├── ui/                        # 全局通用资源
├── MainActivity.kt
└── MyApplication.kt
```

## 核心原则

- **页面即模块**：所有页面专属的数据、模型、组件、业务逻辑全内聚在 `screens/{page}/` 下，不在顶层目录创建
- **布局显式化**：每种布局变体（compact / medium / expanded）在 `layouts/` 下独立目录，各自拥有组装入口和功能子模块
- **组件即子模块**：每个功能组件拥有独立子目录，内含完整实现；无 `components/` 容器目录，父目录平铺的文件即为组装入口
- **顶层仅共享**：顶层 `data/`、`model/`、`ui/` 仅存放被多个页面复用的内容
- **解耦优先**：每个功能独立成模块，由上层组装而非包含，避免改动误触其他功能

## 布局 vs 共享 划分原则

`screens/{page}/` 下存在两种位置来放置功能子模块：

| 位置 | 适用场景 | 示例 |
|------|---------|------|
| `layouts/compact/topbar/` | 该布局**独有**的功能，其他布局不需要 | 竖屏顶部工具栏 |
| `screens/{page}/searchbar/` | **跨布局共享**的功能，两个以上布局使用 | 搜索栏在 compact 和 expanded 中都有 |

判断规则：如果一个功能模块被两个或以上布局引用，提升到页面级别 `screens/{page}/` 下。否则放在对应 `layouts/{layout}/` 内。

## 命名规范

### 目录命名
- 全小写，多单词直接连接（如 `searchbar/`、`quickactions/`、`topbar/`）
- 单数形式，除非语义上必须为复数

### 文件命名

| 类型 | 规则 | 示例 |
|------|------|------|
| 页面入口 Screen | `{Page}Screen.kt` | `HomeScreen.kt` |
| 布局 Screen | `{Page}{Layout}Screen.kt` | `HomeCompactScreen.kt` |
| ViewModel | `{Page}ViewModel.kt` | `HomeViewModel.kt` |
| UiState | `{Page}UiState.kt` | `HomeUiState.kt` |
| 功能模块目录 | `{feature}/` | `topbar/` |
| 模块主组件 | `{Feature}.kt` | `TopBar.kt` |
| 模块子组件 | `{Feature}{部件}.kt` | `TopBarSearchInput.kt` |
| 模块内工具 | `{Feature}Extensions.kt` | `TopBarExtensions.kt` |
| Repository（页面级） | `{Page}Repository.kt` | `HomeRepository.kt` |
| Repository（全局级） | `{Feature}Repository.kt` | `UserRepository.kt` |
| DataSource | `{Feature}DataSource.kt` | `LocalUserDataSource.kt` |
| UseCase | `{动词}{名词}UseCase.kt` | `GetUserUseCase.kt` |
| Mapper | `{源}To{目标}Mapper.kt` | `DtoToEntityMapper.kt` |
| Validator | `{功能}Validator.kt` | `EmailValidator.kt` |
| Extension | `{类型}Extensions.kt` | `StringExtensions.kt` |

## 拆分规范

### 核心理念：组装而非包含

每个功能模块都是可独立提取的单元，上层目录仅负责组装引入，不直接持有实现。

在任何目录层级下：
- **平铺的 .kt 文件** = 组装入口 / 对外暴露的接口
- **子目录** = 完整的功能模块（所有实现细节内聚其中）

浏览目录时，一眼即可辨别哪些是组装，哪些是独立功能模块。

### 功能子目录规则

**每个功能组件必须拥有独立子目录**，无论其行数多少或复杂度高低。不允许在任何 UI 目录下平铺组件文件。

```
# 正确：每个功能独立子目录
layouts/compact/
├── HomeCompactScreen.kt         # 组装入口
├── topbar/                      # 完整功能模块
│   ├── TopBar.kt
│   └── TopBarSearchInput.kt
└── usercard/                    # 完整功能模块
    ├── UserCard.kt
    └── UserCardSection.kt

# 错误：组件平铺在父目录
layouts/compact/
├── HomeCompactScreen.kt
├── TopBar.kt                    # 不应平铺
└── UserCard.kt                  # 不应平铺
```

### 嵌套示例

功能模块内部可以继续按职责拆分子文件，但通常不需要额外一层子目录：

```
usercard/
├── UserCard.kt                  # 主组件（模块入口）
├── UserCardHeader.kt            # 子区域实现
├── UserCardBody.kt              # 子区域实现
└── UserCardExtensions.kt        # 模块内工具 / 扩展

topbar/
├── TopBar.kt                    # 主组件
├── TopBarSearchInput.kt         # 搜索输入框
└── TopBarActionButton.kt        # 操作按钮
```

### 工具集特殊规则

当一个模块内包含**数个平级子工具**（如工具栏内含钢笔、橡皮擦、形状等多种工具），每个子工具独立为子目录：

```
toolbar/
├── ToolBar.kt                   # 组装入口 —— 组装所有子工具
├── pentool/                     # 钢笔工具
│   ├── PenTool.kt
│   └── PenToolConfig.kt
├── erasertool/                  # 橡皮擦工具
│   ├── EraserTool.kt
│   └── EraserToolConfig.kt
└── shapetool/                   # 形状工具
    ├── ShapeTool.kt
    └── ShapeToolRect.kt
```

关键区别：子工具是**平级独立功能**（可独立提取），而非主组件的子区域（不可独立提取）。判断标准——"这个文件离开当前模块能否独立存在"，能则独立为子目录。

### business logic 目录（usecases / mappers / validators / extensions / utils）

`logic/` 下按类型划分的目录（usecases/、mappers/ 等）**不适用**功能子目录规则。这些目录直接存放对应类型的 .kt 文件：

```
logic/
├── usecases/
│   ├── GetUserUseCase.kt
│   └── UpdateProfileUseCase.kt
├── mappers/
│   ├── DtoToEntityMapper.kt
│   └── EntityToVoMapper.kt
├── validators/
│   └── EmailValidator.kt
├── extensions/
│   └── StringExtensions.kt
└── utils/
    └── DateUtils.kt
```

这些是工具函数集合，不是 UI 功能组件，不需要独立子目录。

### 无 components/ 容器目录

不创建 `components/`、`common/`、`shared/` 等无意义的容器目录。功能子模块直接挂在所属父目录下。若确实需要对环境做逻辑分组（如按功能域分类），使用语义化命名：

```
# 可接受：用语义名表示归属
screens/home/
├── HomeScreen.kt
├── panels/                     # 面板类功能域
│   ├── statspanel/
│   └── settingpanel/
└── inputs/                     # 输入类功能域
    ├── searchbar/
    └── filterbar/
```

### service/ 目录说明

`screens/{page}/service/` 存放页面后台服务类，如 Android 前台 Service 或需要独立生命周期的浮动 UI 管理器：

```
service/
├── {Page}Service.kt            # Service 主类
└── {Feature}/                  # 复杂功能子模块
    ├── {Feature}Manager.kt
    └── {Feature}Overlay.kt
```

功能子模块规则同样适用于 service/ 内部：平级子功能用子目录。

### ui/ 目录说明

顶层 `ui/` 存放跨页面复用的全局资源，无业务绑定：

```
ui/
├── theme/                      # 主题配置
│   ├── Theme.kt
│   ├── Color.kt
│   └── Type.kt
├── adaptive/                   # 自适应布局工具
│   └── AdaptiveScaffold.kt
└── {sharedFeature}/            # 全局可复用组件（按功能独立目录）
    ├── {Feature}.kt
    └── {Feature}Section.kt
```

这里的组件同样遵循"每个功能独立子目录"规则。
