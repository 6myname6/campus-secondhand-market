USE second_hand_trading;

CREATE TABLE IF NOT EXISTS users (
    user_id     INT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)   UNIQUE NOT NULL COMMENT '账号',
    password    VARCHAR(255)  NOT NULL COMMENT '密码（加密）',
    phone       VARCHAR(20)   NULL,
    email       VARCHAR(100)  NULL,
    avatar      VARCHAR(500)  DEFAULT '/images/default_avatar.png',
    role        ENUM('user', 'admin') DEFAULT 'user' COMMENT '用户角色',
    status      TINYINT       DEFAULT 1 COMMENT '1=正常 0=禁用',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username(username),
    INDEX idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS categories (
    category_id   INT PRIMARY KEY AUTO_INCREMENT,
    parent_id     INT          DEFAULT 0 COMMENT '父分类ID，0表示顶级',
    category_name VARCHAR(50)  NOT NULL UNIQUE,
    sort_order    INT          DEFAULT 0,
    INDEX idx_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

INSERT IGNORE INTO categories (category_id, category_name, sort_order) VALUES
(1, '电子产品', 0),
(2, '书籍教材', 1),
(3, '生活用品', 2),
(4, '运动户外', 3),
(5, '服饰鞋包', 4),
(6, '数码配件', 5),
(7, '文具办公', 6),
(8, '其他', 7);

CREATE TABLE IF NOT EXISTS goods (
    goods_id       INT PRIMARY KEY AUTO_INCREMENT,
    seller_id      INT            NOT NULL,
    category_id    INT            NOT NULL,
    title          VARCHAR(200)   NOT NULL,
    description    TEXT,
    price          DECIMAL(10,2)  NOT NULL,
    original_price DECIMAL(10,2)  NULL,
    status         TINYINT        DEFAULT 1 COMMENT '1=在售 2=已售 3=下架',
    view_count     INT            DEFAULT 0,
    create_time    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seller(seller_id),
    INDEX idx_category(category_id),
    INDEX idx_status(status),
    INDEX idx_price(price),
    INDEX idx_create_time(create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

CREATE TABLE IF NOT EXISTS goods_images (
    image_id   INT PRIMARY KEY AUTO_INCREMENT,
    goods_id   INT NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    INDEX idx_goods(goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

CREATE TABLE IF NOT EXISTS orders (
    order_id         INT PRIMARY KEY AUTO_INCREMENT,
    order_no         VARCHAR(32)   UNIQUE NOT NULL COMMENT '订单号',
    buyer_id         INT           NOT NULL,
    receiver_name    VARCHAR(50)   NOT NULL COMMENT '收货人姓名',
    receiver_phone   VARCHAR(20)   NOT NULL COMMENT '收货人电话',
    seller_id        INT           NOT NULL COMMENT '卖家ID',
    address          VARCHAR(500)  NOT NULL COMMENT '详细地址',
    total_amount     DECIMAL(10,2) NOT NULL,
    buyer_note       VARCHAR(200)  NULL,
    logistics_company VARCHAR(50)  NULL COMMENT '物流公司',
    logistics_no     VARCHAR(50)   NULL COMMENT '物流单号',
    status           TINYINT       DEFAULT 1 COMMENT '1=待支付 2=待发货 3=待收货 4=完成 5=取消',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    pay_time         DATETIME      NULL,
    ship_time        DATETIME      NULL,
    complete_time    DATETIME      NULL,
    cancel_reason    VARCHAR(500)  NULL,
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_buyer(buyer_id),
    INDEX idx_seller(seller_id),
    INDEX idx_order_no(order_no),
    INDEX idx_status(status),
    INDEX idx_buyer_status(buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS order_items (
    item_id   INT PRIMARY KEY AUTO_INCREMENT,
    order_id  INT            NOT NULL,
    goods_id  INT            NOT NULL,
    price     DECIMAL(10,2)  NOT NULL,
    quantity  INT            DEFAULT 1,
    INDEX idx_order_id(order_id),
    INDEX idx_goods(goods_id),
    UNIQUE KEY uk_order_goods (order_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品表';

CREATE TABLE IF NOT EXISTS favorites (
    favorite_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT NOT NULL,
    goods_id    INT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_goods (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS conversations (
    conversation_id INT PRIMARY KEY AUTO_INCREMENT,
    user1_id        INT NOT NULL COMMENT '较小用户ID',
    user2_id        INT NOT NULL COMMENT '较大用户ID',
    last_message    TEXT,
    last_time       DATETIME,
    unread_cnt_user1 INT DEFAULT 0 COMMENT 'user1的未读数',
    unread_cnt_user2 INT DEFAULT 0 COMMENT 'user2的未读数',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (user1_id, user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话';

CREATE TABLE IF NOT EXISTS messages (
    message_id      INT PRIMARY KEY AUTO_INCREMENT,
    conversation_id INT NOT NULL,
    sender_id       INT NOT NULL,
    receiver_id     INT NOT NULL,
    content         TEXT,
    image_path      VARCHAR(500),
    send_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_read         TINYINT DEFAULT 0,
    INDEX idx_conv_time (conversation_id, send_time),
    INDEX idx_conv_unread (conversation_id, is_read),
    INDEX idx_sender(sender_id),
    INDEX idx_receiver(receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

-- ==================== 外键约束 ====================
ALTER TABLE goods ADD CONSTRAINT fk_goods_seller FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE goods ADD CONSTRAINT fk_goods_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE RESTRICT;
ALTER TABLE goods_images ADD CONSTRAINT fk_images_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id) ON DELETE CASCADE;
ALTER TABLE orders ADD CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE order_items ADD CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE;
ALTER TABLE order_items ADD CONSTRAINT fk_items_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id) ON DELETE RESTRICT;
ALTER TABLE favorites ADD CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE favorites ADD CONSTRAINT fk_fav_goods FOREIGN KEY (goods_id) REFERENCES goods(goods_id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conv_user1 FOREIGN KEY (user1_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conv_user2 FOREIGN KEY (user2_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE messages ADD CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE;
