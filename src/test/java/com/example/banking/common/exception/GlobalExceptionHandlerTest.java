package com.example.banking.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;


import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private HttpServletRequest request() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts/999");
        return request;
    }

    @Test
    void notFoundErrorsReturn404() {
        ResponseEntity<ApiError> response = handler.handleNotFound(
                new ResourceNotFoundException("Account not found."),
                request()
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertEquals("Account not found.", response.getBody().message());
    }

    @Test
    void invalidArgumentsReturn400() {
        ResponseEntity<ApiError> response =
                handler.handleInvalidArgument(
                        new IllegalArgumentException("Invalid account ID."),
                        request()
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void conflictsReturn409() {
        ResponseEntity<ApiError> response = handler.handleConflict(
                new ConflictException("Customer already exists."),
                request()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
    }
}