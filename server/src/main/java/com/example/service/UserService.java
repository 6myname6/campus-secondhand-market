package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户管理服务。
 *
 * 密码使用 BCrypt 哈希存储，登录时哈希比对。
 * 登录限流：同一用户名 5 秒内最多尝试 3 次。
 */
public class UserService extends BaseService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISABLED = 0;

    // 登录限流：用户名 → (首次失败时间, 失败次数)
    private final ConcurrentHashMap<String, long[]> loginRateMap = new ConcurrentHashMap<>();

    /** 根据用户名查询 */
    public User findByUsername(String username) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, User.class,
                        "SELECT * FROM users WHERE username = ?", username));
    }

    /** 根据ID查询 */
    public User findById(int userId) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, User.class,
                        "SELECT * FROM users WHERE user_id = ?", userId));
    }

    /**
     * 用户登录 — BCrypt 密码比对 + 限流。
     *
     * 限流规则：同一用户名 5 秒内最多尝试 3 次，
     * 超过则直接拒绝，不查数据库。
     */
    public User login(String username, String password) {
        // 限流检查
        checkLoginRate(username);

        return execTx(conn -> {
            User user = JdbcUtils.queryOne(conn, User.class,
                    "SELECT * FROM users WHERE username = ?", username);
            if (user == null) {
                recordLoginFailure(username);
                throw new RuntimeException("用户名或密码错误");
            }
            if (user.getStatus() == STATUS_DISABLED) {
                throw new RuntimeException("账号已被禁用");
            }
            if (!BCrypt.checkpw(password, user.getPassword())) {
                recordLoginFailure(username);
                throw new RuntimeException("用户名或密码错误");
            }
            // 登录成功，清除限流记录
            loginRateMap.remove(username);
            return user;
        });
    }

    private void checkLoginRate(String username) {
        long[] record = loginRateMap.get(username);
        if (record == null) return;
        long now = System.currentTimeMillis();
        if (now - record[0] > 5000) {
            loginRateMap.remove(username);
            return;
        }
        if (record[1] >= 3) {
            throw new RuntimeException("登录尝试过于频繁，请5秒后再试");
        }
    }

    private void recordLoginFailure(String username) {
        loginRateMap.compute(username, (k, v) -> {
            long now = System.currentTimeMillis();
            if (v == null || now - v[0] > 5000) return new long[]{now, 1};
            v[1]++;
            return v;
        });
    }

    /**
     * 用户注册 — BCrypt 哈希密码。
     */
    public int register(String username, String password, String phone, String email) {
        return execTx(conn -> {
            User exist = JdbcUtils.queryOne(conn, User.class,
                    "SELECT * FROM users WHERE username = ?", username);
            if (exist != null) {
                throw new RuntimeException("用户名已存在");
            }
            String hashedPwd = BCrypt.hashpw(password, BCrypt.gensalt());
            return JdbcUtils.insertAndGetKey(conn,
                    "INSERT INTO users (username, password, phone, email, role, status) " +
                    "VALUES (?, ?, ?, ?, 'user', ?)",
                    username, hashedPwd, phone, email, STATUS_ACTIVE);
        });
    }

    /** 更新用户状态（启用/禁用） */
    public void updateStatus(int userId, int status) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE users SET status = ? WHERE user_id = ?", status, userId);
            if (rows == 0) throw new RuntimeException("用户不存在");
        });
    }

    /** 更新用户信息 */
    public void updateProfile(int userId, String phone, String email, String avatar) {
        execTxVoid(conn -> {
            JdbcUtils.update(conn,
                    "UPDATE users SET phone = ?, email = ?, avatar = ? WHERE user_id = ?",
                    phone, email, avatar, userId);
        });
    }

    /** 用户列表 */
    public List<User> listUsers(int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, User.class,
                        "SELECT * FROM users ORDER BY create_time DESC", page, pageSize));
    }

    /** 总数 */
    public long countUsers() {
        return execTx(conn ->
                JdbcUtils.queryCount(conn, "SELECT COUNT(*) FROM users"));
    }
}
