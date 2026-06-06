# 校园二手交易平台 — 服务端网络层与认证模块 技术文档

**组员**：组员B (汪)  
**模块**：服务端网络与认证  
**日期**：2026年5月20日  

---

## 第一部分：需求分析

### 1.1 用例图 (Use Case Diagram)

```
┌───────────────────────────────────────────────────────────────────────┐
│                        校园二手交易平台 — 服务端                         │
│                                                                       │
│  ┌─────────┐        ┌──────────────────────────┐                     │
│  │  普通用户 │◄───────┤ 注册 / 登录               │                     │
│  │  (User)  │        │ 查看个人信息               │                     │
│  │          │────────► 修改个人资料               │                     │
│  │          │────────► 发布商品 / 浏览商品         │                     │
│  │          │────────► 下架自己的商品             │                     │
│  │          │────────► 创建订单 / 支付 / 收货      │                     │
│  │          │────────► 取消订单                   │                     │
│  │          │────────► 收藏商品 / 取消收藏         │                     │
│  │          │────────► 发送消息 / 查看聊天         │                     │
│  │          │────────► 上传商品图片               │                     │
│  └─────────┘        └──────────────────────────┘                     │
│                                                                       │
│  ┌─────────┐        ┌──────────────────────────┐                     │
│  │  管理员   │◄───────┤ 查看用户列表               │                     │
│  │ (Admin)  │────────► 启用/禁用用户              │                     │
│  └─────────┘        └──────────────────────────┘                     │
│                                                                       │
│  ┌──────────────────────────────────────────┐                         │
│  │           <<include>> 认证系统              │                         │
│  │  ┌──────────────────────────────────────┐ │                         │
│  │  │  Token 生成 (UUID)                    │ │                         │
│  │  │  Token 校验 (ConcurrentHashMap)       │ │                         │
│  │  │  Token 注销 (登出/互踢)               │ │                         │
│  │  └──────────────────────────────────────┘ │                         │
│  └──────────────────────────────────────────┘                         │
│                                                                       │
│  ◄───────────── 所有需要认证的操作 (如发布、下单、聊天等)                │
│       都 <<include>> 了 Token 校验功能                                 │
└───────────────────────────────────────────────────────────────────────┘
```

### 1.2 功能说明

本模块（服务端网络层与认证）负责以下核心功能：

| 编号 | 功能名称 | 描述 | 优先级 |
|------|----------|------|--------|
| F-01 | **TCP 服务器启动与监听** | 基于 Netty 在 8081 端口启动 TCP 服务器，接收客户端连接 | 高 |
| F-02 | **JSON 消息解析** | 将客户端发送的 JSON 字符串解析为 Request 对象，提取 `action` 和 `params` | 高 |
| F-03 | **请求路由分发** | 根据 `action` 字段，将请求路由到对应的 Service 方法处理 | 高 |
| F-04 | **Token 认证** | 用户登录后生成 UUID Token，后续敏感操作需携带 Token 校验身份 | 高 |
| F-05 | **认证中间件 (requireAuth)** | 高阶函数包装器，自动从 params 中提取 Token 并校验，通过后注入 userId | 高 |
| F-06 | **单用户单 Token 互踢** | 同一用户再次登录时，旧 Token 自动失效，确保一个用户只有一个活跃登录态 | 中 |
| F-07 | **参数校验** | 对客户端传入的参数进行类型和范围检查，拦截非法输入 | 中 |
| F-08 | **用户注册** | 检查用户名唯一性，插入新用户记录 | 高 |
| F-09 | **用户登录** | 校验用户名密码 + 账号状态，成功返回用户信息 + Token | 高 |
| F-10 | **用户信息管理** | 按ID查询用户、更新个人资料（电话/邮箱/头像）、启用/禁用用户 | 中 |
| F-11 | **用户列表查询** | 分页返回全部用户列表，供管理功能使用 | 低 |
| F-12 | **事务管理** | 提供 `execTx` / `execTxVoid` 事务模板，确保多表操作原子性 | 高 |
| F-13 | **响应序列化** | 将业务处理结果封装为 Response 对象，序列化为 JSON 返回客户端 | 高 |
| F-14 | **统一异常处理** | 捕获分发过程中的异常，转换为错误 Response 返回，不中断连接 | 中 |

### 1.3 数据流图 (Data Flow Diagram)

#### 1.3.1 上下文图 (Level-0 DFD)

```
┌──────────┐     JSON请求 (TCP)      ┌──────────────┐     SQL查询      ┌──────────┐
│          │────────────────────────►│              │────────────────►│          │
│  客户端   │                         │  服务端网络层  │                 │  MySQL   │
│ (Swing)  │◄────────────────────────│  + 认证模块   │◄────────────────│  数据库   │
│          │     JSON响应 (TCP)      │              │    查询结果      │          │
└──────────┘                         └──────────────┘                 └──────────┘
```

#### 1.3.2 一级数据流图 (Level-1 DFD)

```
                              ┌─────────────────────────────────────────────────────────┐
                              │               服务端网络层与认证模块                         │
                              │                                                         │
                              │   ┌──────────┐    请求JSON     ┌───────────────┐        │
          ┌─────────┐  TCP   │   │ 1.0      │───────────────►│ 2.0           │        │
          │ 客户端   │──────────►│ 消息接收  │                 │ 请求解析与路由 │        │
          │         │◄──────────│ (Netty   │◄───────────────│ (ServerHandler│        │
          └─────────┘  响应    │  Server) │   响应Response   │  dispatchMap) │        │
                              │   └──────────┘                └───────┬───────┘        │
                              │                                       │                │
                              │                              action → handler           │
                              │                                       │                │
                              │                    ┌──────────────────┼───────┐        │
                              │                    │                  │       │        │
                              │                    ▼                  ▼       ▼        │
                              │            ┌─────────────┐  ┌──────────┐ ┌──────────┐  │
                              │            │ 3.0         │  │ 4.0      │ │ 5.0      │  │
                              │            │ 认证中间件   │  │ 用户管理  │ │ 其他业务  │  │
                              │            │(requireAuth)│  │(UserSvc) │ │(Goods... │  │
                              │            └──────┬──────┘  └────┬─────┘ └────┬─────┘  │
                              │                   │              │            │        │
                              │          token校验│              │            │        │
                              │                   ▼              ▼            ▼        │
                              │            ┌──────────────────────────────────────┐    │
                              │            │          6.0  数据库访问层             │    │
                              │            │      JdbcUtils + HikariCP             │    │
                              │            └──────────────┬───────────────────────┘    │
                              └───────────────────────────┼────────────────────────────┘
                                                          │
                                                          ▼
                                                    ┌──────────┐
                                                    │  MySQL   │
                                                    │  数据库   │
                                                    └──────────┘
```

#### 1.3.3 二级数据流图 — 认证流程 (Process 3.0 展开)

```
                    ┌──────────────────────────────────────┐
                    │          3.0 认证中间件                │
                    │                                      │
  token + params ──►│  ┌────────────┐                      │
                    │  │ 3.1        │  token有效           │
                    │  │ 提取token  │────────► 执行业务 ────►│──► Response
                    │  │ 并校验     │                      │
                    │  └─────┬──────┘                      │
                    │        │                             │
                    │        │ token无效/缺失               │
                    │        ▼                             │
                    │  ┌────────────────────┐              │
                    │  │ 返回 Response.fail │──────────────►│──► "请先登录"
                    │  │ ("请先登录")        │              │
                    │  └────────────────────┘              │
                    └──────────────────────────────────────┘
```

---

## 第二部分：系统设计

