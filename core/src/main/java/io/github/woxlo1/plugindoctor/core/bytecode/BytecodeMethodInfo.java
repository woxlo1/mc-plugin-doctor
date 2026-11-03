package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BytecodeMethodInfo(
        String owner,
        String name,
        String descriptor,
        int access,
        Optional<String> signature,
        List<String> exceptions) {
    public BytecodeMethodInfo {
        owner = requireText(owner, "owner");
        name = requireText(name, "name");
        descriptor = requireText(descriptor, "descriptor");
        signature = Objects.requireNonNull(signature, "signature");
        exceptions = List.copyOf(Objects.requireNonNull(exceptions, "exceptions"));
    }

    public String id() {
        return "M:" + owner + "#" + name + descriptor;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
