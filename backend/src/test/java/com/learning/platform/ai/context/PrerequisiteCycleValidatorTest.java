package com.learning.platform.ai.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrerequisiteCycleValidatorTest {

    private final PrerequisiteCycleValidator validator = new PrerequisiteCycleValidator();

    @Test
    void rejectsSelfReferencesAndIndirectCycles() {
        assertTrue(validator.createsCycle(Collections.emptyList(), "java", "java"));
        assertTrue(
            validator.createsCycle(
                Arrays.asList(
                    Map.of("prerequisiteKey", "basic", "targetKey", "oop"),
                    Map.of("prerequisiteKey", "oop", "targetKey", "spring")
                ),
                "spring",
                "basic"
            )
        );
    }

    @Test
    void acceptsAcyclicDependency() {
        assertFalse(
            validator.createsCycle(
                Collections.singletonList(Map.of("prerequisiteKey", "basic", "targetKey", "oop")),
                "oop",
                "spring"
            )
        );
    }
}
