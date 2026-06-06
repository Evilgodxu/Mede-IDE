# 目录管理规范

## 目录结构

### 标准结构（适用于有网络请求的应用）

```
app/src/main/kotlin/com/company/app/
├── data/                      # 数据层 - Repository作为唯一入口
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
├── model/                     # 领域模型（纯Kotlin，无Android依赖）
│   ├── {Feature}.kt           # 领域实体
│   └── network/               # 网络DTO（仅data层使用）
│       └── {Feature}Dto.kt
├── di/                        # 依赖注入模块
├── navigation/                # 导航配置
├── screens/                   # UI 页面层
│   └── {page}/                # 页面模块（如 home/、detail/）
│       ├── components/        # 页面专属组件（按 UI 类型拆分）
│       │   ├── dialogs/       # 弹窗组件
│       │   ├── bottomsheets/  # 底栏弹窗
│       │   ├── menus/         # 菜单/下拉
│       │   ├── cards/         # 卡片
│       │   ├── lists/         # 列表项
│       │   └── forms/         # 表单输入
│       ├── service/           # 页面后台服务
│       ├── logic/             # 页面业务逻辑
│       │   ├── usecases/      # 用例/业务操作
│       │   ├── mappers/       # 数据映射
│       │   ├── validators/    # 输入验证
│       │   ├── extensions/    # 扩展函数
│       │   └── utils/         # 工具类
│       ├── {Page}Screen.kt
│       ├── {Page}UiState.kt
│       └── {Page}ViewModel.kt
├── ui/                        # 通用 UI 组件
│   ├── components/            # 可复用组件
│   ├── theme/                 # 主题配置
│   └── adaptive/              # 自适应布局
├── MainActivity.kt
└── MyApplication.kt
```

### 单机离线应用结构（无网络请求）

```
app/src/main/kotlin/com/company/app/
├── data/                      # 数据层（按功能模块划分）
│   └── {feature}/             # 功能模块（如 gesture/、settings/）
│       ├── {Feature}Repository.kt      # 仓库接口
│       ├── {Feature}RepositoryImpl.kt  # 仓库实现
│       ├── {Feature}DataSource.kt      # 数据源
│       └── {Feature}DataStore.kt       # DataStore 扩展
├── model/                     # 领域模型（纯Kotlin，无Android依赖）
│   └── {feature}/
│       └── {Feature}.kt                # 领域实体
│   └── app/                   # 应用级数据（如应用列表缓存）
├── di/                        # 依赖注入模块
├── navigation/                # 导航配置
├── screens/                   # UI 页面层（同上）
├── ui/                        # 通用 UI 组件
├── MainActivity.kt
└── MyApplication.kt
```

## 命名规范

### 目录命名
- 全小写，多单词直接连接（如 `bottomsheets/`）
- 单数形式，除非语义上必须为复数

### 文件命名

| 类型 | 规则 | 示例 |
|------|------|------|
| Screen | `{功能}Screen.kt` | `HomeScreen.kt` |
| ViewModel | `{功能}ViewModel.kt` | `HomeViewModel.kt` |
| UiState | `{功能}UiState.kt` | `HomeUiState.kt` |
| 组件 | `{功能}{类型}.kt` | `CreateDialog.kt` |
| 组件目录 | `{功能}/` | `usercard/` |
| UseCase | `{动词}{名词}UseCase.kt` | `GetUserUseCase.kt` |
| Mapper | `{源}To{目标}Mapper.kt` | `DtoToEntityMapper.kt` |
| Validator | `{功能}Validator.kt` | `EmailValidator.kt` |
| Extension | `{类型}Extensions.kt` | `StringExtensions.kt` |
| Repository | `{功能}Repository.kt` | `UserRepository.kt` |
| DataSource | `{功能}DataSource.kt` | `LocalUserDataSource.kt` |

## 拆分规范

### 文件行数限制

| 文件类型 | 建议上限 | 强制拆分阈值 |
|----------|----------|--------------|
| Screen | 300 行 | 400 行 |
| ViewModel | 200 行 | 300 行 |
| 组件 | 150 行 | 200 行 |
| Repository | 200 行 | 300 行 |
| UseCase | 50 行 | 100 行 |
| Extension/Utils | 300 行 | 500 行 |

**拆分原则：**
- 优先按**职责**拆分，而非单纯按行数
- UI 组件拆分到 `components/`，业务逻辑拆分到 `logic/`，工具函数拆分到 `utils/` 或 `extensions/`
- 当文件接近上限时，评估是否职责过多，考虑提取子组件或重构,如无必要,勿增实体.

### 组件拆分目录

**按复杂度拆分（优先）：**
当单个组件超过 200 行或需拆分为多个子文件时，创建同名子目录：

```
components/
└── {component}/               # 子目录与主组件同名（小写）
    ├── {Component}.kt         # 主组件（对外暴露）
    ├── {Component}Section.kt  # 子区域组件
    ├── {Component}Item.kt     # 子项组件
    └── {Component}Utils.kt    # 组件专属工具
```

**示例：**
```
components/
└── usercard/
    ├── UserCard.kt
    ├── UserCardHeader.kt
    ├── UserCardBody.kt
    └── UserCardExtensions.kt
```

**按数量拆分（可选）：**
仅当 `components/` 超过 15 个组件时，按功能域拆分：

```
components/
├── common/                    # 通用基础组件
├── forms/                     # 表单相关
└── navigation/                # 导航相关
```

**不推荐按 UI 类型拆分**（如 `dialogs/`、`cards/`），因同一功能组件可能跨多种 UI 类型。

### Service 功能子模块
当 Service 承载复杂 UI 功能时，在 `service/` 下创建功能子模块：

```
service/
├── {feature}/                 # 功能子模块（如 expandpanel/）
│   ├── {Feature}ViewManager.kt   # 视图管理器
│   ├── {Feature}Overlay.kt       # 根 Compose UI 容器
│   ├── {Feature}Content.kt       # 主内容组件
│   ├── {Feature}Screen.kt        # 子界面
│   ├── {Feature}Section.kt       # 子组件
│   ├── {Feature}Callback.kt      # 接口定义
│   └── Utils.kt                  # 模块内工具
└── {Service}.kt               # Service 主类
```

**设计原则：** 功能内聚、层级清晰（Manager → Overlay → Content → 子组件）、独立生命周期、自包含、单一职责
