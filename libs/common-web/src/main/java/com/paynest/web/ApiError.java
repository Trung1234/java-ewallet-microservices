package com.paynest.web;

/**
 * Error body returned by every service: {@code { "code": "...", "message": "..." }}.
 */
public record ApiError(String code, String message) {
}
