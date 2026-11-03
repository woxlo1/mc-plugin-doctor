package io.github.woxlo1.plugindoctor.core.bytecode;

public enum BytecodeReferenceKind {
    EXTENDS,
    IMPLEMENTS,
    FIELD_TYPE,
    METHOD_PARAMETER,
    METHOD_RETURN,
    METHOD_CALL,
    FIELD_READ,
    FIELD_WRITE,
    TYPE_USE,
    CLASS_LITERAL,
    BOOTSTRAP,
    EXCEPTION
}
