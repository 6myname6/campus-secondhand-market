package com.example.netty;

import com.google.gson.JsonObject;

/**
 * 客户端请求
 * {"action": "UserService.findByUsername", "params": {"username": "test"}}
 */
public class Request {
    private String action;
    private JsonObject params;
    private int _reqId;

    public Request() {}
    public int get_reqId() { return _reqId; }
    public void set_reqId(int _reqId) { this._reqId = _reqId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public JsonObject getParams() { return params; }
    public void setParams(JsonObject params) { this.params = params; }

    public String getParam(String key) {
        return params != null && params.has(key) ? params.get(key).getAsString() : null;
    }

    public int getParamInt(String key) {
        return params != null && params.has(key) ? params.get(key).getAsInt() : 0;
    }
}
