package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.ApiErrorResponse;
import com.khoaluan.digishop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(ex.getStatus().value())
                .code(ex.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code("VALIDATION_ERROR")
                .message("Dữ liệu không hợp lệ")
                .details(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        // Log full stack trace: without this, every unexpected 500 shows only
        // "Đã xảy ra lỗi hệ thống..." to the client with zero trace server-side.
        log.error("Unhandled exception while processing request", ex);
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code("INTERNAL_ERROR")
                .message("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau.")
                .build();
        return ResponseEntity.internalServerError().body(body);
    }
}