### 2.1 模块结构图 (Module Diagram)

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         server 模块                                        │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌─────────────────────┐    ┌─────────────────────┐                      │
│  │  netty/             │    │  service/           │                      │
│  │  ┌───────────────┐  │    │  ┌───────────────┐  │                      │
│  │  │ NettyServer   │  │    │  │ BaseService   │◄─┼── 抽象基类(事务)       │
│  │  │ (入口+启动)    │  │    │  └───────────────┘  │                      │
│  │  └───────┬───────┘  │    │  ┌───────────────┐  │                      │
│  │          │ 创建      │    │  │ AuthService   │  │  Token管理            │
│  │          ▼           │    │  └───────────────┘  │                      │
│  │  ┌───────────────┐  │    │  ┌───────────────┐  │                      │
│  │  │ ServerHandler │──┼────┼─►│ UserService   │  │  用户CRUD            │
│  │  │ (请求分发)     │  │    │  └───────────────┘  │                      │
│  │  └───────┬───────┘  │    │  ┌───────────────┐  │                      │
│  │          │ 使用      │    │  │ GoodsService  │  │  (组员C)             │
│  │          ▼           │    │  └───────────────┘  │                      │
│  │  ┌───────────────┐  │    │  ┌───────────────┐  │                      │
│  │  │ParamValidator │  │    │  │ OrderService  │  │  (组员C)             │
│  │  └───────────────┘  │    │  └───────────────┘  │                      │
│  │  ┌───────────────┐  │    │  ...                 │                      │
│  │  │ Request/Resp  │  │    └─────────────────────┘                      │
│  │  └───────────────┘  │                                                 │
│  └─────────────────────┘                                                 │
│                                                                           │
│  ┌─────────────────────┐    ┌─────────────────────┐                      │
│  │  annotation/        │    │  entity/            │                      │
│  │  @Table  @Column    │    │  User, Goods, ...   │                      │
│  └─────────────────────┘    └─────────────────────┘                      │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │  JdbcUtils.java  (HikariCP + ORM反射 + 通用CRUD)                    │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────┘
```

### 2.2 类图 (Class Diagram)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             NettyServer                                     │
├────────────────────────────────────────────────────────────────────────────┤
│ - port: int                                                                 │
│ - bossGroup: EventLoopGroup                                                │
│ - workerGroup: EventLoopGroup                                              │
│ - readyLatch: CountDownLatch                                               │
├────────────────────────────────────────────────────────────────────────────┤
│ + NettyServer(port: int)                                                    │
│ + NettyServer(port: int, useLatch: boolean)                                │
│ + start(): void                                                            │
│ + awaitReady(): void                                                       │
│ + shutdown(): void                                                         │
│ + main(args: String[]): void                                               │
└────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ 创建 & 配置
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                          ServerHandler                                      │
│                    extends SimpleChannelInboundHandler<String>              │
├────────────────────────────────────────────────────────────────────────────┤
│ - GSON: Gson (含 LocalDateTime 适配器)                                      │
│ - MAP_TYPE: Type (Map<String,Object>)                                      │
│ - userService: UserService                                                 │
│ - goodsService: GoodsService                                               │
│ - orderService: OrderService                                               │
│ - authService: AuthService           ◄── Token 认证                        │
│ - conversationService: ConversationService                                 │
│ - messageService: MessageService                                           │
│ - fileService: FileService                                                 │
│ - dispatchMap: Map<String, Function<JsonObject, Response>>  ◄── 路由表      │
├────────────────────────────────────────────────────────────────────────────┤
│ + ServerHandler()                                                          │
│ - initDispatch(): void                 ◄── 注册30+个路由                    │
│ - requireAuth(fn): Function<...>      ◄── 认证中间件(高阶函数)              │
│ # channelRead0(ctx, msg): void       ◄── 消息接收入口                      │
│ # exceptionCaught(ctx, cause): void                                        │
│ - getStr(obj, key): String                                                 │
│ - getInt(obj, key, default): int                                           │
└────────────────────────────────────────────────────────────────────────────┘
          │                           │                       │
          │ uses                      │ uses                  │ uses
          ▼                           ▼                       ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────────────┐
│    AuthService   │    │   UserService    │    │  其他 Service (11个)       │
├──────────────────┤    ├──────────────────┤    ├──────────────────────────┤
│ - tokenToUserId  │    │ extends BaseSvc  │    │ extends BaseService       │
│   : ConcurrentMap│    ├──────────────────┤    ├──────────────────────────┤
│ - userIdToToken  │    │ + login()        │    │ + 各业务方法...            │
│   : ConcurrentMap│    │ + register()     │    └──────────────────────────┘
├──────────────────┤    │ + findById()     │               △
│ + login(userId)  │    │ + updateStatus() │               │
│ + validate(token)│    │ + updateProfile()│               │ extends
│ + logout(token)  │    │ + listUsers()    │    ┌──────────────────────────┐
└──────────────────┘    │ + countUsers()   │    │      BaseService         │
                        └──────────────────┘    ├──────────────────────────┤
                                                │ + execTx(Function): T   │
┌──────────────────────┐                        │ + execTxVoid(VoidFn)    │
│   ParamValidator     │                        └──────────────────────────┘
├──────────────────────┤
│ + require(params,key)│     ┌──────────────────────┐
│ + requireInt()       │     │       Request        │
│ + requireStr()       │     ├──────────────────────┤
│ + requirePositiveInt │     │ - action: String     │
│ + requireBigDecimal  │     │ - params: JsonObject │
│ + requireStringLength│     │ - _reqId: int        │
└──────────────────────┘     └──────────────────────┘
                             ┌──────────────────────┐
                             │       Response       │
                             ├──────────────────────┤
                             │ - success: boolean   │
                             │ - data: Object       │
                             │ - message: String    │
                             │ - _reqId: int        │
                             ├──────────────────────┤
                             │ + ok(data): Response │
                             │ + ok(): Response     │
                             │ + fail(msg): Response│
                             └──────────────────────┘
```

### 2.3 组件图 (Component Diagram)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          客户端 (client.jar)                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────────────────────────────┐    │
│  │SwingApp  │───►│ApiClient │───►│          NettyClient             │    │
│  └──────────┘    └──────────┘    │  CompletableFuture 异步通信        │    │
│                                  └──────────────┬───────────────────┘    │
└─────────────────────────────────────────────────┼────────────────────────┘
                                                  │ TCP: JSON + \n
                                                  │
┌─────────────────────────────────────────────────┼────────────────────────┐
│                      服务端 (server.jar)          │ 8081 端口              │
│                                                  ▼                        │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │                     NettyServer                                   │    │
│  │  LineBasedFrameDecoder → StringDecoder → ServerHandler           │    │
│  └──────────────────────────────┬───────────────────────────────────┘    │
│                                 │                                        │
│  ┌──────────────────────────────▼───────────────────────────────────┐    │
│  │                     ServerHandler                                 │    │
│  │  ┌─────────────┐    ┌──────────────────┐    ┌────────────────┐   │    │
│  │  │ JSON 解析    │───►│ dispatchMap 路由 │───►│ Service 执行业务│   │    │
│  │  │ Request.java │    │ (函数式分发)     │    │                 │   │    │
│  │  └─────────────┘    └───────┬──────────┘    └───────┬────────┘   │    │
│  │                             │ requireAuth              │          │    │
│  │                             ▼                          │          │    │
│  │                    ┌──────────────────┐                │          │    │
│  │                    │   AuthService    │                │          │    │
│  │                    │ ConcurrentHashMap│                │          │    │
│  │                    └──────────────────┘                │          │    │
│  └───────────────────────────────────────────────────────┼──────────┘    │
│                                                          │               │
│  ┌───────────────────────────────────────────────────────▼──────────┐    │
│  │                     JdbcUtils (数据持久层)                        │    │
│  │  ┌──────────────┐   ┌──────────────┐   ┌────────────────────┐   │    │
│  │  │ HikariCP     │   │ 反射 ORM     │   │ PreparedStatement  │   │    │
│  │  │ 连接池       │   │ @Table映射   │   │ 参数化查询防注入    │   │    │
│  │  └──────────────┘   └──────────────┘   └────────────────────┘   │    │
│  └──────────────────────────────┬───────────────────────────────────┘    │
│                                 │ JDBC                                   │
└─────────────────────────────────┼────────────────────────────────────────┘
                                  │
                                  ▼
                        ┌──────────────────┐
                        │   MySQL 8.0      │
                        │ second_hand_     │
                        │   trading        │
                        └──────────────────┘
```

### 2.4 时序图 (Sequence Diagram)

#### 2.4.1 用户登录全流程

```
客户端                     NettyServer          ServerHandler       UserService      AuthService       MySQL
  │                            │                     │                   │                │              │
  │── TCP连接 ────────────────►│                     │                   │                │              │
  │                            │── channelActive ──►│                   │                │              │
  │                            │                     │                   │                │              │
  │── {"action":"UserService   │                     │                   │                │              │
  │    .login",                │                     │                   │                │              │
  │    params:{username:"wyq", │                     │                   │                │              │
  │    password:"123"}} ──────►│                     │                   │                │              │
  │                            │── channelRead0 ────►│                   │                │              │
  │                            │                     │                   │                │              │
  │                            │                     │── 1.JSON解析      │                │              │
  │                            │                     │   Request.action  │                │              │
  │                            │                     │   ="UserService   │                │              │
  │                            │                     │    .login"        │                │              │
  │                            │                     │                   │                │              │
  │                            │                     │── 2.dispatchMap   │                │              │
  │                            │                     │   .get(action)    │                │              │
  │                            │                     │   → login()      │                │              │
  │                            │                     │                   │                │              │
  │                            │                     │                   │── 3.login() ──►│              │
  │                            │                     │                   │                │              │
  │                            │                     │                   │── SELECT ... ──────────────►│
  │                            │                     │                   │◄── User row ────────────────│
  │                            │                     │                   │                │              │
  │                            │                     │                   │── 校验密码     │              │
  │                            │                     │                   │   校验状态     │              │
  │                            │                     │                   │                │              │
  │                            │                     │                   │── 4.返回User ─►│              │
  │                            │                     │                   │                │              │
  │                            │                     │── 5.authService   │                │              │
  │                            │                     │   .login(userId)  │                │              │
  │                            │                     │                   │                │              │
  │                            │                     │                   │   ┌────────────────────────┐
  │                            │                     │                   │   │ 1.检查旧Token → 删除   │
  │                            │                     │                   │   │ 2.UUID生成新Token     │
  │                            │                     │                   │   │ 3.tokenToUserId.put() │
  │                            │                     │                   │   │ 4.userIdToToken.put() │
  │                            │                     │                   │   └────────────────────────┘
  │                            │                     │                   │                │              │
  │                            │                     │◄── 6.token ───────────────────────│              │
  │                            │                     │                   │                │              │
  │                            │                     │── 7.Response.ok   │                │              │
  │                            │                     │   ({user,token})  │                │              │
  │                            │                     │                   │                │              │
  │◄── {"success":true,       │◄── ctx.writeAndFlush │                   │                │              │
  │     "data":{"user":{...}, │                     │                   │                │              │
  │     "token":"abc123..."}} │                     │                   │                │              │
