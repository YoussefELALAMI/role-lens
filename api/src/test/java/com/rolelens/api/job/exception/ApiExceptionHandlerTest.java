package com.rolelens.api.job.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void keepsTheStatusAndReasonOfAResponseStatusException() {
        ResponseEntity<ApiExceptionHandler.ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are accepted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ApiExceptionHandler.ApiError("Only PDF files are accepted"));
    }

    @Test
    void keepsOtherStatusCodesToo() {
        ResponseEntity<ApiExceptionHandler.ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File must not exceed 5 MB"));

        // Compare the number: Spring 7 has two enum names for 413.
        assertThat(response.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void turnsUnexpectedErrorsIntoAGenericServerError() {
        ResponseEntity<ApiExceptionHandler.ApiError> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://db/role_lens password=hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Internal details must never reach the client.
        assertThat(response.getBody()).isEqualTo(new ApiExceptionHandler.ApiError("An unexpected error occurred"));
    }
}
