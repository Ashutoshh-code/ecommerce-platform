package com.example.ecommerceplatform.CartService.Exception;

public class DownstreamServiceUnavailableException extends RuntimeException {

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " is unavailable, please try again later", cause);
    }
}
