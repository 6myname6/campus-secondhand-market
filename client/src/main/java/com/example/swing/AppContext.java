package com.example.swing;

public class AppContext {

    private static final AppContext INSTANCE = new AppContext();

    private Integer userId;
    private String username;
    private String token;
    private String role;

    private AppContext() {}

    public static AppContext getInstance() { return INSTANCE; }

    public void login(Integer userId, String username, String token, String role) {
        this.userId = userId;
        this.username = username;
        this.token = token;
        this.role = role;
    }

    public void logout() {
        this.userId = null;
        this.username = null;
        this.token = null;
        this.role = null;
    }

    public boolean isLoggedIn() { return token != null; }
    public boolean isAdmin() { return "admin".equals(role); }

    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getRole() { return role; }
}
