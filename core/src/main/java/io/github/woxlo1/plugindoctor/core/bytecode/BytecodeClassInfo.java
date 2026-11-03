package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BytecodeClassInfo(
        String name,
        int access,
        int minorVersion,
        int majorVersion,
        Optional<String> superName,
        List<String> interfaces,
        Optional<String> signature,
        Optional<String> sourceFile) {
    public BytecodeClassInfo {
        name = requireText(name, "name");
        if (minorVersion < 0 || minorVersion > 0xffff
                || majorVersion < 45 || majorVersion > 0xffff) {
            throw new IllegalArgumentException("Invalid class-file version");
        }
        superName = Objects.requireNonNull(superName, "superName");
        interfaces = List.copyOf(Objects.requireNonNull(interfaces, "interfaces"));
        signature = Objects.requireNonNull(signature, "signature");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
