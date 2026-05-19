package com.example.queueflow.common;

public class AppException extends RuntimeException {

    private final int status;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static AppException notFound(String msg)        { return new AppException(404, msg); }
    public static AppException badRequest(String msg)      { return new AppException(400, msg); }
    public static AppException conflict(String msg)        { return new AppException(409, msg); }
    public static AppException tooManyRequests(String msg) { return new AppException(429, msg); }
}