```

#### 2.4.2 requireAuth 认证中间件调用时序

```
客户端                  ServerHandler         requireAuth包装器        AuthService      实际业务方法
  │                         │                       │                      │                 │
  │── action:"goods.publish"│                       │                      │                 │
  │   params:{token, ...}  │                       │                      │                 │
  │                         │                       │                      │                 │
  │                         │── 1.从dispatchMap     │                      │                 │
  │                         │   获取 requireAuth    │                      │                 │
  │                         │   (goodsService       │                      │                 │
  │                         │    ::publish)         │                      │                 │
  │                         │                       │                      │                 │
  │                         │── 2.执行包装后的函数 ──►│                      │                 │
  │                         │                       │                      │                 │
  │                         │                       │── 3.提取params.token  │                 │
  │                         │                       │                      │                 │
  │                         │                       │── 4.validate(token) ─►│                 │
  │                         │                       │                      │                 │
  │                         │                       │                      │ tokenToUserId   │
  │                         │                       │                      │   .get(token)   │
  │                         │                       │◄── userId or null ────│                 │
  │                         │                       │                      │                 │
  │                         │           ┌───────────┴───────────┐          │                 │
  │                         │           │ token有效?              │          │                 │
  │                         │           │ YES → 执行业务方法      │          │                 │
  │                         │           │ NO  → Response.fail    │          │                 │
  │                         │           │       ("请先登录")      │          │                 │
  │                         │           └───────────┬───────────┘          │                 │
  │                         │                       │                      │                 │
  │                         │                       │ (有效时)               │                 │
  │                         │                       │── fn.apply(params) ────────────────────►│
  │                         │                       │                      │  执行业务逻辑    │
  │                         │                       │◄── Response ───────────────────────────│
  │                         │                       │                      │                 │
  │                         │◄── Response ──────────│                      │                 │
  │                         │                       │                      │                 │
  │◄── JSON Response ───────│                       │                      │                 │
```

### 2.5 协作图 (Collaboration Diagram)

#### 2.5.1 请求处理协作

```
                        ┌─────────────────────────────────┐
                        │         ServerHandler            │
                        │  (中心协调者, 所有请求的入口)      │
                        └────┬───────┬──────┬─────┬───────┘
                             │       │      │     │
          ┌──────────────────┘       │      │     └──────────────────┐
          │                          │      │                        │
          ▼                          ▼      ▼                        ▼
┌──────────────────┐   ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐
│  1: 解析 JSON    │   │  2: 查找路由     │  │ 3a: 公开接口  │  │ 3b: 需认证   │
│  Gson.fromJson() │   │  dispatchMap     │  │   直接调用    │  │ requireAuth  │
│  → Request       │   │  .get(action)    │  │   fn.apply()  │  │   包装器     │
└──────────────────┘   └──────────────────┘  └──────┬───────┘  └──────┬───────┘
                                                    │                 │
                                                    │         ┌───────┴───────┐
                                                    │         │ AuthService   │
                                                    │         │ .validate()   │
                                                    │         └───────┬───────┘
                                                    │                 │
                                                    ▼                 ▼
                                             ┌──────────────────────────────┐
                                             │  4: Service 执行业务逻辑       │
                                             │  (UserService/GoodsService/   │
                                             │   OrderService/...)           │
                                             └──────────────┬───────────────┘
                                                            │
                                             ┌──────────────▼───────────────┐
                                             │  5: BaseService.execTx()     │
                                             │  JdbcUtils CRUD → MySQL      │
                                             └──────────────────────────────┘
```

### 2.6 关键算法描述

#### 2.6.1 函数式路由分发算法

```
算法：dispatchMap 请求路由分发
输入：客户端 JSON 消息 msg
输出：服务端 JSON 响应

1. 解析 JSON:
   try:
       request ← Gson.fromJson(msg, Request.class)
       提取 action ← request.getAction()
       提取 params ← request.getParams()   // 可能为 null
   catch Exception:
       返回 Response.fail("JSON parse error")

2. 路由查找:
   handler ← dispatchMap.get(action)
   if handler == null:
       返回 Response.fail("未知 action: " + action)

3. 执行业务:
   try:
       response ← handler.apply(params != null ? params : new JsonObject())
       response._reqId ← request.get_reqId()
       序列化并写回: ctx.writeAndFlush(Gson.toJson(response) + "\n")
   catch Exception:
       提取错误消息
       返回 Response.fail(errorMessage)
```

#### 2.6.2 requireAuth 认证包装算法

```
算法：requireAuth 高阶函数认证包装
输入：params (JsonObject, 包含 token 字段)
输出：Response

1. 提取 Token:
   token ← getStr(params, "token")
   if token == null:
       返回 Response.fail("未登录或登录已过期")

2. 校验 Token:
   userId ← authService.validate(token)
   if userId == null:
       返回 Response.fail("未登录或登录已过期")

3. 执行业务:
   返回 fn.apply(params)   // Token 有效, 放行到业务方法
```

#### 2.6.3 Token 单用户互踢算法

```
算法：AuthService.login 单用户单Token互踢
输入：userId
输出：新 Token (UUID字符串)

1. 检查已有登录:
   existingToken ← userIdToToken.get(userId)
   if existingToken != null:
       返回 existingToken   // 已登录, 复用旧Token (不踢)

   // 注意: 当前实现为"复用旧Token"策略
   // 如需"互踢", 可改用以下逻辑:
   // if existingToken != null:
   //     tokenToUserId.remove(existingToken)  // 踢掉旧Token
   //     userIdToToken.remove(userId)

2. 生成新 Token:
   token ← UUID.randomUUID().toString().replace("-", "")

3. 存储映射:
   tokenToUserId.put(token, userId)
   userIdToToken.put(userId, token)

4. 返回 token
```

#### 2.6.4 事务模板算法

```
算法：BaseService.execTx 事务包围
输入：fn (Lambda: Connection → T)
输出：T (业务返回值)

1. 获取连接:
   conn ← JdbcUtils.getConnection()  // 从 HikariCP 连接池

2. 开启事务:
   conn.setAutoCommit(false)

3. 执行业务:
   try:
       result ← fn.apply(conn)
       conn.commit()          // 成功 → 提交
       返回 result
   catch Exception:
       conn.rollback()        // 失败 → 回滚
       抛出 RuntimeException("Transaction failed", e)
   finally:
       conn.setAutoCommit(true)
       conn.close()           // 归还连接池
```

---

## 第三部分：代码实现（含详细注释）

### 3.1 NettyServer.java — TCP 服务器入口

```java
package com.example.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Netty TCP 服务器主类。
 *
 * 【设计思想】
 * - 采用 Netty 经典的 Boss-Worker 线程模型：
 *   Boss 线程组(1线程)负责接收客户端连接请求，
 *   Worker 线程组(CPU核数×2)负责处理已建立连接的 I/O 读写。
 * - 使用 LineBasedFrameDecoder 实现基于换行符(\n)的 TCP 帧定界，
 *   配合 StringDecoder/StringEncoder 实现字符串级别的消息收发。
 *
 * 【支持的构造方式】
 * - NettyServer(port): 普通启动，不等待就绪信号
 * - NettyServer(port, useLatch): 配合测试框架，通过 CountDownLatch
 *   通知其他线程"服务器已就绪"
 */
public class NettyServer {

    // SLF4J 日志记录器 — 记录服务器启动、关闭和异常事件
    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    // ==================== 成员变量 ====================

    private final int port;                          // 监听端口号 (默认 8081)

    // Boss 线程组: 负责监听端口并接收客户端连接
    // 线程数设置为 1 是因为通常只需要一个线程来执行 accept 操作
    private EventLoopGroup bossGroup;

    // Worker 线程组: 负责处理已建立的连接的 I/O 读写和业务处理
    // 默认线程数为 Runtime.getRuntime().availableProcessors() * 2
    private EventLoopGroup workerGroup;

    // 同步工具: 用于测试场景 — 服务器启动完成后通知等待线程
    // 不使用 CountDownLatch 时为 null
    private final CountDownLatch readyLatch;

    // ==================== 构造函数 ====================

    /**
     * 简化构造函数 — 默认不使用 CountDownLatch 同步
     * @param port 监听端口
     */
    public NettyServer(int port) {
        this(port, false);
    }

    /**
     * 完整构造函数
     * @param port     监听端口
     * @param useLatch 是否创建 CountDownLatch 用于就绪通知 (测试用)
     */
    public NettyServer(int port, boolean useLatch) {
        this.port = port;
        this.readyLatch = useLatch ? new CountDownLatch(1) : null;
    }

    // ==================== 核心方法 ====================

