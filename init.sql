-- ============================================================================
-- 校园二手交易平台 — 数据库初始化脚本
-- ============================================================================
-- 数据库：second_hand_trading
-- 引擎：InnoDB（支持事务、行级锁、外键约束）
-- 字符集：utf8mb4（支持 emoji 和完整 Unicode）
-- 作者：李梓豪（组员李 — 基础架构层）
-- 日期：2026-05
-- ============================================================================
--
-- 【设计说明】
--   本脚本负责创建 9 张业务表及其所有索引、外键约束。
--   同时插入分类种子数据（8 个预定义商品分类）。
--   所有表使用 IF NOT EXISTS 保证可重复执行。
--
-- 【表清单】
--   1. users          — 用户表
--   2. categories     — 商品分类表
--   3. goods          — 商品表
--   4. goods_images   — 商品图片表
--   5. orders         — 订单主表
--   6. order_items    — 订单明细表
--   7. favorites      — 收藏表
--   8. conversations  — 聊天会话表
--   9. messages       — 聊天消息表
--
-- 【运行方式】
--   mysql -u root -p < init.sql
--   或：在 MySQL 客户端中执行 source init.sql;
--
-- 【前置条件】
--   1. MySQL 8.0+ 已安装并运行
--   2. 数据库 second_hand_trading 已创建：
--      CREATE DATABASE IF NOT EXISTS second_hand_trading DEFAULT CHARSET utf8mb4;
--   3. 用户 root 有建表和修改权限
-- ============================================================================

USE second_hand_trading;

-- ============================================================================
-- 1. 用户表 (users)
-- ============================================================================
-- 存储平台所有注册用户信息，包括普通用户和管理员。
--
-- 字段说明：
--   user_id     : 用户唯一ID，自增主键
--   username    : 登录账号，唯一约束，最大50字符
--   password    : BCrypt 加密后的密码哈希值，最大255字符
--   phone       : 手机号，可选（注册/联系方式）
--   email       : 邮箱，可选
--   avatar      : 头像文件路径，默认使用系统默认头像
--   role        : 用户角色，user=普通用户, admin=管理员（管理端可见不同菜单）
--   status      : 账户状态，1=正常启用, 0=禁用（管理员可操作）
--   create_time : 注册时间，由数据库自动设置
--   update_time : 最后更新时间，由数据库自动维护
--
-- 索引设计：
--   idx_username : 登录时按用户名查询，高频操作
--   idx_status   : 管理员按状态筛选用户列表
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    user_id     INT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)   UNIQUE NOT NULL COMMENT '账号',
    password    VARCHAR(255)  NOT NULL COMMENT '密码（BCrypt加密）',
    phone       VARCHAR(20)   NULL COMMENT '手机号',
    email       VARCHAR(100)  NULL COMMENT '邮箱',
    avatar      VARCHAR(500)  DEFAULT '/images/default_avatar.png' COMMENT '头像路径',
    role        ENUM('user', 'admin') DEFAULT 'user' COMMENT '用户角色',
    status      TINYINT       DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username(username),
    INDEX idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================================
