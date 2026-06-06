package com.example;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心 JDBC 工具类 — 整个服务端的数据访问层基石。
 *
 * <h2>职责概述</h2>
 * 本类是项目中<b>唯一直接操作数据库的类</b>，所有 Service 层都通过它来读写数据库。
 * 它封装了以下能力：
 * <ol>
 *   <li><b>连接池管理</b> — 基于 HikariCP，启动时初始化，全局复用</li>
 *   <li><b>通用 CRUD</b> — queryList / queryOne / queryPage / queryCount / update / insert</li>
 *   <li><b>ORM 自动映射</b> — 反射 + 自定义注解（@Table / @Column / @Id）</li>
 *   <li><b>命名转换</b> — Java 驼峰命名 ↔ 数据库蛇形命名自动互转</li>
 *   <li><b>参数化查询</b> — 所有 SQL 操作使用 PreparedStatement，防 SQL 注入</li>
 *   <li><b>类型安全</b> — 支持 Integer / Long / String / BigDecimal / LocalDateTime / byte[] 等</li>
 *   <li><b>性能优化</b> — ConcurrentHashMap 缓存表名和列名映射，避免重复反射</li>
 * </ol>
 *
 * <h2>架构位置</h2>
 * <pre>
 *   Service 层 (GoodsService, OrderService, ...)
 *        │
 *        ▼
 *   JdbcUtils  (本类 — 唯一数据访问层)
 *        │
 *        ▼
 *   HikariCP 连接池 ──▶ MySQL 8.0
 * </pre>
 *
 * <h2>设计决策</h2>
 *
 * <h3>为什么不用 MyBatis / JPA？</h3>
 * <ul>
 *   <li><b>学习目的</b>：深入理解 ORM 底层原理（反射、注解处理、SQL 生成）</li>
 *   <li><b>项目规模</b>：仅 9 张表，自定义 ORM 完全够用</li>
 *   <li><b>依赖精简</b>：避免引入重量级框架，降低学习成本</li>
 * </ul>
 *
 * <h3>为什么用 HikariCP？</h3>
 * <ul>
 *   <li>业界公认性能最优的 JDBC 连接池</li>
 *   <li>配置简单，Spring Boot 2.x 默认连接池</li>
 *   <li>连接泄漏检测、空闲超时回收等健壮特性</li>
 * </ul>
 *
 * <h3>为什么用 PreparedStatement？</h3>
 * <ul>
 *   <li><b>防 SQL 注入</b>：参数与 SQL 语句分离，用户输入不会被当作 SQL 执行</li>
 *   <li><b>预编译优化</b>：数据库可缓存执行计划，重复执行时性能更好</li>
 * </ul>
 *
 * <h2>安全措施</h2>
 * <ol>
 *   <li>所有 SQL 参数通过 PreparedStatement.setXxx() 设置（非字符串拼接）</li>
 *   <li>数据库密码支持环境变量覆盖（DB_URL / DB_USER / DB_PASS），避免写死在配置文件</li>
 *   <li>INSERT 返回自增主键使用 RETURN_GENERATED_KEYS 而非 LAST_INSERT_ID()</li>
 * </ol>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.annotation.Table
 * @see com.example.annotation.Column
 * @see com.example.annotation.Id
 * @see com.example.service.BaseService
 * @since 1.0
 */
public class JdbcUtils {

    private static final Logger log = LoggerFactory.getLogger(JdbcUtils.class);

    /**
     * HikariCP 数据源（连接池），全局唯一单例。
     * 在 static 初始化块中完成配置和启动。
     */
    private static final HikariDataSource dataSource;

    // ==================== 静态初始化：连接池配置 ====================

