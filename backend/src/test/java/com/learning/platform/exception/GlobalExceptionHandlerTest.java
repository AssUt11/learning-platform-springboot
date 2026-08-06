package com.learning.platform.exception;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GlobalExceptionHandlerTest {

    @Test
    void clientAbortShouldBeIgnoredWithoutWritingAnotherResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertDoesNotThrow(() -> handler.handleClientAbort(new ClientAbortException("Broken pipe")));
    }
}