-- 2. 商品分类表 (categories)
-- ============================================================================
-- 存储商品的分类体系，支持两级分类。
-- parent_id = 0 表示顶级分类，> 0 表示子分类。
--
-- 字段说明：
--   category_id   : 分类唯一ID
--   parent_id     : 父分类ID，0=顶级分类
--   category_name : 分类名称，唯一约束
--   sort_order    : 排序序号，数字越小越靠前显示
--
-- 索引设计：
--   idx_parent : 按父分类查询子分类，如查找"电子产品"下的所有子类
-- ============================================================================
CREATE TABLE IF NOT EXISTS categories (
    category_id   INT PRIMARY KEY AUTO_INCREMENT,
    parent_id     INT          DEFAULT 0 COMMENT '父分类ID，0表示顶级',
    category_name VARCHAR(50)  NOT NULL UNIQUE COMMENT '分类名称',
    sort_order    INT          DEFAULT 0 COMMENT '排序序号',
    INDEX idx_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 插入预定义的 8 个顶级分类种子数据
-- 使用 IGNORE 防止重复执行时主键冲突
INSERT IGNORE INTO categories (category_id, category_name, sort_order) VALUES
(1, '电子产品', 0),
(2, '书籍教材', 1),
(3, '生活用品', 2),
(4, '运动户外', 3),
(5, '服饰鞋包', 4),
(6, '数码配件', 5),
(7, '文具办公', 6),
(8, '其他', 7);

-- ============================================================================
-- 3. 商品表 (goods)
-- ============================================================================
-- 存储用户发布的二手商品信息。
-- 与 users（卖家）和 categories（分类）通过外键关联。
--
-- 字段说明：
--   goods_id       : 商品唯一ID
--   seller_id      : 卖家用户ID，外键 → users.user_id
--   category_id    : 商品分类ID，外键 → categories.category_id
--   title          : 商品标题，必填，最大200字符
--   description    : 商品详细描述，TEXT 类型支持长文本
--   price          : 售价，使用 DECIMAL(10,2) 保证金额精度
--   original_price : 原价/划线价，可选，用于展示优惠幅度
--   status         : 商品状态：1=在售, 2=已售出, 3=已下架
--   view_count     : 浏览量，每次查看商品详情自动+1
--   create_time    : 发布时间
--   update_time    : 最后更新时间
--
-- 索引设计（共5个，覆盖常用查询）：
--   idx_seller      : 卖家查看自己发布的商品列表
--   idx_category    : 按分类浏览商品
--   idx_status      : 筛选在售/已售/下架商品
--   idx_price       : 按价格排序/筛选
--   idx_create_time : 按发布时间排序（最新发布在前）
-- ============================================================================
CREATE TABLE IF NOT EXISTS goods (
    goods_id       INT PRIMARY KEY AUTO_INCREMENT,
    seller_id      INT            NOT NULL COMMENT '卖家ID',
    category_id    INT            NOT NULL COMMENT '分类ID',
    title          VARCHAR(200)   NOT NULL COMMENT '商品标题',
    description    TEXT           COMMENT '商品描述',
    price          DECIMAL(10,2)  NOT NULL COMMENT '售价',
    original_price DECIMAL(10,2)  NULL COMMENT '原价',
    status         TINYINT        DEFAULT 1 COMMENT '1=在售 2=已售 3=下架',
    view_count     INT            DEFAULT 0 COMMENT '浏览量',
    create_time    DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    update_time    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_seller(seller_id),
    INDEX idx_category(category_id),
    INDEX idx_status(status),
    INDEX idx_price(price),
    INDEX idx_create_time(create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================================================
-- 4. 商品图片表 (goods_images)
-- ============================================================================
-- 存储商品的多张图片信息（一个商品可以有最多5张图片）。
-- 图片按 sort_order 排序显示。
--
-- 字段说明：
--   image_id  : 图片唯一ID
--   goods_id  : 所属商品ID，外键 → goods.goods_id，级联删除
--   image_url : 图片存储路径（如 /uploads/2026/05/xxx.jpg）
--   sort_order: 显示排序，值越小越靠前
--
-- 索引设计：
--   idx_goods : 按商品ID查询该商品的所有图片
-- ============================================================================
CREATE TABLE IF NOT EXISTS goods_images (
    image_id   INT PRIMARY KEY AUTO_INCREMENT,
    goods_id   INT NOT NULL COMMENT '商品ID',
    image_url  VARCHAR(500) NOT NULL COMMENT '图片路径',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    INDEX idx_goods(goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

-- ============================================================================
-- 5. 订单主表 (orders)
-- ============================================================================
-- 存储交易订单的核心信息，包含收件人、金额、物流、状态等。
-- 与 users（买家、卖家）关联。
--
-- 字段说明：
--   order_id          : 订单唯一ID
--   order_no          : 订单号（UUID生成），唯一，用户可见
--   buyer_id          : 买家用户ID
--   seller_id         : 卖家用户ID（冗余存储，加速"我卖出的"查询）
--   receiver_name     : 收货人姓名
--   receiver_phone    : 收货人联系电话
--   address           : 收货详细地址
--   total_amount      : 订单总金额（= Σ order_items.price × order_items.quantity）
--   buyer_note        : 买家备注/留言，可选
--   logistics_company : 物流公司名称（卖家发货时填写）
--   logistics_no      : 物流单号（卖家发货时填写）
--   status            : 订单状态，见下方状态机
--   create_time       : 下单时间
--   pay_time          : 付款时间
--   ship_time         : 发货时间
--   complete_time     : 交易完成时间
--   cancel_reason     : 取消原因
--   update_time       : 最后更新时间
--
-- 订单状态机：
--   1 = 待支付 (pending_pay)     — 买家已下单，等待付款
--   2 = 待发货 (pending_ship)    — 买家已付款，等待卖家发货
--   3 = 待收货 (pending_receive) — 卖家已发货，等待买家确认收货
--   4 = 已完成 (completed)       — 买家已确认收货，交易完成
--   5 = 已取消 (cancelled)       — 订单已取消（终态）
--
-- 状态流转规则：
--   待支付 → 付款 → 待发货
--   待支付 → 取消 → 已取消
--   待发货 → 发货 → 待收货
--   待发货 → 取消 → 已取消
--   待收货 → 确认收货 → 已完成
--   已完成和已取消为终态，不可再变更
--
-- 索引设计（共5个）：
--   idx_buyer        : 买家查看"我买到的"订单列表
--   idx_seller       : 卖家查看"我卖出的"订单列表
--   idx_order_no     : 按订单号精确查找
--   idx_status       : 按状态筛选
--   idx_buyer_status : 联合索引，买家侧按状态筛选（高频：查待付款/待收货）
-- ============================================================================
CREATE TABLE IF NOT EXISTS orders (
    order_id         INT PRIMARY KEY AUTO_INCREMENT,
    order_no         VARCHAR(32)   UNIQUE NOT NULL COMMENT '订单号',
    buyer_id         INT           NOT NULL COMMENT '买家ID',
    receiver_name    VARCHAR(50)   NOT NULL COMMENT '收货人姓名',
    receiver_phone   VARCHAR(20)   NOT NULL COMMENT '收货人电话',
    seller_id        INT           NOT NULL COMMENT '卖家ID(冗余)',
    address          VARCHAR(500)  NOT NULL COMMENT '详细地址',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    buyer_note       VARCHAR(200)  NULL COMMENT '买家备注',
    logistics_company VARCHAR(50)  NULL COMMENT '物流公司',
    logistics_no     VARCHAR(50)   NULL COMMENT '物流单号',
    status           TINYINT       DEFAULT 1 COMMENT '1=待支付 2=待发货 3=待收货 4=完成 5=取消',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    pay_time         DATETIME      NULL COMMENT '付款时间',
    ship_time        DATETIME      NULL COMMENT '发货时间',
    complete_time    DATETIME      NULL COMMENT '完成时间',
    cancel_reason    VARCHAR(500)  NULL COMMENT '取消原因',
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_buyer(buyer_id),
    INDEX idx_seller(seller_id),
    INDEX idx_order_no(order_no),
    INDEX idx_status(status),
    INDEX idx_buyer_status(buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ============================================================================
-- 6. 订单明细表 (order_items)
-- ============================================================================
-- 存储订单中包含的商品信息（一个订单可包含多个商品）。
-- 记录商品的下单时快照价格，避免商品价格变动影响历史订单。
--
-- 字段说明：
--   item_id  : 明细唯一ID
--   order_id : 所属订单ID，外键 → orders.order_id，级联删除
--   goods_id : 商品ID，外键 → goods.goods_id（RESTRICT：售出商品不可直接删除）
--   price    : 商品下单时的单价快照
--   quantity : 购买数量，默认1
--
-- 约束：
--   uk_order_goods : 同一订单同一商品不能重复出现
-- ============================================================================
CREATE TABLE IF NOT EXISTS order_items (
    item_id   INT PRIMARY KEY AUTO_INCREMENT,
    order_id  INT            NOT NULL COMMENT '订单ID',
    goods_id  INT            NOT NULL COMMENT '商品ID',
    price     DECIMAL(10,2)  NOT NULL COMMENT '商品单价快照',
    quantity  INT            DEFAULT 1 COMMENT '数量',
    INDEX idx_order_id(order_id),
    INDEX idx_goods(goods_id),
    UNIQUE KEY uk_order_goods (order_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品表';

-- ============================================================================
-- 7. 收藏表 (favorites)
-- ============================================================================
-- 存储用户对商品的收藏关系（多对多）。
--
-- 字段说明：
--   favorite_id : 收藏记录唯一ID
--   user_id     : 收藏用户ID，外键 → users.user_id，级联删除
--   goods_id    : 被收藏商品ID，外键 → goods.goods_id，级联删除
--   create_time : 收藏时间
--
-- 约束：
--   uk_user_goods : 同一用户对同一商品只能收藏一次
-- ============================================================================
CREATE TABLE IF NOT EXISTS favorites (
    favorite_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT NOT NULL COMMENT '用户ID',
    goods_id    INT NOT NULL COMMENT '商品ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_goods (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- ============================================================================
-- 8. 聊天会话表 (conversations)
-- ============================================================================
-- 存储两个用户之间的聊天会话。
-- 采用 user1_id < user2_id 的约定，确保二人之间只有一条会话记录。
--
-- 设计模式：小ID在前
--   当用户 A（ID=3）与用户 B（ID=5）聊天时：
--     user1_id = 3, user2_id = 5
--   无论 A 还是 B 发起会话，都写入同一条记录，避免重复。
--
-- 字段说明：
--   conversation_id  : 会话唯一ID
--   user1_id         : 较小ID的用户，外键 → users
--   user2_id         : 较大ID的用户，外键 → users
--   last_message     : 最后一条消息的文本摘要（用于列表预览）
--   last_time        : 最后一条消息的时间
--   unread_cnt_user1 : user1 的未读消息计数
--   unread_cnt_user2 : user2 的未读消息计数
--   update_time      : 会话最后更新时间
--
-- 约束：
--   uk_pair : (user1_id, user2_id) 联合唯一，保证唯一会话
-- ============================================================================
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id INT PRIMARY KEY AUTO_INCREMENT,
    user1_id        INT NOT NULL COMMENT '较小用户ID',
    user2_id        INT NOT NULL COMMENT '较大用户ID',
    last_message    TEXT COMMENT '最后消息摘要',
    last_time       DATETIME COMMENT '最后消息时间',
    unread_cnt_user1 INT DEFAULT 0 COMMENT 'user1的未读数',
    unread_cnt_user2 INT DEFAULT 0 COMMENT 'user2的未读数',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_pair (user1_id, user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话';

-- ============================================================================
-- 9. 聊天消息表 (messages)
-- ============================================================================
-- 存储每条聊天消息，属于某个会话。
--
-- 字段说明：
--   message_id      : 消息唯一ID
--   conversation_id : 所属会话ID，外键 → conversations
--   sender_id       : 发送者用户ID
--   receiver_id     : 接收者用户ID
--   content         : 消息文本内容，TEXT 类型
--   image_path      : 图片消息的存储路径，可选
--   send_time       : 消息发送时间
--   is_read         : 是否已读：0=未读, 1=已读
--
-- 索引设计（共4个）：
--   idx_conv_time   : 按会话+时间查询消息列表（最常用的查询）
--   idx_conv_unread : 统计某会话的未读消息数
--   idx_sender      : 按发送者统计
--   idx_receiver    : 按接收者统计
-- ============================================================================
CREATE TABLE IF NOT EXISTS messages (
    message_id      INT PRIMARY KEY AUTO_INCREMENT,
    conversation_id INT NOT NULL COMMENT '会话ID',
    sender_id       INT NOT NULL COMMENT '发送者ID',
    receiver_id     INT NOT NULL COMMENT '接收者ID',
    content         TEXT COMMENT '消息内容',
    image_path      VARCHAR(500) COMMENT '图片路径',
    send_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    is_read         TINYINT DEFAULT 0 COMMENT '0=未读 1=已读',
    INDEX idx_conv_time (conversation_id, send_time),
    INDEX idx_conv_unread (conversation_id, is_read),
    INDEX idx_sender(sender_id),
    INDEX idx_receiver(receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

-- ============================================================================
-- 外键约束定义（共 13 条）
-- ============================================================================
-- 说明：
--   CASCADE  = 级联删除（如：删除用户 → 自动删除该用户的商品、收藏、消息等）
--   RESTRICT = 禁止删除（如：有商品属于某分类 → 禁止删除该分类）
--
-- 为什么部分使用 RESTRICT？
--   保护数据完整性：categories 被 goods 引用时不应被删除；
--   order_items 中的 goods_id 引用商品，已完成订单的商品不可删除。
-- ============================================================================

-- goods 表外键
ALTER TABLE goods ADD CONSTRAINT fk_goods_seller   FOREIGN KEY (seller_id)   REFERENCES users(user_id)       ON DELETE CASCADE;
ALTER TABLE goods ADD CONSTRAINT fk_goods_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE RESTRICT;

-- goods_images 表外键
ALTER TABLE goods_images ADD CONSTRAINT fk_images_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id) ON DELETE CASCADE;

-- orders 表外键
ALTER TABLE orders ADD CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- order_items 表外键
ALTER TABLE order_items ADD CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE;
ALTER TABLE order_items ADD CONSTRAINT fk_items_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id)   ON DELETE RESTRICT;

-- favorites 表外键
ALTER TABLE favorites ADD CONSTRAINT fk_fav_user  FOREIGN KEY (user_id)  REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE favorites ADD CONSTRAINT fk_fav_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id) ON DELETE CASCADE;

-- conversations 表外键（两个用户引用）
ALTER TABLE conversations ADD CONSTRAINT fk_conv_user1 FOREIGN KEY (user1_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conv_user2 FOREIGN KEY (user2_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- messages 表外键
ALTER TABLE messages ADD CONSTRAINT fk_msg_conv    FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_msg_sender  FOREIGN KEY (sender_id)      REFERENCES users(user_id)                ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id)    REFERENCES users(user_id)                ON DELETE CASCADE;

-- ============================================================================
-- 脚本执行完毕
-- 共创建 9 张表、20+ 个索引、13 条外键约束、8 条种子数据
-- ============================================================================
