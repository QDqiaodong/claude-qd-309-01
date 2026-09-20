package com.car.wash.dto;

public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
