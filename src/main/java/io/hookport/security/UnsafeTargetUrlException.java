package io.hookport.security;

public class UnsafeTargetUrlException
        extends RuntimeException {

    public UnsafeTargetUrlException(String message) {
        super(message);
    }
}