    static {
        try {
            // 1. 从 classpath 加载数据库配置文件
            InputStream is = JdbcUtils.class.getClassLoader().getResourceAsStream("db.properties");
            Properties props = new Properties();
            props.load(is);

            // 2. 配置 HikariCP 连接池参数
            HikariConfig config = new HikariConfig();

            // 驱动类名（MySQL 8.0+ 使用 com.mysql.cj.jdbc.Driver）
            config.setDriverClassName(props.getProperty("jdbc.driver"));

            /*
             * 连接信息：优先使用环境变量，未设置时回退到配置文件。
             * 环境变量的优先级更高，方便容器化部署时动态覆盖配置。
             */
            config.setJdbcUrl(envOr(props, "DB_URL", props.getProperty("jdbc.url")));
            config.setUsername(envOr(props, "DB_USER", props.getProperty("jdbc.username")));
            config.setPassword(envOr(props, "DB_PASS", props.getProperty("jdbc.password")));

            // 连接池参数调优
            config.setMaximumPoolSize(20);       // 最大连接数（单服务实例，校内并发量适中）
            config.setMinimumIdle(5);             // 最小空闲连接数，避免冷启动延迟
            config.setIdleTimeout(300000);        // 空闲连接超时 5 分钟（毫秒）
            config.setConnectionTimeout(10000);   // 获取连接超时 10 秒（毫秒）

            // 3. 创建数据源（此时连接池启动，开始预热连接）
            dataSource = new HikariDataSource(config);
            log.info("HikariCP 连接池初始化成功（最大连接数: 20, 最小空闲: 5）");
        } catch (Exception e) {
            throw new RuntimeException("HikariCP 初始化失败，请检查 db.properties 配置", e);
        }
    }

    // ==================== 连接管理 ====================

    /**
     * 从连接池获取一个数据库连接。
     * <p>连接使用完毕后<b>必须调用 close() 归还</b>，否则会导致连接泄漏。</p>
     *
     * <h3>调用约定</h3>
     * 推荐在 try-with-resources 或 finally 块中确保关闭：
     * <pre>{@code
     * Connection conn = JdbcUtils.getConnection();
     * try {
     *     // 执行数据库操作...
     * } finally {
     *     JdbcUtils.close(conn, null, null);
     * }
     * }</pre>
     *
     * @return 一个可用的数据库连接
     * @throws SQLException 如果连接池已耗尽或数据库不可达
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 安全关闭数据库资源（Connection / Statement / ResultSet）。
     * <p>每个资源单独 try-catch，确保一个关闭失败不影响其他资源释放。</p>
     *
     * @param conn 数据库连接（可为 null）
     * @param stmt PreparedStatement（可为 null）
     * @param rs   ResultSet（可为 null）
     */
    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
        try { if (stmt != null) stmt.close(); } catch (SQLException ignored) {}
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    // ==================== 查询方法 ====================

