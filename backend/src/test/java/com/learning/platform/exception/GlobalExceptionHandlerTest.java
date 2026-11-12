package com.learning.platform.exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void clientAbortShouldBeIgnoredWithoutWritingAnotherResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertDoesNotThrow(() ->
            handler.handleClientAbort(new ClientAbortException("Broken pipe"))
        );
    }
}
