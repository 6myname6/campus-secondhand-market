# 🏫 校园二手交易平台

> **课程设计项目** — 基于 Java Swing + Netty + MySQL 的 C/S 架构二手交易系统

![JDK](https://img.shields.io/badge/JDK-17%2B-orange)
![Netty](https://img.shields.io/badge/Netty-4.1.112-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-green)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 📋 目录

- [项目概述](#-项目概述)
- [功能特性](#-功能特性)
- [技术栈](#-技术栈)
- [系统架构](#-系统架构)
- [快速开始](#-快速开始)
- [使用指南](#-使用指南)
- [项目结构](#-项目结构)
- [通信协议](#-通信协议)
- [API 路由表](#-api-路由表)
- [数据库设计](#-数据库设计)
- [设计决策](#-设计决策)
- [常见问题](#-常见问题)
- [开发团队](#-开发团队)

---

## 📖 项目概述

校园二手交易平台是一个基于 **Java Swing** 客户端 + **Netty** 服务端的 C/S 架构桌面应用，提供校园场景下的闲置商品交易服务。项目使用 Maven 多模块管理，包含完整的商品发布浏览、下单交易、即时聊天、收藏管理、管理员后台等功能。

**适用场景：** 大学校园内学生之间的二手物品交易（教材、电子产品、生活用品等）。

---

## ✨ 功能特性

### 👤 用户系统
- 用户注册 / 登录（BCrypt 密码加密）
- 个人资料修改（头像、手机、邮箱）
- 管理员可启用/禁用用户

### 🛒 商品管理
- 商品发布（标题、分类、描述、价格、最多 5 张图片）
- 商品浏览（分类筛选 + 关键词搜索，防 SQL 注入）
- 商品详情（浏览量统计）
- 商品编辑 / 下架（仅卖家可操作）

### 📦 订单交易
- 完整订单生命周期：**下单 → 付款 → 发货 → 确认收货**
- 买家视角：我买到的订单管理（付款、收货、取消）
- 卖家视角：我卖出的订单管理（发货填写物流单号）
- 状态机严格校验，防止非法状态流转
- `SELECT ... FOR UPDATE` 防止并发下单超卖

### 💬 即时聊天
- 商品详情页一键联系卖家，自动创建会话
- 会话列表展示最近消息和未读计数（红点提示）
- 3 秒轮询新消息
- 消息可删除（仅发送者）

### ❤️ 收藏系统
- 收藏 / 取消收藏商品
- 收藏列表管理

### 🔧 管理员后台
- 用户管理（启用 / 禁用）
- 商品分类管理（增、删、改）

---

## 🛠 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 客户端 UI | **Java Swing** | JFrame + CardLayout + JMenuBar |
| 网络通信 | **Netty 4.1** | TCP 长连接，JSON-over-TCP 协议 |
| 服务端框架 | **Netty** | 独立业务线程池，避免 I/O 阻塞 |
| 数据库 | **MySQL 8.0** | InnoDB + utf8mb4 |
| 连接池 | **HikariCP 5.0** | 高性能 JDBC 连接池 |
| JSON 序列化 | **Gson 2.10** | 请求/响应序列化 |
| 日志 | **SLF4J + Logback** | 异步日志 |
| 测试 | **JUnit 5** | 单元测试 + E2E 集成测试 |
| 构建工具 | **Maven** | 多模块（`client` + `server`） |
| 密码加密 | **BCrypt** | 安全密码哈希 |
| ORM | **自制注解式 ORM** | 反射 + 注解映射 |

---

## 🏗 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    客户端 (Java Swing)                     │
│  ┌──────┐ ┌──────┐ ┌────────┐ ┌────────┐ ┌──────────┐  │
│  │Login │ │Main  │ │GoodsDt │ │Order   │ │ Chat     │  │
│  │View  │ │View  │ │ailView │ │Views   │ │Views     │  │
│  └──┬───┘ └──┬───┘ └───┬────┘ └───┬────┘ └────┬─────┘  │
│     └────────┴─────────┴──────────┴───────────┘          │
│                        │                                  │
│                    ┌────┴────┐        API 门面            │
│                    │ApiClent │←────────────────────────── │
│                    └────┬────┘                            │
│                    ┌────┴────┐                            │
│                    │NettyCli │  TCP 通信                  │
│                    └─────────┘                            │
└──────────────────────────┬──────────────────────────────┘
                           │  JSON + '\n' 分隔
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  服务端 (Netty + MySQL)                    │
│  ┌─────────────────────────────────────────────────┐    │
│  │              NettyServer (端口 8081)             │    │
│  │  ┌───────────────────────────────────────────┐  │    │
│  │  │         ServerHandler (dispatchMap)        │  │    │
│  │  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌────────┐  │  │    │
│  │  │  │Auth  │ │Goods │ │Order │ │Message │  │  │    │
│  │  │  │Service│ │Service│ │Servic│ │Service │  │  │    │
│  │  │  └──┬───┘ └──┬───┘ └──┬───┘ └───┬────┘  │  │    │
│  │  │     └─────────┴────────┴──────────┘       │  │    │
│  │  │                    │                       │  │    │
│  │  │             ┌──────┴──────┐               │  │    │
│  │  │             │  JdbcUtils   │               │  │    │
│  │  │             └──────┬──────┘               │  │    │
│  │  └────────────────────┼───────────────────────┘  │    │
│  └───────────────────────┼──────────────────────────┘    │
│                          │                                │
│                   ┌──────┴──────┐                         │
│                   │  HikariCP    │                         │
│                   │  连接池      │                         │
│                   └──────┬──────┘                         │
└──────────────────────────┼────────────────────────────────┘
                           │
                   ┌───────┴───────┐
                   │   MySQL 8.0    │
                   │second_hand_    │
                   │   trading      │
                   └───────────────┘
```

### 分层架构

```
┌────────────────────────────────────┐
│  表示层 (Presentation)              │
│  Swing Views — UI 渲染与用户交互     │
├────────────────────────────────────┤
│  通信层 (Communication)             │
│  ApiClient / NettyClient            │
│  协议: JSON over TCP (换行分隔)      │
├────────────────────────────────────┤
│  网络层 (Network)                   │
│  NettyServer / ServerHandler        │
│  请求解析 → 路由分发 → 参数校验      │
├────────────────────────────────────┤
│  服务层 (Service)                   │
│  11 个 Service 类                   │
│  事务管理 / 业务逻辑 / 权限控制      │
├────────────────────────────────────┤
│  持久层 (Persistence)               │
│  JdbcUtils + HikariCP               │
│  ORM: 反射 + 注解映射               │
├────────────────────────────────────┤
│  数据层 (Data)                      │
│  MySQL 8.0 — 9 张业务表 + 13 条外键 │
└────────────────────────────────────┘
```

---

## 🚀 快速开始

### 环境要求

- **JDK 17+**
- **MySQL 8.0+**
- **Maven 3.8+**（如需自行编译）
- 操作系统：Windows / Linux / macOS

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS second_hand_trading DEFAULT CHARSET utf8mb4;
USE second_hand_trading;
SOURCE init.sql;
```

### 2. 配置数据库连接

编辑 `server/src/main/resources/db.properties`，修改数据库连接信息（默认 `root/123456`）：

```properties
db.url=jdbc:mysql://localhost:3306/second_hand_trading?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
db.username=root
db.password=123456
```

### 3. 启动服务端

```bash
# 方式一：直接运行预编译 JAR
java -jar dist/server.jar

# 方式二：Maven 编译运行
cd server
mvn compile exec:java -Dexec.mainClass="com.example.netty.NettyServer"
```

看到 `NettyServer started on port 8081` 表示启动成功。

### 4. 启动客户端

```bash
# 方式一：直接运行预编译 JAR
java -jar dist/client.jar

# 方式二：Maven 编译运行
cd client
mvn compile exec:java -Dexec.mainClass="com.example.swing.SwingApp"
```

> **提示：** Windows 下可直接双击 `dist/` 目录下的 `.bat` 启动脚本。
> `启动双客户端.bat` 可同时打开两个客户端，方便测试聊天和交易功能。

---

## 📖 使用指南

### 登录与注册

- 首次使用先点击"注册"标签，填写用户名 + 密码（可选手机号 / 邮箱）
- 注册成功后自动切回"登录"标签
- 密码框支持 **回车键** 快速登录

### 功能导航

| 菜单 | 功能 | 说明 |
|------|------|------|
| 商品专区 → 浏览全部商品 | 首页 | 分类筛选 + 关键词搜索 + 商品网格 |
| 商品专区 → 发布闲置商品 | 发布 | 填表单 + **选择本地图片**（最多 5 张） |
| 商品专区 → 我发布的 | 管理 | 查看自己商品、**下架**、**编辑** |
| 订单中心 → 我买到的 | 买家订单 | 付款、确认收货、取消 |
| 订单中心 → 我卖出的 | 卖家订单 | 发货（填物流单号） |
| 我的收藏 | 收藏夹 | 管理收藏商品 |
| 消息聊天 | 聊天 | 未读红点提示、消息删除 |

### 交易流程

```
发布商品 → 买家下单 → 买家付款 → 卖家发货 → 买家确认收货 → 完成
```

1. **发布**：填写标题 / 分类 / 价格，可选上传图片
2. **浏览**：首页按分类筛选或关键词搜索，点击商品进入详情
3. **下单**：详情页点"立即购买" → 填写收件人 / 电话 / 地址
4. **付款**：订单页点"付款"
5. **发货**：卖家在"我卖出的"订单中点"发货" → 填写物流公司 + 单号
6. **收货**：买家在"我买到的"订单中点"确认收货"

### 聊天

- 商品详情页点"联系卖家"自动创建会话
- 对方用户名自动解析显示
- 未读消息显示红色数字角标
- 鼠标悬停消息左侧出现 **✕** 可删除自己发送的消息
- 3 秒自动轮询新消息

### 管理员

- 数据库中 `users` 表设置 `role='admin'` 的账号登录后会多出"管理"菜单
- 管理员可启用 / 禁用用户、增删改商品分类

---

## 📁 项目结构

```
ab_java_project/
├── pom.xml                          # Maven 多模块父 POM
├── init.sql                         # 数据库初始化脚本
├── README.md                        # 项目说明文档
├── 提示.md                          # 使用提示
│
├── docs/                            # 课程设计文档
│   ├── 01-分工文档.md
│   ├── 02-项目结构与设计文档.md
│   ├── 03-使用手册.md
│   ├── 04-关键代码注解.md
│   └── 05-期中交付文档.md
│
├── dist/                            # 部署产物
│   ├── server.jar                   # 服务端 fat JAR（已编译）
│   ├── client.jar                   # 客户端 fat JAR（已编译）
│   ├── init.sql                     # 数据库脚本副本
│   ├── 启动服务端.bat               # Windows 一键启动服务端
│   ├── 启动客户端.bat               # Windows 一键启动客户端
│   └── 启动双客户端.bat             # 同时启动两个客户端（测试聊天/交易）
│
├── server/                          # 服务端模块
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/
│       │   ├── annotation/          # 自定义 ORM 注解
│       │   │   ├── Table.java       #   表名映射
│       │   │   ├── Column.java      #   列名映射
│       │   │   └── Id.java          #   主键标记
│       │   ├── entity/              # 数据实体（9 个）
│       │   │   ├── User.java
│       │   │   ├── Goods.java
│       │   │   ├── GoodsImage.java
│       │   │   ├── Category.java
│       │   │   ├── Order.java
│       │   │   ├── OrderItem.java
│       │   │   ├── Favorite.java
│       │   │   ├── Conversation.java
│       │   │   └── Message.java
│       │   ├── netty/               # Netty 网络层
│       │   │   ├── NettyServer.java     # 服务端入口
│       │   │   ├── ServerHandler.java   # 请求分发核心（dispatchMap）
│       │   │   ├── Request.java         # 请求 DTO
│       │   │   ├── Response.java        # 响应 DTO
│       │   │   └── ParamValidator.java  # 参数校验
│       │   ├── service/             # 业务服务层（11 个）
│       │   │   ├── BaseService.java     # 事务管理基类（ThreadLocal 传播）
│       │   │   ├── AuthService.java     # Token 认证（ConcurrentHashMap）
│       │   │   ├── UserService.java     # 用户管理
│       │   │   ├── GoodsService.java    # 商品管理
│       │   │   ├── GoodsImageService.java
│       │   │   ├── CategoryService.java
│       │   │   ├── OrderService.java    # 订单管理 + 状态机
│       │   │   ├── FavoriteService.java
│       │   │   ├── ConversationService.java
│       │   │   ├── MessageService.java
│       │   │   └── FileService.java     # 文件上传（魔数校验）
│       │   ├── JdbcUtils.java       # JDBC 工具类（HikariCP 封装）
│       │   └── CrudTest.java        # CRUD 冒烟测试
│       ├── main/resources/
│       │   ├── db.properties        # 数据库连接配置
│       │   └── logback.xml          # 日志配置
│       └── test/java/com/example/service/
│           ├── BaseTest.java
│           ├── UserServiceTest.java
│           └── GoodsServiceTest.java
│
├── client/                          # 客户端模块
│   ├── pom.xml
│   └── src/main/java/com/example/
│       ├── netty/                   # Netty 客户端通信
│       │   ├── NettyClient.java     # TCP 客户端
│       │   ├── ClientHandler.java
│       │   └── NettyE2ETest.java    # 端到端集成测试
│       └── swing/                   # Swing 图形界面
│           ├── SwingApp.java        # 客户端主入口（JFrame + CardLayout）
│           ├── ApiClient.java       # API 门面（封装所有服务端调用）
│           ├── AppContext.java      # 会话状态单例
│           └── view/                # 10 个视图页面
│               ├── LoginView.java
│               ├── MainView.java
│               ├── GoodsDetailView.java
│               ├── PublishView.java
│               ├── MyOrdersView.java
│               ├── OrderDetailView.java
│               ├── FavoritesView.java
│               ├── ChatListView.java
│               ├── ChatView.java
│               └── ProfileView.java
│
├── logs/                            # 运行日志
└── uploads/                         # 用户上传的商品图片
```

---

## 🔌 通信协议

### 消息格式

**请求：**
```json
{
    "action": "goods.findById",
    "params": {
        "goodsId": 1,
        "token": "uuid-token"
    },
    "_reqId": 1
}
```

**成功响应：**
```json
{
    "success": true,
    "data": { /* 业务数据 */ },
    "message": "操作成功"
}
```

**错误响应：**
```json
{
    "success": false,
    "data": null,
    "message": "商品不存在"
}
```

### 协议特征

- 基于 **TCP 长连接**，客户端启动后与服务器建立一条持久连接
- JSON 序列化，以 **换行符（`\n`）** 作为消息分隔符
- 客户端使用 **异步回调 + 请求 ID（`_reqId`）** 匹配请求与响应
- 每个请求包最大 10MB（`LineBasedFrameDecoder(10485760)`）

---

## 📡 API 路由表

| Action | 需认证 | 说明 |
|--------|:------:|------|
| **用户** | | |
| `user.register` | ✗ | 用户注册 |
| `user.login` | ✗ | 用户登录 |
| `user.findById` | ✗ | 查询用户信息 |
| `user.findByUsername` | ✗ | 按用户名查询 |
| `user.updateProfile` | ✓ | 更新个人资料 |
| `user.updateStatus` | ✓ | 启用/禁用用户（管理员） |
| `user.list` | ✓ | 用户列表（分页） |
| `user.count` | ✓ | 用户总数 |
| **分类** | | |
| `category.findAll` | ✗ | 全部分类 |
| `category.findById` | ✗ | 分类详情 |
| `category.findByParent` | ✗ | 子分类查询 |
| `category.create` | ✓ | 创建分类 |
| `category.update` | ✓ | 更新分类 |
| `category.delete` | ✓ | 删除分类 |
| **商品** | | |
| `goods.publish` | ✓ | 发布商品 |
| `goods.findById` | ✗ | 商品详情（浏览量 +1） |
| `goods.findByCategory` | ✗ | 分类浏览（分页） |
| `goods.search` | ✗ | 搜索商品（ESCAPE 防注入） |
| `goods.offShelf` | ✓ | 下架商品（卖家校验） |
| `goods.listBySeller` | ✓ | 卖家商品列表 |
| `goods.update` | ✓ | 更新商品（卖家校验） |
| **订单** | | |
| `order.create` | ✓ | 创建订单（FOR UPDATE 防竞态） |
| `order.pay` | ✓ | 支付订单 |
| `order.ship` | ✓ | 发货（卖家校验） |
| `order.complete` | ✓ | 确认收货 |
| `order.cancel` | ✓ | 取消订单（恢复商品状态） |
| `order.listByBuyer` | ✓ | 买家订单列表 |
| `order.listBySeller` | ✓ | 卖家订单列表 |
| `order.findById` | ✓ | 订单详情 |
| **聊天** | | |
| `conversation.createOrGet` | ✓ | 创建/获取会话 |
| `conversation.listByUser` | ✓ | 会话列表 |
| `message.send` | ✓ | 发送消息 |
| `message.listByConversation` | ✓ | 聊天记录（分页） |
| `message.countUnread` | ✓ | 未读消息计数 |
| `message.delete` | ✓ | 删除消息（仅发送者） |
| **收藏** | | |
| `favorite.add` | ✓ | 添加收藏 |
| `favorite.remove` | ✓ | 取消收藏 |
| `favorite.listByUser` | ✓ | 收藏列表 |
| **文件** | | |
| `file.upload` | ✓ | 上传文件（魔数校验 + 5MB 限制） |
| `file.download` | ✗ | 下载文件（base64） |
| **认证** | | |
| `auth.logout` | ✓ | 登出销毁 Token |

---

## 🗄 数据库设计

### ER 关系

```
┌──────────┐     ┌──────────┐     ┌──────────────┐
│   users  │     │  goods   │     │ goods_images │
│──────────│     │──────────│     │──────────────│
│ user_id  │◄────│seller_id │     │ goods_id (FK)│──┐
│ username │     │goods_id  │◄────│ image_url    │  │
│ password │     │category_id(FK) │ sort_order   │  │
│ phone    │     │title     │     └──────────────┘  │
│ email    │     │price     │            ▲          │
│ avatar   │     │status    │            │          │
│ role     │     │view_count│     ┌──────┴──────┐   │
│ status   │     │create_time│    │ categories  │   │
└────┬─────┘     └──────────┘    │─────────────│   │
     │                ▲          │ category_id │◄──┘
     │                │          │ parent_id   │
     ▼                │          │ category_name│
┌──────────┐     ┌────┴─────┐   └─────────────┘
│ favorites│     │  orders  │
│──────────│     │──────────│
│ user_id  │     │ buyer_id │──► users
│ goods_id │     │ seller_id│──► users
└──────────┘     │ order_no │
                 │ total_   │
                 │  amount  │
                 │ status   │
                 └────┬─────┘
                      │
                      ▼
                 ┌──────────────┐
                 │ order_items  │
                 │──────────────│
                 │ order_id (FK)│──► orders
                 │ goods_id (FK)│──► goods
                 │ price        │
                 │ quantity     │
                 └──────────────┘

┌──────────────┐     ┌──────────┐
│conversations │     │ messages │
│──────────────│     │──────────│
│conversation_id│◄───│conv_id   │
│ user1_id     │     │sender_id │──► users
│ user2_id     │     │receiver_id│──► users
│ last_message │     │content   │
│ last_time    │     │image_path│
│ unread_cnt   │     │send_time │
└──────────────┘     │is_read   │
                     └──────────┘
```

### 订单状态机

```
                    ┌─────────────┐
                    │  待支付      │  (pending_pay / 1)
                    └──────┬──────┘
                           │ 付款
                           ▼
                    ┌─────────────┐
                    │  待发货      │  (pending_ship / 2)
                    └──────┬──────┘
                           │ 发货
                           ▼
                    ┌──────────────┐
                    │  待收货       │  (pending_receive / 3)
                    └──────┬───────┘
                           │ 确认收货
                           ▼
                    ┌─────────────┐
                    │  已完成      │  (completed / 4)
                    └─────────────┘

    任意非终态 ── 取消 ──► ┌─────────────┐
                           │  已取消      │  (cancelled / 5)
                           └─────────────┘
```

### 表清单（共 9 张表）

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `users` | 用户表 | username, password(BCrypt), role, status |
| `categories` | 分类表 | parent_id(支持二级), category_name |
| `goods` | 商品表 | seller_id, category_id, title, price, status |
| `goods_images` | 商品图片表 | goods_id, image_url, sort_order |
| `orders` | 订单主表 | buyer_id, seller_id, status(状态机), 物流信息 |
| `order_items` | 订单明细表 | order_id, goods_id, price(快照) |
| `favorites` | 收藏表 | user_id, goods_id(联合唯一) |
| `conversations` | 聊天会话表 | user1_id, user2_id(小ID在前), 未读计数 |
| `messages` | 聊天消息表 | conversation_id, sender_id, content, is_read |

---

## 💡 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 通信协议 | **Netty TCP** 而非 HTTP/REST | 学习目的，深入理解 TCP；单连接复用；低延迟适合聊天 |
| 序列化 | **JSON** 而非 Protobuf | 可读性好，Gson 映射方便，小项目性能开销可接受 |
| Token 存储 | **ConcurrentHashMap** 而非 Redis | 减少外部依赖；单实例部署无分布式需求 |
| ORM | **自制注解式 ORM** 而非 MyBatis/JPA | 学习 ORM 底层原理（反射、注解、SQL 生成） |
| 密码加密 | **BCrypt** | 安全强度高，内置 salt |
| 事务 | **ThreadLocal 传播** | 支持嵌套事务复用连接，避免分布式事务复杂度 |

---

## ❓ 常见问题

| 问题 | 解决 |
|------|------|
| 连接服务器失败 | 确认服务端已启动，端口 8081 未被占用 |
| 注册失败 / 用户名已存在 | 换一个用户名 |
| 登录失败 | 检查用户名密码，输错 3 次会限流 5 秒 |
| 图片无法上传 | 仅支持 JPG/PNG/GIF，最大 5MB |
| 分类下拉为空 | 数据库未初始化，执行 `SOURCE init.sql` |
| 自己不能买自己的商品 | 这是设计限制 |

---

## 👥 开发团队

课程设计 — Java 面向对象程序设计

- **李梓豪** — 基础架构层（网络通信、数据库、持久层 ORM、服务层核心）
- **（其他组员）** — 视具体分工文档

---

## 📄 许可证

本项目仅供学习交流使用，遵循 MIT 许可证。
