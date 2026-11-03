package io.github.woxlo1.plugindoctor.foundation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeBaselineTest {
    @Test
    void testRuntimeMeetsTheProjectBaseline() {
        assertTrue(
                Runtime.version().feature() >= 17,
                "The test runtime must be Java 17 or newer");
    }
}