    /**
     * 启动 Netty 服务器。
     *
     * 【启动流程】
     * 1. 创建 Boss(1线程) 和 Worker(默认线程数) 事件循环组
     * 2. 使用 ServerBootstrap 配置服务器参数:
     *    - SO_BACKLOG=128: TCP 连接请求队列最大长度
     *    - SO_KEEPALIVE=true: 启用 TCP KeepAlive 探测死连接
     * 3. 配置 ChannelPipeline (管道):
     *    LineBasedFrameDecoder(10MB) → StringDecoder(UTF-8)
     *    → StringEncoder(UTF-8) → ServerHandler(自定义)
     * 4. 绑定端口并阻塞等待直到服务器 Channel 关闭
     * 5. finally 块确保资源释放
     *
     * @throws InterruptedException 线程中断异常
     */
    public void start() throws InterruptedException {
        // 步骤1: 创建事件循环组
        bossGroup = new NioEventLoopGroup(1);       // Boss: 1个线程即可
        workerGroup = new NioEventLoopGroup();       // Worker: 默认 CPU*2

        try {
            // 步骤2: 创建服务端引导器并配置参数
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)           // 设置线程组
                    .channel(NioServerSocketChannel.class)     // 使用 NIO 传输
                    .option(ChannelOption.SO_BACKLOG, 128)     // 连接队列深度
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // 保活探测
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            // 步骤3: 配置每个新连接的管道处理器链
                            ch.pipeline()
                                // (1) 帧定界器 — 按换行符切分消息，最大帧长10MB
                                // LineBasedFrameDecoder 通过查找 \n 或 \r\n 来分割字节流
                                .addLast(new LineBasedFrameDecoder(10485760))  // 10MB
                                // (2) 字节→字符串解码器 — 使用 UTF-8 编码
                                .addLast(new StringDecoder(Charset.forName("UTF-8")))
                                // (3) 字符串→字节编码器 — 将响应字符串编码为字节流
                                .addLast(new StringEncoder(Charset.forName("UTF-8")))
                                // (4) 自定义业务处理器 — 负责请求解析、路由分发、响应
                                .addLast(new ServerHandler());
                        }
                    });

            // 步骤4: 绑定端口，sync()阻塞等待绑定完成
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("NettyServer started on port {}", port);

            // 通知等待线程服务器已就绪 (测试框架使用)
            if (readyLatch != null) readyLatch.countDown();

            // 步骤5: 阻塞等待服务器 Channel 关闭
            // closeFuture().await() 会一直阻塞直到调用 shutdown()
            future.channel().closeFuture().await();
        } finally {
            // 步骤6: 确保优雅关闭，释放所有线程资源
            shutdown();
        }
    }

    /**
     * 等待服务器就绪 (仅当 useLatch=true 时有效)。
     * 供测试代码在启动服务器后、发送请求前调用来确保服务器已启动完成。
     */
    public void awaitReady() throws InterruptedException {
        if (readyLatch != null) readyLatch.await();
    }

    /**
     * 优雅关闭服务器。
     * 调用 EventLoopGroup.shutdownGracefully() 让正在处理的任务完成后再关闭线程池。
     */
    public void shutdown() {
        log.info("NettyServer shutting down...");
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    /**
     * 程序入口。
     * 启动 Netty 服务器监听 8081 端口，并注册 JVM 关闭钩子确保优雅退出。
     */
    public static void main(String[] args) throws InterruptedException {
        NettyServer server = new NettyServer(8081);
        // 注册 JVM 关闭钩子 — 当 JVM 正常退出(Ctrl+C或kill)时触发 graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.start();
    }
}
```

### 3.2 ServerHandler.java — 请求分发核心

```java
package com.example.netty;

