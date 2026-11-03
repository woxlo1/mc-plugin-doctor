package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.Objects;
import java.util.Optional;

public record BytecodeFieldInfo(
        String owner,
        String name,
        String descriptor,
        int access,
        Optional<String> signature,
        Optional<String> constantValue) {
    public BytecodeFieldInfo {
        owner = requireText(owner, "owner");
        name = requireText(name, "name");
        descriptor = requireText(descriptor, "descriptor");
        signature = Objects.requireNonNull(signature, "signature");
        constantValue = Objects.requireNonNull(constantValue, "constantValue");
    }

    public String id() {
        return "F:" + owner + "#" + name + ":" + descriptor;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