    /**
     * 执行参数化查询，返回实体列表。
     *
     * <h3>核心流程</h3>
     * <ol>
     *   <li>创建 PreparedStatement（防 SQL 注入）</li>
     *   <li>通过 setParams() 设置参数</li>
     *   <li>执行查询，遍历 ResultSet</li>
     *   <li>每行数据通过 mapRow() 反射映射为实体对象</li>
     * </ol>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 查询所有在售商品（status = 1）
     * List<Goods> goodsList = JdbcUtils.queryList(
     *     conn, Goods.class,
     *     "SELECT * FROM goods WHERE status = ?", 1
     * );
     *
     * // 按分类和关键词搜索
     * List<Goods> results = JdbcUtils.queryList(
     *     conn, Goods.class,
     *     "SELECT * FROM goods WHERE category_id = ? AND title LIKE ?",
     *     categoryId, "%手机%"
     * );
     * }</pre>
     *
     * <h3>简单类型查询</h3>
     * 如果 clazz 是 Integer.class / String.class 等简单类型，
     * 则返回单列结果（如 {@code SELECT COUNT(*) FROM goods}）。
     *
     * @param conn   数据库连接
     * @param clazz  实体类（如 Goods.class）或简单类型（如 Integer.class）
     * @param sql    SQL 语句，使用 ? 占位符
     * @param params 参数值，按 ? 顺序传入
     * @param <T>    返回值泛型类型
     * @return 查询结果列表，无结果时返回空列表（非 null）
     * @throws RuntimeException 查询失败时抛出
     */
    public static <T> List<T> queryList(Connection conn, Class<T> clazz, String sql, Object... params) {
        List<T> result = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sql);  // 预编译 SQL（重要：防注入）
            setParams(stmt, params);             // 设置占位符参数值
            rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(mapRow(rs, clazz));   // 反射映射每行数据
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + sql, e);
        } finally {
            close(null, stmt, rs);               // 释放 Statement 和 ResultSet
        }
        return result;
    }

    /**
     * 查询单条记录。
     * <p>等价于 queryList().get(0)，无结果时返回 null。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * User user = JdbcUtils.queryOne(
     *     conn, User.class,
     *     "SELECT * FROM users WHERE user_id = ?", userId
     * );
     * if (user == null) {
     *     // 用户不存在
     * }
     * }</pre>
     *
     * @param conn   数据库连接
     * @param clazz  实体类
     * @param sql    SQL 语句
     * @param params 参数值
     * @param <T>    返回值泛型
     * @return 单条记录，无结果时返回 null
     */
    public static <T> T queryOne(Connection conn, Class<T> clazz, String sql, Object... params) {
        List<T> list = queryList(conn, clazz, sql, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 分页查询。
     *
     * <h3>实现方式</h3>
     * 自动在原始 SQL 末尾追加 {@code LIMIT offset, pageSize}。
     * <blockquote>
     *   offset = (page - 1) * pageSize  <br>
     *   第 1 页 → LIMIT 0, 10  <br>
     *   第 2 页 → LIMIT 10, 10
     * </blockquote>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>page 从 1 开始计数</li>
     *   <li>原始 SQL 不应包含 LIMIT，由本方法统一追加</li>
     *   <li>通常配合 queryCount() 计算总页数</li>
     * </ul>
     *
     * @param conn     数据库连接
     * @param clazz    实体类
     * @param sql      基础 SQL（不含 LIMIT）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @param params   参数值
     * @param <T>      返回值泛型
     * @return 当前页数据列表
     */
    public static <T> List<T> queryPage(Connection conn, Class<T> clazz,
                                         String sql, int page, int pageSize, Object... params) {
        // 构建分页 SQL：原 SQL + LIMIT ?, ?
        String pagedSql = sql + " LIMIT ?, ?";
        // 扩展参数数组：原始参数 + offset + pageSize
        Object[] pagedParams = Arrays.copyOf(params, params.length + 2);
        pagedParams[params.length] = (page - 1) * pageSize;   // offset
        pagedParams[params.length + 1] = pageSize;             // limit
        return queryList(conn, clazz, pagedSql, pagedParams);
    }

    /**
     * 执行 COUNT 查询，返回记录总数。
     *
     * <h3>使用示例（配合分页）</h3>
     * <pre>{@code
     * long total = JdbcUtils.queryCount(conn,
     *     "SELECT COUNT(*) FROM goods WHERE status = ?", 1);
     * int totalPages = (int) Math.ceil((double) total / pageSize);
     * }</pre>
     *
     * @param conn   数据库连接
     * @param sql    COUNT SQL（如 "SELECT COUNT(*) FROM goods WHERE status = ?"）
     * @param params 参数值
     * @return 记录总数，无结果返回 0
     */
    public static long queryCount(Connection conn, String sql, Object... params) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sql);
            setParams(stmt, params);
            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);  // COUNT 结果在第一列
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("COUNT 查询失败: " + sql, e);
        } finally {
            close(null, stmt, rs);
        }
    }

    // ==================== 写操作方法 ====================

    /**
     * 执行 INSERT / UPDATE / DELETE 语句。
     *
     * <h3>返回值</h3>
     * 受影响的行数（即 SQL 执行后改变的行数）。
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // UPDATE
     * int rows = JdbcUtils.update(conn,
     *     "UPDATE goods SET status = ? WHERE goods_id = ?", 3, goodsId);
     * if (rows == 0) { /* 没有行被更新 *&#47; }
     *
     * // DELETE
     * JdbcUtils.update(conn,
     *     "DELETE FROM favorites WHERE user_id = ? AND goods_id = ?", userId, goodsId);
     * }</pre>
     *
     * @param conn   数据库连接
     * @param sql    SQL 语句
     * @param params 参数值
     * @return 受影响的行数
     */
    public static int update(Connection conn, String sql, Object... params) {
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sql);
            setParams(stmt, params);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新失败: " + sql, e);
        } finally {
            close(null, stmt, null);
        }
    }

    /**
     * 执行 INSERT 并返回自增主键值。
     *
     * <h3>原理</h3>
     * 创建 PreparedStatement 时传入 {@link Statement#RETURN_GENERATED_KEYS}，
     * 执行后通过 getGeneratedKeys() 获取数据库生成的自增 ID。
     *
     * <h3>为什么不用 LAST_INSERT_ID()？</h3>
     * <ul>
     *   <li>RETURN_GENERATED_KEYS 是 JDBC 标准，跨数据库兼容</li>
     *   <li>LAST_INSERT_ID() 是 MySQL 特有函数，且在高并发下可能返回其他会话的 ID</li>
     *   <li>RETURN_GENERATED_KEYS 在同一连接内线程安全</li>
     * </ul>
     *
     * @param conn   数据库连接
     * @param sql    INSERT SQL
     * @param params 参数值
     * @return 自增主键值，失败返回 -1
     */
    public static int insertAndGetKey(Connection conn, String sql, Object... params) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            // RETURN_GENERATED_KEYS 让 JDBC 驱动返回自增主键
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            setParams(stmt, params);
            stmt.executeUpdate();
            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);  // 自增主键通常在第一列
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("插入失败: " + sql, e);
        } finally {
            close(null, stmt, rs);
        }
    }

    // ==================== ORM 实体便捷方法 ====================

    /**
     * 将实体对象插入数据库（自动 ORM 映射）。
     *
     * <h3>核心流程</h3>
     * <ol>
     *   <li>通过反射读取 {@code @Table} 注解获取表名</li>
     *   <li>遍历所有字段，通过 {@code @Column} 注解或驼峰转蛇形获取列名</li>
     *   <li>跳过 null 值字段（让数据库使用默认值）</li>
     *   <li>生成 INSERT INTO table (col1, col2) VALUES (?, ?) 语句</li>
     *   <li>执行并返回自增主键</li>
     * </ol>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Goods goods = new Goods();
     * goods.setTitle("二手 MacBook Pro");
     * goods.setPrice(new BigDecimal("5000.00"));
     * goods.setSellerId(1);
     * goods.setCategoryId(1);
     *
     * // 自动生成：INSERT INTO goods (title, price, seller_id, category_id) VALUES (?, ?, ?, ?)
     * int newId = JdbcUtils.insert(conn, goods);
     * </pre>
     *
     * @param conn   数据库连接
     * @param entity 实体对象（需标注 @Table 注解）
     * @param <T>    实体类型
     * @return 插入后生成的自增主键值
     * @throws RuntimeException 如果所有字段都为 null
     */
    public static <T> int insert(Connection conn, T entity) {
        Class<?> clazz = entity.getClass();
        String tableName = getTableName(clazz);            // 获取表名
        Map<String, Field> columnFieldMap = getColumnFieldMap(clazz);  // 获取列名→字段映射

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        StringBuilder placeholders = new StringBuilder();

        // 遍历所有字段，收集非 null 值的列名和值
        for (Map.Entry<String, Field> entry : columnFieldMap.entrySet()) {
            Field field = entry.getValue();
            field.setAccessible(true);  // 允许访问 private 字段
            try {
                Object value = field.get(entity);
                if (value == null) continue;  // 跳过 null 值，让数据库使用默认值

                if (!columns.isEmpty()) {
                    placeholders.append(", ");
                }
                columns.add(entry.getKey());       // 列名（已转换为蛇形）
                values.add(value);                 // 字段值
                placeholders.append("?");          // 占位符
            } catch (IllegalAccessException ignored) {}
        }

        if (columns.isEmpty()) {
            throw new RuntimeException("实体 " + clazz.getSimpleName() + " 没有非 null 字段，无法插入");
        }

        // 构建 SQL：INSERT INTO goods (title, price, seller_id) VALUES (?, ?, ?)
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName, String.join(", ", columns), placeholders);
        return insertAndGetKey(conn, sql, values.toArray());
    }

    /**
     * 根据主键更新实体对象的所有字段（自动 ORM 映射）。
     *
     * <h3>主键识别优先级</h3>
     * <ol>
     *   <li>标注了 {@code @Id} 注解的字段（推荐）</li>
     *   <li>若未找到 @Id，回退使用第一个字段（防御性设计）</li>
     * </ol>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Goods goods = JdbcUtils.queryOne(conn, Goods.class,
     *     "SELECT * FROM goods WHERE goods_id = ?", 1);
     * goods.setTitle("新标题");
     * goods.setPrice(new BigDecimal("4500.00"));
     *
     * // 自动生成：UPDATE goods SET title=?, price=?, ... WHERE goods_id=?
     * int rows = JdbcUtils.updateById(conn, goods);
     * }</pre>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>会更新<b>所有字段</b>（包括 null 值），不是仅更新变化的部分</li>
     *   <li>如果只想更新部分字段，请使用 update() 方法手写 SQL</li>
     * </ul>
     *
     * @param conn   数据库连接
     * @param entity 实体对象（需包含主键值）
     * @param <T>    实体类型
     * @return 受影响的行数（正常为 1）
     */
    public static <T> int updateById(Connection conn, T entity) {
        Class<?> clazz = entity.getClass();
        String tableName = getTableName(clazz);
        Map<String, Field> columnFieldMap = getColumnFieldMap(clazz);

        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        String idColumn = null;
        Object idValue = null;

        // 查找主键字段（优先 @Id，回退到第一个字段）
        Field idField = getIdField(clazz, columnFieldMap);

        // 遍历所有字段
        for (Map.Entry<String, Field> entry : columnFieldMap.entrySet()) {
            Field field = entry.getValue();
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                if (field.equals(idField)) {
                    // 主键字段：记录列名和值，最后放到 WHERE 子句中
                    idColumn = entry.getKey();
                    idValue = value;
                    continue;
                }
                // 非主键字段：加入 SET 子句
                setClauses.add(entry.getKey() + " = ?");
                values.add(value);
            } catch (IllegalAccessException ignored) {}
        }

        if (idColumn == null || idValue == null) {
            throw new RuntimeException("主键字段未找到，请检查实体类是否标注了 @Id 注解");
        }
        values.add(idValue);  // 主键值放到参数列表最后

        // 构建 SQL：UPDATE goods SET title=?, price=?, ... WHERE goods_id=?
        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                tableName, String.join(", ", setClauses), idColumn);
        return update(conn, sql, values.toArray());
    }

    /**
     * 按主键删除记录。
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 按主键删除用户
     * JdbcUtils.deleteById(conn, User.class, 5);
     *
     * // 生成的 SQL：DELETE FROM users WHERE user_id = ?
     * }</pre>
     *
     * @param conn    数据库连接
     * @param clazz   实体类
     * @param idValue 主键值
     * @param <T>     实体类型
     * @return 受影响的行数
     */
    public static <T> int deleteById(Connection conn, Class<T> clazz, Object idValue) {
        String tableName = getTableName(clazz);
        Map<String, Field> columnFieldMap = getColumnFieldMap(clazz);
        String idColumn = getIdColumn(clazz, columnFieldMap);
        return update(conn, "DELETE FROM " + tableName + " WHERE " + idColumn + " = ?", idValue);
    }

    // ==================== 内部辅助：反射 & 注解处理 ====================

    /** 表名缓存（ConcurrentHashMap 保证线程安全） */
    private static final Map<Class<?>, String> TABLE_CACHE = new ConcurrentHashMap<>();

    /** 列名 → 字段 映射缓存（ConcurrentHashMap 保证线程安全） */
    private static final Map<Class<?>, Map<String, Field>> COLUMN_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取实体类对应的数据库表名。
     *
     * <h3>优先级</h3>
     * <ol>
     *   <li>{@code @Table("表名")} 注解的值（推荐）</li>
     *   <li>类名驼峰转蛇形（如 OrderItem → order_item）</li>
     * </ol>
     *
     * <h3>缓存策略</h3>
     * 使用 ConcurrentHashMap.computeIfAbsent() 保证线程安全，
     * 每个类只会反射一次，后续直接返回缓存值。
     *
     * @param clazz 实体类
     * @return 数据库表名
     */
    private static String getTableName(Class<?> clazz) {
        return TABLE_CACHE.computeIfAbsent(clazz, c -> {
            Table table = c.getAnnotation(Table.class);
            // 有 @Table 注解 → 用注解值；否则 → 驼峰转蛇形
            return table != null ? table.value() : camelToSnake(c.getSimpleName());
        });
    }

    /**
     * 获取实体类的"数据库列名 → Java 字段"映射。
     *
     * <h3>映射规则（按优先级）</h3>
     * <ol>
     *   <li>标注了 {@code @Column("列名")} → 使用指定的列名</li>
     *   <li>未标注 → 将 Java 字段名驼峰转蛇形作为列名</li>
     * </ol>
     *
     * <h3>线程安全</h3>
     * 使用 ConcurrentHashMap + computeIfAbsent 保证。
     * 返回的 LinkedHashMap 保证字段的声明顺序，方便调试和日志输出。
     *
     * @param clazz 实体类
     * @return LinkedHashMap，key=数据库列名，value=Java Field 对象
     */
    private static Map<String, Field> getColumnFieldMap(Class<?> clazz) {
        return COLUMN_CACHE.computeIfAbsent(clazz, c -> {
            // LinkedHashMap 保证遍历顺序 = 字段声明顺序
            Map<String, Field> map = new LinkedHashMap<>();
            for (Field field : c.getDeclaredFields()) {
                // 跳过 static 字段（它们不属于实例数据）
                if (Modifier.isStatic(field.getModifiers())) continue;

                Column col = field.getAnnotation(Column.class);
                // 有 @Column 注解 → 用注解值；否则 → 驼峰转蛇形
                String columnName = col != null ? col.value() : camelToSnake(field.getName());
                map.put(columnName, field);
            }
            return map;
        });
    }

    /**
     * 查找 @Id 标注的字段，未找到则返回第一个字段。
     *
     * <h3>查找策略</h3>
     * <ol>
     *   <li>遍历 clazz 的所有声明字段，找 @Id 注解</li>
     *   <li>若找到 → 返回该字段</li>
     *   <li>若未找到 → 回退到 columnFieldMap 的第一个字段（防御性设计）</li>
     * </ol>
     *
     * @param clazz          实体类
     * @param columnFieldMap 列名→字段映射
     * @return 主键字段
     */
    private static Field getIdField(Class<?> clazz, Map<String, Field> columnFieldMap) {
        // 遍历所有字段查找 @Id
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) return f;
        }
        // 回退：使用第一个字段作为主键
        return columnFieldMap.values().iterator().next();
    }

    /**
     * 获取主键列名（数据库列名，非 Java 字段名）。
     */
    private static String getIdColumn(Class<?> clazz, Map<String, Field> columnFieldMap) {
        // 查找 @Id 标注的字段
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(Id.class)) {
                Column col = f.getAnnotation(Column.class);
                // 有 @Column → 用注解值；否则 → 驼峰转蛇形
                return col != null ? col.value() : camelToSnake(f.getName());
            }
        }
        // 回退到第一列
        return columnFieldMap.keySet().iterator().next();
    }

    /**
     * 将 ResultSet 当前行的数据映射为实体对象。
     *
     * <h3>核心流程</h3>
     * <ol>
     *   <li>若 clazz 是简单类型（Integer/String 等）→ 直接读取第一列</li>
     *   <li>否则通过反射创建实体实例</li>
     *   <li>遍历 ResultSet 的每一列</li>
     *   <li>在 columnFieldMap 中查找匹配的 Java 字段</li>
     *   <li>通过 getColumnValue() 读取值并设置到字段</li>
     * </ol>
     *
     * <h3>字段匹配策略</h3>
     * <ul>
     *   <li>先查 columnFieldMap（@Column 标注的 + 未标注但驼峰转蛇形后匹配的）</li>
     *   <li>再查 fallback（未标注 @Column 的字段的蛇形名）</li>
     *   <li>都找不到就跳过该列（兼容 JOIN 查询多出的列）</li>
     * </ul>
     *
     * @param rs    ResultSet，定位到当前行
     * @param clazz 目标实体类
     * @param <T>   实体类型
     * @return 填充了数据的实体对象
     */
    private static <T> T mapRow(ResultSet rs, Class<T> clazz) {
        try {
            // ---- 处理单列简单类型查询 ----
            // 场景：SELECT COUNT(*), SELECT MAX(price), 等
            if (clazz == Integer.class || clazz == int.class) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : clazz.cast(v);
            }
            if (clazz == Long.class || clazz == long.class) {
                long v = rs.getLong(1);
                return rs.wasNull() ? null : clazz.cast(v);
            }
            if (clazz == String.class) {
                return clazz.cast(rs.getString(1));
            }
            if (clazz == Double.class || clazz == double.class) {
                double v = rs.getDouble(1);
                return rs.wasNull() ? null : clazz.cast(v);
            }
            if (clazz == Float.class || clazz == float.class) {
                float v = rs.getFloat(1);
                return rs.wasNull() ? null : clazz.cast(v);
            }
            if (clazz == Boolean.class || clazz == boolean.class) {
                boolean v = rs.getBoolean(1);
                return rs.wasNull() ? null : clazz.cast(v);
            }
            if (clazz == BigDecimal.class) {
                return clazz.cast(rs.getBigDecimal(1));
            }

            // ---- 实体类映射 ----
            // 通过反射创建实例（要求有无参构造器）
            T obj = clazz.getDeclaredConstructor().newInstance();
            Map<String, Field> columnFieldMap = getColumnFieldMap(clazz);

            // 构建回退映射：未标注 @Column 的字段的 蛇形名 → Field
            Map<String, Field> fallback = new LinkedHashMap<>();
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getAnnotation(Column.class) == null) {
                    fallback.put(camelToSnake(f.getName()), f);
                }
            }

            // 遍历结果集的每一列
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String columnName = meta.getColumnName(i);  // 数据库返回的列名
                Field field = columnFieldMap.get(columnName);  // 先在注解映射中查找
                if (field == null) field = fallback.get(columnName);  // 然后在回退映射中查找
                if (field == null) continue;  // 找不到匹配字段 → 跳过此列

                // 根据字段类型读取列值
                Object value = getColumnValue(rs, i, field.getType());
                if (value != null) {
                    field.setAccessible(true);
                    field.set(obj, value);  // 反射设置字段值
                }
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("映射行数据到 " + clazz.getName() + " 失败", e);
        }
    }

    /**
     * 为 PreparedStatement 设置参数值。
     *
     * <h3>参数类型映射</h3>
     * <table>
     *   <tr><th>Java 类型</th><th>JDBC 方法</th></tr>
     *   <tr><td>null</td><td>setNull(Types.NULL)</td></tr>
     *   <tr><td>String</td><td>setString()</td></tr>
     *   <tr><td>Integer</td><td>setInt()</td></tr>
     *   <tr><td>Long</td><td>setLong()</td></tr>
     *   <tr><td>BigDecimal</td><td>setBigDecimal()</td></tr>
     *   <tr><td>Double</td><td>setDouble()</td></tr>
     *   <tr><td>LocalDateTime</td><td>setTimestamp()</td></tr>
     *   <tr><td>LocalDate</td><td>setDate()</td></tr>
     *   <tr><td>其他</td><td>setObject()</td></tr>
     * </table>
     *
     * <h3>安全性</h3>
     * 使用类型化的 setXxx() 方法而非 setObject()，防止 SQL 类型混淆注入。
     *
     * @param stmt   PreparedStatement 对象
     * @param params 参数值数组
     * @throws SQLException 参数设置失败
     */
    private static void setParams(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            // 根据参数的实际类型调用对应的 setXxx() 方法
            if (param == null) {
                stmt.setNull(i + 1, Types.NULL);          // null → SQL NULL
            } else if (param instanceof String) {
                stmt.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                stmt.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                stmt.setLong(i + 1, (Long) param);
            } else if (param instanceof BigDecimal) {
                stmt.setBigDecimal(i + 1, (BigDecimal) param);
            } else if (param instanceof Double) {
                stmt.setDouble(i + 1, (Double) param);
            } else if (param instanceof Float) {
                stmt.setFloat(i + 1, (Float) param);
            } else if (param instanceof Boolean) {
                stmt.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof LocalDateTime) {
                // LocalDateTime → java.sql.Timestamp
                stmt.setTimestamp(i + 1, Timestamp.valueOf((LocalDateTime) param));
            } else if (param instanceof java.time.LocalDate) {
                // LocalDate → java.sql.Date
                stmt.setDate(i + 1, java.sql.Date.valueOf((java.time.LocalDate) param));
            } else {
                // 兜底：其他类型使用 setObject
                stmt.setObject(i + 1, param);
            }
        }
    }

    /**
     * 从 ResultSet 的指定列读取值，按目标 Java 类型转换。
     *
     * <h3>NULL 处理</h3>
     * 先调用 rs.getObject() 触发 wasNull() 检测，
     * 如果该列为 SQL NULL 则直接返回 null。
     *
     * @param rs         ResultSet
     * @param index      列索引（从 1 开始）
     * @param targetType 目标 Java 类型
     * @return 列值，NULL 时返回 null
     */
    private static Object getColumnValue(ResultSet rs, int index, Class<?> targetType) throws SQLException {
        // 先探测是否为 SQL NULL
        rs.getObject(index);
        if (rs.wasNull()) return null;

        // 根据目标类型调用对应的 getter
        if (targetType == String.class) {
            return rs.getString(index);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return rs.getInt(index);
        }
        if (targetType == Long.class || targetType == long.class) {
            return rs.getLong(index);
        }
        if (targetType == BigDecimal.class) {
            return rs.getBigDecimal(index);
        }
        if (targetType == Double.class || targetType == double.class) {
            return rs.getDouble(index);
        }
        if (targetType == Float.class || targetType == float.class) {
            return rs.getFloat(index);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return rs.getBoolean(index);
        }
        if (targetType == LocalDateTime.class) {
            // java.sql.Timestamp → LocalDateTime
            Timestamp ts = rs.getTimestamp(index);
            return ts != null ? ts.toLocalDateTime() : null;
        }
        if (targetType == java.time.LocalDate.class) {
            // java.sql.Date → LocalDate
            java.sql.Date date = rs.getDate(index);
            return date != null ? date.toLocalDate() : null;
        }
        if (targetType == byte[].class) {
            return rs.getBytes(index);
        }

        // 兜底：使用通用 getObject
        return rs.getObject(index);
    }

    // ==================== 内部辅助：命名转换 ====================

    /**
     * 驼峰命名 → 蛇形命名转换。
     *
     * <h3>转换规则</h3>
     * <ul>
     *   <li>遇到大写字母时，在前面插入下划线并转小写</li>
     *   <li>小写字母直接保留</li>
     * </ul>
     *
     * <h3>示例</h3>
     * <table>
     *   <tr><th>驼峰</th><th>蛇形</th></tr>
     *   <tr><td>userId</td><td>user_id</td></tr>
     *   <tr><td>createTime</td><td>create_time</td></tr>
     *   <tr><td>OrderItem</td><td>order_item</td></tr>
     *   <tr><td>ABC</td><td>_a_b_c</td></tr>
     * </table>
     *
     * <h3>注意事项</h3>
     * 连续大写字母会产生连续下划线（如 ABC → _a_b_c），
     * 因此实体类应遵循 Java 命名规范，避免全大写字段名。
     *
     * @param camel 驼峰命名字符串
     * @return 蛇形命名字符串
     */
    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (Character.isUpperCase(ch)) {
                sb.append('_');                         // 大写字母前加下划线
                sb.append(Character.toLowerCase(ch));   // 转小写
            } else {
                sb.append(ch);                          // 小写直接保留
            }
        }
        return sb.toString();
    }

    /**
     * 读取环境变量，如果不存在则使用回退值。
     *
     * <h3>设计目的</h3>
     * 支持容器化部署时通过环境变量动态覆盖数据库配置，
     * 避免敏感信息写死在配置文件中。
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 优先使用环境变量 DB_URL，否则用 db.properties 中的值
     * String url = envOr(props, "DB_URL", props.getProperty("jdbc.url"));
     * }</pre>
     *
     * @param props    Properties 配置对象（保留备用）
     * @param envKey   环境变量名
     * @param fallback 回退值
     * @return 环境变量值（若存在），否则返回回退值
     */
    private static String envOr(Properties props, String envKey, String fallback) {
        String env = System.getenv(envKey);
        return env != null ? env : fallback;
    }
}