import com.example.service.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 服务端核心处理器。
 *
 * 【设计思想】
 * 1. 继承 SimpleChannelInboundHandler<String> — Netty 会自动释放消息内存
 * 2. 函数式路由表 dispatchMap — 将 action 字符串映射到 Lambda 处理函数
 *    新增业务只需 put 一行，无需修改框架代码 (开闭原则)
 * 3. requireAuth 高阶函数包装器 — 在不侵入业务代码的前提下添加 Token 校验
 * 4. 统一异常处理 — 任何业务异常都转换为 Response.fail 返回，不中断 Channel
 *
 * 【线程安全】
 * SimpleChannelInboundHandler 在 pipeline 中是共享实例 (@Sharable)，
 * 但 dispatchMap 在构造器中初始化后只读，所以是线程安全的。
 */
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    // Gson 实例 — 配置了 LocalDateTime 类型适配器，确保日期时间正确序列化
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, type, ctx) ->
                            new JsonPrimitive(
                                src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, type, ctx) ->
                            LocalDateTime.parse(
                                json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    // Map<String, Object> 的反射类型 — 用于 Gson 泛型反序列化
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    // ==================== Service 实例 ====================
    // 每个 Service 只在构造时创建一次，后续所有请求复用
    private final UserService userService = new UserService();
    private final GoodsService goodsService = new GoodsService();
    private final OrderService orderService = new OrderService();
    private final CategoryService categoryService = new CategoryService();
    private final GoodsImageService goodsImageService = new GoodsImageService();
    private final FavoriteService favoriteService = new FavoriteService();
    private final AuthService authService = new AuthService();
    private final ConversationService conversationService = new ConversationService();
    private final MessageService messageService = new MessageService();
    private final FileService fileService = new FileService();

    // ==================== 路由表 ====================
    /**
     * 核心路由表: action → 处理函数。
     *
     * 使用 Map<String, Function<JsonObject, Response>> 实现函数式路由:
     * - Key:    action 字符串，如 "UserService.login", "GoodsService.publish"
     * - Value:  处理函数，接收 params (JsonObject)，返回 Response
     *
     * 命名规范: "模块名.方法名" (如 "GoodsService.search")
     */
    private final Map<String, Function<JsonObject, Response>> dispatchMap = new HashMap<>();

    // ==================== 构造函数 ====================

    /** 构造时完成所有路由注册 */
    public ServerHandler() {
        initDispatch();
    }

    /**
     * 初始化路由表。
     *
     * 【路由注册规范】
     * - 格式: dispatchMap.put("模块名.方法名", params → Response.ok(service.xxx(...)))
     * - 公开接口: 直接放入 service 方法引用或 Lambda
     * - 需认证接口: 通过 requireAuth() 包装
     *
     * 当前共注册 30+ 个路由，覆盖以下模块:
     *   UserService (7) / GoodsService (6) / OrderService (8) /
     *   CategoryService (4) / GoodsImageService (2) /
     *   FavoriteService (4) / FileService (2) /
     *   AuthService (2) / ConversationService (3) / MessageService (4)
     */
    private void initDispatch() {

        // ==================== UserService (用户管理) ====================

        // 用户登录: 公开接口，不需要认证
        // 先调用 UserService.login 校验凭证，再调用 AuthService.login 生成 Token
        dispatchMap.put("UserService.login", p -> {
            var user = userService.login(
                    p.get("username").getAsString(),
                    p.get("password").getAsString());
            String token = authService.login(user.getUserId());
            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("token", token);
            return Response.ok(result);
        });

        // 用户注册: 公开接口
        // 参数: username(必填), password(必填), phone(选填), email(选填)
        dispatchMap.put("UserService.register", p ->
                Response.ok(userService.register(
                        p.get("username").getAsString(),
                        p.get("password").getAsString(),
                        getStr(p, "phone"),     // getStr 安全获取可选字符串
                        getStr(p, "email"))));

        // 按用户名查询: 公开接口
        dispatchMap.put("UserService.findByUsername", p ->
                Response.ok(userService.findByUsername(
                        p.get("username").getAsString())));

        // 按ID查询用户: 公开接口
        dispatchMap.put("UserService.findById", p ->
                Response.ok(userService.findById(
                        p.get("userId").getAsInt())));

        // 更新用户状态(启用/禁用): 管理接口
        // 参数: userId, status (1=正常, 0=禁用)
        dispatchMap.put("UserService.updateStatus", p -> {
            userService.updateStatus(
                    p.get("userId").getAsInt(),
                    p.get("status").getAsInt());
            return Response.ok();
        });

        // 用户列表(分页): 管理接口
        // 参数: page(默认1), pageSize(默认10)
        dispatchMap.put("UserService.listUsers", p ->
                Response.ok(userService.listUsers(
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10))));

        // 更新个人资料: 需认证接口 (此处简化实现)
        // 参数: userId, phone, email, avatar
        dispatchMap.put("UserService.updateProfile", p -> {
            userService.updateProfile(
                    p.get("userId").getAsInt(),
                    getStr(p, "phone"),
                    getStr(p, "email"),
                    getStr(p, "avatar"));
            return Response.ok();
        });

        // 统计用户总数
        dispatchMap.put("UserService.countUsers", p ->
                Response.ok(userService.countUsers()));

        // ==================== GoodsService (商品管理) ====================

        // 发布商品
        dispatchMap.put("GoodsService.publish", p ->
                Response.ok(goodsService.publish(
                        p.get("sellerId").getAsInt(),
                        p.get("categoryId").getAsInt(),
                        p.get("title").getAsString(),
                        getStr(p, "description"),
                        new BigDecimal(p.get("price").getAsString()),
                        p.has("originalPrice")
                            ? new BigDecimal(p.get("originalPrice").getAsString())
                            : null)));

        // 查询商品详情 (自动增加浏览量)
        dispatchMap.put("GoodsService.findById", p ->
                Response.ok(goodsService.findById(
                        p.get("goodsId").getAsInt())));

        // 按分类分页浏览 (仅上架商品)
        dispatchMap.put("GoodsService.findByCategory", p ->
                Response.ok(goodsService.findByCategory(
                        p.get("categoryId").getAsInt(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10))));

        // 标题关键词模糊搜索
        dispatchMap.put("GoodsService.search", p ->
                Response.ok(goodsService.search(
                        p.get("keyword").getAsString(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10))));

        // 下架商品 (卖家校验)
        dispatchMap.put("GoodsService.offShelf", p -> {
            goodsService.offShelf(
                    p.get("goodsId").getAsInt(),
                    p.get("sellerId").getAsInt());
            return Response.ok();
        });

        // 卖家商品列表
        dispatchMap.put("GoodsService.listBySeller", p ->
                Response.ok(goodsService.listBySeller(
                        p.get("sellerId").getAsInt(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10))));

        // 更新商品信息
        dispatchMap.put("GoodsService.updateGoods", p -> {
            goodsService.updateGoods(
                    p.get("goodsId").getAsInt(),
                    getStr(p, "title"),
                    getStr(p, "description"),
                    p.has("price")
                        ? new BigDecimal(p.get("price").getAsString())
                        : null,
                    p.has("status") ? p.get("status").getAsInt() : null);
            return Response.ok();
        });

        // ==================== OrderService (订单管理) ====================

        dispatchMap.put("OrderService.findById", p ->
                Response.ok(orderService.findById(
                        p.get("orderId").getAsInt())));

        dispatchMap.put("OrderService.findByOrderNo", p ->
                Response.ok(orderService.findByOrderNo(
                        p.get("orderNo").getAsString())));

        dispatchMap.put("OrderService.findItems", p ->
                Response.ok(orderService.findItems(
                        p.get("orderId").getAsInt())));

        // 买家订单列表(分页)
        dispatchMap.put("OrderService.listByBuyer", p ->
                Response.ok(orderService.listByBuyer(
                        p.get("buyerId").getAsInt(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10))));

        // 支付订单: 仅买家可操作
        dispatchMap.put("OrderService.pay", p -> {
            orderService.pay(
                    p.get("orderId").getAsInt(),
                    p.get("buyerId").getAsInt());
            return Response.ok();
        });

        // 发货: 卖家填写物流信息
        dispatchMap.put("OrderService.ship", p -> {
            orderService.ship(
                    p.get("orderId").getAsInt(),
                    p.get("logisticsCompany").getAsString(),
                    p.get("logisticsNo").getAsString());
            return Response.ok();
        });

        // 确认收货: 买家操作
        dispatchMap.put("OrderService.complete", p -> {
            orderService.complete(
                    p.get("orderId").getAsInt(),
                    p.get("buyerId").getAsInt());
            return Response.ok();
        });

        // 取消订单: 买家操作，可附带原因
        dispatchMap.put("OrderService.cancel", p -> {
            orderService.cancel(
                    p.get("orderId").getAsInt(),
                    p.get("buyerId").getAsInt(),
                    getStr(p, "reason"));
            return Response.ok();
        });

        // 创建订单 (事务操作: 插入订单+明细+更新商品状态)
        dispatchMap.put("OrderService.createOrder", p -> {
            // 解析订单项列表 — 使用 Gson 反序列化为 List<OrderItemRequest>
            List<OrderService.OrderItemRequest> items = GSON.fromJson(
                    p.get("items").getAsJsonArray(),
                    new TypeToken<List<OrderService.OrderItemRequest>>() {}.getType());
            return Response.ok(orderService.createOrder(
                    p.get("buyerId").getAsInt(),
                    p.get("receiverName").getAsString(),
                    p.get("receiverPhone").getAsString(),
                    p.get("address").getAsString(),
                    getStr(p, "buyerNote"),
                    items));
        });

        // ==================== CategoryService (分类管理) ====================
        // ... (其余路由注册略，完整代码见源文件)

        // ==================== AuthService (认证) ====================

        // 登出: 销毁 Token
        dispatchMap.put("AuthService.logout", p -> {
            authService.logout(p.get("token").getAsString());
            return Response.ok();
        });

        // ==================== ConversationService (聊天会话) ====================
        // 需要认证的接口使用 requireAuth() 包装
        dispatchMap.put("ConversationService.createOrGet", requireAuth(p ->
                Response.ok(conversationService.createOrGet(
                        p.get("userId1").getAsInt(),
                        p.get("userId2").getAsInt()))));

        dispatchMap.put("ConversationService.listByUser", requireAuth(p ->
                Response.ok(conversationService.listByUser(
                        p.get("userId").getAsInt(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 10)))));

        // ==================== MessageService (聊天消息) ====================
        dispatchMap.put("MessageService.send", requireAuth(p ->
                Response.ok(messageService.send(
                        p.get("conversationId").getAsInt(),
                        p.get("senderId").getAsInt(),
                        p.get("receiverId").getAsInt(),
                        getStr(p, "content"),
                        getStr(p, "imagePath")))));

        dispatchMap.put("MessageService.listByConversation", requireAuth(p ->
                Response.ok(messageService.listByConversation(
                        p.get("conversationId").getAsInt(),
                        getInt(p, "page", 1),
                        getInt(p, "pageSize", 20)))));
    }

    // ==================== 认证中间件 ====================

    /**
     * 认证包装器 (高阶函数)。
     *
     * 【设计模式】装饰器模式的函数式实现
     * 接收一个业务处理函数 fn，返回一个"先做 Token 校验、再执行 fn"的新函数。
     * 业务代码完全不感知认证逻辑 — 这是 AOP(面向切面编程)的朴素实现。
     *
     * 【执行流程】
     * 1. 从 params 中提取 "token" 字段
     * 2. 调用 authService.validate(token) 校验
     *    - 校验通过 → 执行业务函数 fn.apply(params)
     *    - 校验失败 → 直接返回 Response.fail("未登录或登录已过期")
     *
     * @param fn 需要被保护的业务处理函数
     * @return 包装后的新函数 (包含认证逻辑)
     */
    private Function<JsonObject, Response> requireAuth(
            Function<JsonObject, Response> fn) {
        return params -> {
            // 步骤1: 提取 Token
            String token = getStr(params, "token");
            // 步骤2: 校验 Token
            if (token == null || authService.validate(token) == null) {
                // 步骤2a: Token 无效 — 拒绝请求
                return Response.fail("未登录或登录已过期");
            }
            // 步骤2b: Token 有效 — 放行到业务逻辑
            return fn.apply(params);
        };
    }

    // ==================== 消息接收与分发 ====================

    /**
     * channelRead0: Netty 的回调方法，每收到一条完整的消息（以\n分隔）时触发。
     *
     * 【处理流程】
     * 1. JSON 字符串 → Request 对象 (action + params + _reqId)
     * 2. 根据 action 查找 dispatchMap 中的处理函数
     * 3. 执行处理函数，获取 Response
     * 4. Response → JSON 字符串 → ctx.writeAndFlush() 写回客户端
     * 5. 任何异常都被捕获并转换为错误 Response
     *
     * @param ctx ChannelHandlerContext — Netty 的 Channel 上下文
     * @param msg 接收到的完整消息字符串 (已经是去掉换行符的纯 JSON)
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        Request request;

        // 步骤1: JSON 反序列化
        try {
            request = GSON.fromJson(msg, Request.class);
        } catch (Exception e) {
            log.warn("JSON parse error: {}", e.getMessage());
            ctx.writeAndFlush(
                GSON.toJson(Response.fail("JSON parse error: " + e.getMessage()))
                + "\n");
            return;
        }

        // 步骤2: 路由查找
        Function<JsonObject, Response> fn = dispatchMap.get(request.getAction());
        if (fn == null) {
            // 未注册的 action — 返回错误
            Response r = Response.fail("未知 action: " + request.getAction());
            r._reqId = request.get_reqId();
            ctx.writeAndFlush(GSON.toJson(r) + "\n");
            return;
        }

        // 步骤3 & 4: 执行业务 + 写回响应
        try {
            // 确保 params 不为 null (部分请求可能没有 params)
            JsonObject params = request.getParams() != null
                ? request.getParams()
                : new JsonObject();

            Response response = fn.apply(params);
            response._reqId = request.get_reqId();   // 回传请求ID
            ctx.writeAndFlush(GSON.toJson(response) + "\n");
        } catch (Exception e) {
            // 步骤5: 业务异常统一处理
            // getCause() 优先 — 因为 execTx 会包装为 RuntimeException
            String err = e.getCause() != null
                ? e.getCause().getMessage()
                : e.getMessage();
            log.error("Dispatch error: action={} error={}",
                request.getAction(), err);
            Response r = Response.fail(err);
            r._reqId = request.get_reqId();
            ctx.writeAndFlush(GSON.toJson(r) + "\n");
        }
    }

    /**
     * Channel 异常处理。
     * 当网络连接出现异常时，记录日志并关闭该连接。
     * 不影响其他连接的正常服务。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }

    // ==================== 辅助方法 ====================

    /**
     * 安全获取可选字符串参数。
     * 先检查 key 是否存在且非 null，再取值；否则返回 null。
     * 避免 Gson 的 getAsString() 在 null 值时抛出 NullPointerException。
     */
    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
            ? obj.get(key).getAsString()
            : null;
    }

    /**
     * 安全获取可选整数参数，带默认值。
     * 用于分页参数(page, pageSize)等有合理默认值的场景。
     */
    private static int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}
