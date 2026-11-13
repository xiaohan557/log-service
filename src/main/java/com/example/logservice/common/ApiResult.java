package com.example.logservice.common;

public class ApiResult<T> {
    private int code;   // 0 成功，非 0 失败
    private String msg; // 提示信息
    private T data;     // 实际数据

    public ApiResult() {}
    public ApiResult(int code, String msg, T data) {
        this.code = code; this.msg = msg; this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }
    public static <T> ApiResult<T> error(String msg) {
        return new ApiResult<>(1, msg, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
