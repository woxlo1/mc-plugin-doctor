package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.Objects;
import java.util.Optional;

public record BytecodeNode(
        String id,
        BytecodeNodeKind kind,
        String owner,
        Optional<String> name,
        Optional<String> descriptor,
        boolean internal) {
    public BytecodeNode {
        id = requireText(id, "id");
        Objects.requireNonNull(kind, "kind");
        owner = requireText(owner, "owner");
        name = Objects.requireNonNull(name, "name");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (kind == BytecodeNodeKind.CLASS) {
            if (name.isPresent() || descriptor.isPresent()) {
                throw new IllegalArgumentException("Class nodes must not have member details");
            }
        } else if (name.isEmpty() || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Member nodes require a name and descriptor");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