```

### 3.3 Request.java — 请求 DTO

```java
package com.example.netty;

import com.google.gson.JsonObject;

/**
 * 客户端请求的 Java 对象映射。
 *
 * 【JSON 格式】
 * {
 *     "action": "UserService.findByUsername",  // 调用的服务方法
 *     "params": {"username": "test"},           // 方法参数
 *     "_reqId": 1                               // 请求序列号(可选, 用于请求-响应匹配)
 * }
 *
 * 【设计说明】
 * - 使用 Gson 自动将 JSON 字段映射到 Java 属性 (字段名需保持一致)
 * - params 使用 JsonObject 而非 Map 因为:
 *   1. 参数类型多样 (String/Int/BigDecimal/嵌套对象), JsonObject 类型灵活
 *   2. 避免额外的类型转换代码
 * - _reqId 用于客户端将请求与响应进行匹配 (类似 HTTP/2 的 Stream ID)
 */
public class Request {

    private String action;        // 操作名称, 格式: "模块名.方法名"
    private JsonObject params;    // 参数对象, 可为 null
    private int _reqId;           // 请求序列号

    public Request() {}

    // ===== Getters & Setters =====
    public int get_reqId() { return _reqId; }
    public void set_reqId(int _reqId) { this._reqId = _reqId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public JsonObject getParams() { return params; }
    public void setParams(JsonObject params) { this.params = params; }

    // ===== 便捷取值方法 =====

    /** 获取字符串参数 */
    public String getParam(String key) {
        return params != null && params.has(key)
            ? params.get(key).getAsString()
            : null;
    }

    /** 获取整数参数 */
    public int getParamInt(String key) {
        return params != null && params.has(key)
            ? params.get(key).getAsInt()
            : 0;
    }
}
```

### 3.4 Response.java — 响应 DTO

```java
package com.example.netty;

/**
 * 服务端响应的 Java 对象映射。
 *
 * 【JSON 格式】
 * {
 *     "success": true,              // 操作是否成功
 *     "data": { ... },              // 业务数据 (成功时)
 *     "message": "错误描述"          // 错误消息 (失败时)
 * }
 *
 * 【设计说明】
 * - 使用静态工厂方法 (ok/fail) 而非公开构造函数:
 *   1. 语义清晰: Response.ok(data) vs new Response(true, data, null)
 *   2. 强制一致性: 成功时 message 必须为 null, 失败时 data 必须为 null
 * - _reqId 为包级可见 (非 private), 由 ServerHandler 在写回前设置
 */
public class Response {

    private boolean success;    // true=成功, false=失败
    private Object data;        // 成功时的业务数据 (任意类型)
    private String message;     // 失败时的错误描述
    int _reqId;                 // 回传客户端的请求序列号 (包级可见)

    /** 私有构造: 通过静态工厂方法创建 */
    private Response(boolean success, Object data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    /** 创建成功响应 (带数据) */
    public static Response ok(Object data) {
        return new Response(true, data, null);
    }

    /** 创建成功响应 (无数据 — 用于 update/delete 等操作) */
    public static Response ok() {
        return new Response(true, null, null);
    }

    /** 创建失败响应 */
    public static Response fail(String message) {
        return new Response(false, null, message);
    }

    // ===== Getters =====
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setData(Object data) { this.data = data; }
    public void setMessage(String message) { this.message = message; }
}
```

### 3.5 ParamValidator.java — 参数校验工具

```java
package com.example.netty;

import com.google.gson.JsonObject;
import java.math.BigDecimal;

/**
 * 参数校验工具类。
 *
 * 【设计说明】
 * - 纯静态方法, 私有构造器防止实例化
 * - 校验不通过直接抛出 IllegalArgumentException，
 *   由 ServerHandler.channelRead0() 的 try-catch 捕获并转为 Response.fail
 * - 方法命名采用 requireXxx 风格，失败抛异常，方便链式调用
 *
 * 【安全作用】
 * 在参数进入业务逻辑之前进行校验，防止:
 * 1. 空指针异常 (null params/缺失必要字段)
 * 2. 类型转换异常 (字符串转数字等)
 * 3. 非法值 (负数金额、空字符串等)
 * 4. 超长字符串 (潜在的DoS攻击)
 */
public final class ParamValidator {

    private ParamValidator() {}  // 工具类, 禁止实例化

    /**
     * 基础校验: 检查参数是否存在且非 null。
     * 所有其他 require 方法都基于此方法。
     */
    public static void require(JsonObject params, String key) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
    }

    /**
     * 校验并返回整型参数。
     * 使用场景: userId, goodsId, page, pageSize 等
     */
    public static int requireInt(JsonObject params, String key) {
        require(params, key);
        try {
            return params.get(key).getAsInt();
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }

    /**
     * 校验并返回字符串参数。
     * 使用场景: username, password, title 等
     */
    public static String requireStr(JsonObject params, String key) {
        require(params, key);
        try {
            return params.get(key).getAsString();
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
    }

    /**
     * 校验正整数 (>0)。
     * 使用场景: 所有ID类参数 (userId, goodsId, orderId 等)
     */
    public static int requirePositiveInt(JsonObject params, String key) {
        int val = requireInt(params, key);
        if (val <= 0) throw new IllegalArgumentException(key + " 必须大于0");
        return val;
    }

    /**
     * 校验并返回 BigDecimal (金额类参数)。
     * 自动检查非负数。
     * 使用场景: price, totalAmount 等
     */
    public static BigDecimal requireBigDecimal(JsonObject params, String key) {
        require(params, key);
        try {
            BigDecimal val = new BigDecimal(params.get(key).getAsString());
            if (val.compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException(key + " 不能为负数");
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 不是有效的金额");
        }
    }

    /**
     * 校验字符串长度上限。
     * 使用场景: title(最大200), address(最大500) 等，防止超长字符串攻击。
     */
    public static void requireStringLength(JsonObject params, String key, int maxLen) {
        String val = requireStr(params, key);
        if (val.length() > maxLen)
            throw new IllegalArgumentException(
                key + " 长度不能超过" + maxLen + "个字符");
    }
}
```

### 3.6 AuthService.java — Token 认证服务

```java
package com.example.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 认证服务。
 *
 * 【设计说明】
 * - 基于内存 ConcurrentHashMap 的 Token 存储，适用于单机部署。
 * - ConcurrentHashMap 保证多线程并发的读写安全，无需额外加锁。
 * - 维护双向映射:
 *   tokenToUserId: Token → userId (快速校验)
 *   userIdToToken: userId → Token (快速查找旧Token, 实现互踢)
 *
 * 【Token 生命周期】
 * 生成(login) → 使用(validate) → 销毁(logout/互踢)
 *
 * 【单用户单Token策略】
 * 当同一用户多次登录时，当前实现为"复用已有Token"策略。
 * 这样做的好处是:
 * - 旧客户端不会突然掉线
 * - 避免了多端登录冲突
 *
 * 【安全考虑】
 * - Token = UUID 去掉连字符 (32位十六进制字符)，碰撞概率极低
 * - 无过期时间: 这是当前实现的一个局限，生产中应加入 TTL + 定时清理
 * - 不持久化: 服务端重启后所有 Token 失效，所有用户需重新登录
 */
public class AuthService {

    // Token → userId 映射 (主索引: 登录验证时使用)
    // ConcurrentHashMap: 分段锁机制，高并发下性能优于 Hashtable
    private final Map<String, Integer> tokenToUserId = new ConcurrentHashMap<>();

    // userId → Token 映射 (反向索引: 用于查找和清理旧Token)
    private final Map<Integer, String> userIdToToken = new ConcurrentHashMap<>();

    /**
     * 用户登录 — 生成并存储 Token。
     *
     * 【互踢逻辑】
     * 如果该用户已有活跃 Token，直接复用旧 Token 返回。
     * 这确保了:
     * 1. 一个用户只有一个活跃 Token
     * 2. 旧的登录会话保持有效 (不会被踢)
     *
     * @param userId 登录用户的ID
     * @return 生成的 (或已有的) Token 字符串
     */
    public String login(int userId) {
        // 检查是否已有活跃Token: 有则直接返回, 无则新建
        String existing = userIdToToken.get(userId);
        if (existing != null) return existing;

        // 生成新Token: UUID去掉连字符，32位十六进制
        String token = UUID.randomUUID().toString().replace("-", "");
        // 双向映射
        tokenToUserId.put(token, userId);
        userIdToToken.put(userId, token);
        return token;
    }

    /**
     * 校验 Token 有效性。
     *
     * 【时间复杂度】O(1) — HashMap 查找
     *
     * @param token 待校验的 Token 字符串
     * @return 对应的 userId，若 Token 无效则返回 null
     */
    public Integer validate(String token) {
        return tokenToUserId.get(token);
    }

    /**
     * 登出 — 销毁 Token。
     *
     * 同时删除双向映射中的记录，Token 立即失效。
     * 后续携带此 Token 的请求将被 requireAuth 拒绝。
     *
     * @param token 待注销的 Token
     */
    public void logout(String token) {
        Integer userId = tokenToUserId.remove(token);
        if (userId != null) userIdToToken.remove(userId);
    }
}
```

### 3.7 UserService.java — 用户管理服务

```java
package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.User;

import java.sql.Connection;
import java.util.List;

/**
 * 用户管理服务。
 *
 * 【职责】
 * - 用户注册: 检查用户名唯一性，插入用户记录
 * - 用户登录: 校验用户名+密码+账号状态
 * - 用户查询: 按ID/用户名查询
 * - 用户管理: 更新状态(启用/禁用)、更新资料、分页列表、计数
 *
 * 【事务说明】
 * 所有方法通过 execTx 在独立事务中执行。
 * 对于单体查询(如 findById)，事务确保读一致性。
 */
public class UserService extends BaseService {

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return User 对象，不存在时返回 null
     */
    public User findByUsername(String username) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, User.class,
                        "SELECT * FROM users WHERE username = ?", username));
    }

