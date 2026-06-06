package com.example.netty;

/**
 * 服务端响应
 * {"success": true, "data": {...}, "message": null}
 */
public class Response {
    private boolean success;
    private Object data;
    private String message;
    int _reqId;

    private Response(boolean success, Object data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static Response ok(Object data) {
        return new Response(true, data, null);
    }

    public static Response ok() {
        return new Response(true, null, null);
    }

    public static Response fail(String message) {
        return new Response(false, null, message);
    }

    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getMessage() { return message; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setData(Object data) { this.data = data; }
    public void setMessage(String message) { this.message = message; }
}