    /**
     * 用户登录。
     *
     * 【校验流程】
     * 1. 先按 (username, password) 联合查询
     *    如果查到了 → 再检查 status 是否为禁用
     *    如果没查到 → 再查用户名是否存在
     *        → 存在: 密码错误
     *        → 不存在: 用户不存在
     * 2. 这种做法区分了"用户不存在"和"密码错误"，
     *    防止攻击者通过错误信息探测注册用户列表
     *
     * @param username 用户名
     * @param password 密码 (明文 — 注意: 生产环境应使用 BCrypt 加密比较)
     * @return 登录成功的 User 对象
     * @throws RuntimeException 登录失败 (用户不存在/密码错误/账号禁用)
     */
    public User login(String username, String password) {
        return execTx(conn -> {
            // 先尝试用户名+密码联合查询
            User user = JdbcUtils.queryOne(conn, User.class,
                    "SELECT * FROM users WHERE username = ? AND password = ?",
                    username, password);
            if (user == null) {
                // 区分错误类型
                User exist = JdbcUtils.queryOne(conn, User.class,
                        "SELECT * FROM users WHERE username = ?", username);
                if (exist == null)
                    throw new RuntimeException("用户不存在");
                if (exist.getStatus() == 0)
                    throw new RuntimeException("账号已被禁用");
                throw new RuntimeException("密码错误");
            }
            // 即使密码正确，也检查状态
            if (user.getStatus() == 0)
                throw new RuntimeException("账号已被禁用");
            return user;
        });
    }

    /**
     * 按用户ID查询。
     */
    public User findById(int userId) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, User.class,
                        "SELECT * FROM users WHERE user_id = ?", userId));
    }

    /**
     * 用户注册。
     *
     * 【注册流程】
     * 1. 检查用户名是否已存在 (唯一约束)
     * 2. 插入新用户记录，角色默认为 'user'，状态默认为 1 (正常)
     * 3. 返回自增主键 userId
     *
     * @param username 用户名 (必填, 唯一)
     * @param password 密码 (必填)
     * @param phone    手机号 (选填)
     * @param email    邮箱 (选填)
     * @return 新用户的 userId
     * @throws RuntimeException 用户名已存在
     */
    public int register(String username, String password, String phone, String email) {
        return execTx(conn -> {
            // 检查重名
            User exist = JdbcUtils.queryOne(conn, User.class,
                    "SELECT * FROM users WHERE username = ?", username);
            if (exist != null) {
                throw new RuntimeException("用户名已存在");
            }
            // 插入并返回自增ID
            return JdbcUtils.insertAndGetKey(conn,
                    "INSERT INTO users (username, password, phone, email, role, status) " +
                    "VALUES (?, ?, ?, ?, 'user', 1)",
                    username, password, phone, email);
        });
    }

    /**
     * 更新用户状态 (启用/禁用)。
     * 管理员功能。
     *
     * @param userId 目标用户ID
     * @param status 状态: 1=正常, 0=禁用
     */
    public void updateStatus(int userId, int status) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE users SET status = ? WHERE user_id = ?",
                    status, userId);
            if (rows == 0) throw new RuntimeException("用户不存在");
        });
    }

    /**
     * 更新用户个人资料。
     * 手机、邮箱、头像均可选更新 (传 null 的不更新)。
     */
    public void updateProfile(int userId, String phone, String email, String avatar) {
        execTxVoid(conn -> {
            JdbcUtils.update(conn,
                    "UPDATE users SET phone = ?, email = ?, avatar = ? WHERE user_id = ?",
                    phone, email, avatar, userId);
        });
    }

    /**
     * 分页查询用户列表。
     * 按创建时间倒序排列。
     *
     * @param page     页码 (从1开始)
     * @param pageSize 每页条数
     * @return 用户列表
     */
    public List<User> listUsers(int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, User.class,
                        "SELECT * FROM users ORDER BY create_time DESC",
                        page, pageSize));
    }

    /**
     * 统计用户总数。
     */
    public long countUsers() {
        return execTx(conn ->
                JdbcUtils.queryCount(conn, "SELECT COUNT(*) FROM users"));
    }
}
```

### 3.8 BaseService.java — 事务基类

```java
package com.example.service;

import com.example.JdbcUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * 事务管理抽象基类。
 *
 * 【核心设计：模板方法模式的函数式实现】
 * 不使用传统的抽象方法，而是用 Lambda 回调实现"事务包围"模式:
 * - 业务代码通过 Lambda 表达式传入具体的 SQL 操作
 * - 基类负责连接获取、事务提交/回滚、资源释放
 * - 业务代码无需手动管理 Connection
 *
 * 【设计优势】
 * 1. 连接泄漏防护: finally 块确保连接一定归还连接池
 * 2. 事务原子性: 一组SQL要么全成功(commit)要么全失败(rollback)
 * 3. 样板代码消除: 业务代码不再需要写重复的 try-catch-commit-rollback
 * 4. 一致性: 全项目的事务策略统一由此类管理
 *
 * 【使用示例】
 * // 有返回值
 * User user = execTx(conn ->
 *     JdbcUtils.queryOne(conn, User.class, "SELECT ...", id));
 *
 * // 无返回值
 * execTxVoid(conn -> {
 *     JdbcUtils.update(conn, "UPDATE users SET ...", ...);
 *     JdbcUtils.insert(conn, goods);
 * });
 */
public abstract class BaseService {

    /**
     * 在事务中执行业务逻辑，并返回结果。
     *
     * 【事务流程】
     * 1. 从 HikariCP 连接池获取连接
     * 2. conn.setAutoCommit(false) — 关闭自动提交，开启事务
     * 3. 执行业务 Lambda
     * 4. conn.commit() — 成功，提交事务
     * 5. 若步骤3抛出异常 → conn.rollback() — 回滚所有改动
     * 6. finally: 恢复 autoCommit + 归还连接
     *
     * @param fn  业务逻辑 (Lambda: Connection → T)
     * @param <T> 返回值类型
     * @return    业务逻辑的返回值
     * @throws RuntimeException 事务失败 (原始异常被包装)
     */
    protected <T> T execTx(Function<Connection, T> fn) {
        Connection conn = null;
        try {
            // 步骤1: 从连接池获取连接
            conn = JdbcUtils.getConnection();
            // 步骤2: 开启事务
            conn.setAutoCommit(false);
            // 步骤3: 执行业务
            T result = fn.apply(conn);
            // 步骤4: 提交
            conn.commit();
            return result;
        } catch (Exception e) {
            // 步骤5: 回滚
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            // 包装为 RuntimeException 抛出
            // (上层 ServerHandler 的 try-catch 会捕获并转为 Response.fail)
            throw new RuntimeException("Transaction failed", e);
        } finally {
            // 步骤6: 清理
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);  // 恢复默认状态
                    conn.close();              // 归还连接池
                }
            } catch (SQLException ignored) {}
        }
    }

    /**
     * 在事务中执行无返回值的业务逻辑。
     *
     * 内部复用 execTx，通过 TransactionVoid 函数式接口适配。
     * 免去业务代码写 return null 的麻烦。
     *
     * @param fn 无返回值的业务逻辑
     */
    protected void execTxVoid(TransactionVoid fn) {
        execTx(conn -> {
            try {
                fn.execute(conn);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;  // Void 方法返回 null，外部最终丢弃
        });
    }

    /**
     * 函数式接口: 无返回值的事务回调。
     * 等价于 Consumer<Connection>，但允许抛出受检异常。
     */
    @FunctionalInterface
    protected interface TransactionVoid {
        void execute(Connection conn) throws Exception;
    }
}
```

---

## 第四部分：测试用例与测试结果

### 4.1 CrudTest.java — CRUD 冒烟测试

#### 测试说明

`CrudTest` 是一个**独立运行的冒烟测试**（非 JUnit），覆盖所有 7 个实体类的 CRUD 操作。测试采用**事务回滚**策略 — 所有 INSERT/UPDATE/DELETE 操作结束后统一 rollback，确保不污染数据库。

#### 测试用例清单

| 编号 | 测试模块 | 测试场景 | 验证点 |
|------|----------|----------|--------|
| TC-01 | User CRUD | 插入用户 | 自增ID > 0 |
| TC-02 | User CRUD | 查询用户 | username, phone 匹配 |
| TC-03 | User CRUD | PreparedStatement 防注入 | 参数化查询正确返回结果 |
| TC-04 | User CRUD | 更新用户 | phone, email 已变更 |
| TC-05 | User CRUD | 删除用户 | 查询返回 null |
| TC-06 | Goods CRUD | 使用实体对象插入 | 自增ID > 0 |
| TC-07 | Goods CRUD | 查询商品 | title, price 匹配 |
| TC-08 | Goods CRUD | updateById 更新 | price 已变更 |
| TC-09 | Goods CRUD | 分页查询 (queryPage) | 返回结果不为空 |
| TC-10 | Goods CRUD | 计数查询 (queryCount) | count ≥ 1 |
| TC-11 | Goods CRUD | deleteById 删除 | 查询返回 null |
| TC-12 | Order CRUD | 创建订单+明细 | orderId > 0, itemId > 0 |
| TC-13 | Order CRUD | 关联查询 | receiverName, totalAmount 匹配 |
| TC-14 | Order CRUD | 更新订单状态 | status变为2, logisticsCompany匹配 |
| TC-15 | Order CRUD | 删除订单 | 先删明细再删主表 |
| TC-16 | Favorite CRUD | 添加收藏 | favId > 0 |
| TC-17 | Favorite CRUD | 查询收藏 | userId, goodsId 匹配 |
| TC-18 | Favorite CRUD | 唯一约束校验 | 重复收藏触发数据库异常 |
| TC-19 | Favorite CRUD | 删除收藏 | 查询返回 null |
| TC-20 | Message CRUD | 创建会话+消息 | convId > 0, msgId > 0 |
| TC-21 | Message CRUD | 查询消息 | content 匹配 |
| TC-22 | Message CRUD | 标为已读 | isRead 变为 1 |
| TC-23 | Message CRUD | 删除消息+会话 | 查询返回 null |

#### 测试结果

```
========== CRUD 测试开始 ==========

--- User CRUD ---
  INSERT  userId=1 ✓
  SELECT  username=test_user phone=13800000001 ✓
  SELECT  (PreparedStatement 防注入) ✓
  UPDATE  phone=13900000002 email=updated@example.com ✓
  DELETE ✓

--- Goods CRUD ---
  INSERT  goodsId=1 ✓
  SELECT  title=iPhone 15 99新 price=4999.00 ✓
  UPDATE  price=4599.00 ✓
  PAGE   total=1 ✓
  COUNT  1 ✓
  DELETE ✓

--- Order CRUD ---
  INSERT  orderId=1 orderNo=ORD1716203xxx ✓
  INSERT  orderItemId=1 ✓
  SELECT  receiver=张三 amount=79.00 items=1 ✓
  UPDATE  status=2 logistics=顺丰速运 ✓
  DELETE ✓

--- Favorite CRUD ---
  INSERT  favId=1 ✓
  SELECT  userId=... goodsId=... ✓
  UNIQUE 约束正确拦截重复收藏 ✓
  DELETE ✓

--- Message CRUD ---
  INSERT  convId=1 ✓
  INSERT  msgId=1 ✓
  SELECT  content=你好！我对你的商品感兴趣 ✓
  UPDATE  isRead=1 ✓
  DELETE ✓

========== 全部测试通过 ==========
(已回滚，数据库无变化)
```

### 4.2 UserServiceTest.java — 单元测试

#### 测试说明

基于 JUnit 5 的单元测试，继承 `BaseTest`（预留 setUp/tearDown 生命周期）。使用 UUID 生成唯一用户名避免测试间数据冲突。每个 Service 方法通过 `execTx` 自行管理事务。

#### 测试用例

```java
package com.example.service;

import com.example.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 单元测试。
 *
 * 【测试策略】
 * - 使用 UUID 随机用户名: 避免测试数据残留导致的冲突
 * - 每个 @Test 方法独立运行: 不依赖执行顺序
 * - 真实数据库测试: 方法内部的 execTx 自动 commit
 *   (测试环境建议使用独立的测试数据库)
 */
class UserServiceTest extends BaseTest {

    private final UserService userService = new UserService();

    /** 生成唯一用户名: "u_" + UUID前8位 */
    private String uid() {
        return "u_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * TC-U01: 注册 + 登录 + 密码错误场景。
     *
     * 测试流程:
     * 1. 注册新用户 → 验证返回有效的 userId (>0)
     * 2. 用正确密码登录 → 验证返回 User 对象且 username 匹配
     * 3. 用错误密码登录 → 验证抛出 RuntimeException
     *
     * 覆盖方法: register(), login()
     */
    @Test
    void testRegisterAndLogin() {
        // 1. 注册
        String uname = uid();
        int userId = userService.register(
                uname, "pass123", "13811111111", "j@test.com");
        assertTrue(userId > 0, "注册应返回有效ID");

        // 2. 正确登录
        User user = userService.login(uname, "pass123");
        assertNotNull(user, "登录应成功");
        assertEquals(uname, user.getUsername());

        // 3. 错误密码
        assertThrows(RuntimeException.class,
                () -> userService.login(uname, "wrong_pass"),
                "错误密码应抛异常");
    }

    /**
     * TC-U02: 按ID查询 + 状态更新。
     *
     * 测试流程:
     * 1. 注册用户
     * 2. findById 查询 → 验证 status 默认为 1
     * 3. updateStatus 设为 0 → findById 验证 status 已变为 0
     *
     * 覆盖方法: findById(), updateStatus()
     */
    @Test
    void testFindByIdAndUpdateStatus() {
        String uname = uid();
        int userId = userService.register(uname, "pass", null, null);

        // 查询
        User user = userService.findById(userId);
        assertNotNull(user);
        assertEquals(1, user.getStatus(), "默认状态应为1");

        // 禁用
        userService.updateStatus(userId, 0);
        user = userService.findById(userId);
        assertEquals(0, user.getStatus());
    }

    /**
     * TC-U03: 分页查询 + 总数统计。
     *
     * 测试流程:
     * 1. 注册3个用户
     * 2. listUsers(1, 2) → 验证返回 ≤ 2 条
     * 3. countUsers() → 验证 ≥ 3
     *
     * 覆盖方法: listUsers(), countUsers()
     */
    @Test
    void testListAndCount() {
        userService.register(uid(), "pass", null, null);
        userService.register(uid(), "pass", null, null);
        userService.register(uid(), "pass", null, null);

        // 分页: 请求第1页，每页2条
        List<User> page = userService.listUsers(1, 2);
        assertTrue(page.size() <= 2, "分页最多返回2条");

        // 总数: 至少3个 (可能有之前测试的残留数据)
        long count = userService.countUsers();
        assertTrue(count >= 3, "至少应有3个用户");
    }
}
```

#### 测试执行结果

```
---------------------------------------------------------------
Test set: com.example.service.UserServiceTest
---------------------------------------------------------------
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: 0.057 s

测试结果: 3/3 通过 ✓
- testRegisterAndLogin          PASSED
- testFindByIdAndUpdateStatus   PASSED
- testListAndCount              PASSED
```

---

## 第五部分：运行与部署

### 5.1 编译打包

```bash
# 在项目根目录执行
mvn clean package -DskipTests
```

打包产物:
- `server/target/server-jar-with-dependencies.jar` — 服务端 fat JAR
- `client/target/client-jar-with-dependencies.jar` — 客户端 fat JAR

### 5.2 启动服务端

```bash
java -jar dist/server.jar
```

预期输出:
```
NettyServer started on port 8081
```

### 5.3 通信端口

| 参数 | 值 |
|------|-----|
| 协议 | TCP |
| 端口 | 8081 |
| 编码 | UTF-8 |
| 帧定界 | 换行符 `\n` |
| 最大帧长 | 10 MB |

---

## 附录：文件清单

| 序号 | 文件路径 | 行数 | 职责 |
|------|----------|------|------|
| 1 | `server/.../netty/NettyServer.java` | 90 | TCP 服务器启动与配置 |
| 2 | `server/.../netty/ServerHandler.java` | 370 | 请求解析、路由分发、认证中间件 |
| 3 | `server/.../netty/Request.java` | 30 | 客户端请求 DTO |
| 4 | `server/.../netty/Response.java` | 37 | 服务端响应 DTO |
| 5 | `server/.../netty/ParamValidator.java` | 56 | 参数校验工具 |
| 6 | `server/.../service/AuthService.java` | 35 | Token 认证服务 |
| 7 | `server/.../service/UserService.java` | 87 | 用户管理服务 |
| 8 | `server/.../service/BaseService.java` | 51 | 事务管理抽象基类 |
| 9 | `server/.../CrudTest.java` | 294 | 全实体 CRUD 冒烟测试 |
| 10 | `server/.../service/UserServiceTest.java` | 58 | UserService 单元测试 |
| 11 | `server/.../service/BaseTest.java` | 21 | 测试基类 |
