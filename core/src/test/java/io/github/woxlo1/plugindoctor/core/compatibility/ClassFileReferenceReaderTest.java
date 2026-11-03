package io.github.woxlo1.plugindoctor.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.jar.JarReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClassFileReferenceReaderTest {
    @Test
    void readsClassVersionAndClassAndMemberReferences() throws IOException {
        String path = "/io/github/woxlo1/plugindoctor/core/compatibility/"
                + "ClassFileReferenceReaderTest$Fixture.class";
        ClassFileInfo info;
        try (InputStream input = ClassFileReferenceReaderTest.class.getResourceAsStream(path)) {
            info = new ClassFileReferenceReader().read(input, path.substring(1));
        }

        assertEquals(
                "io.github.woxlo1.plugindoctor.core.compatibility.ClassFileReferenceReaderTest$Fixture",
                info.className());
        assertEquals(17, info.majorVersion() - 44);
        assertTrue(info.referencedClasses().contains(JarReader.class.getName()));
        assertTrue(info.referencedClasses().contains(Path.class.getName()));
        assertTrue(info.apiReferences().contains(ApiReference.memberReference(
                ApiReferenceKind.METHOD,
                JarReader.class.getName(),
                "read",
                "(Ljava/nio/file/Path;)Lio/github/woxlo1/plugindoctor/core/jar/JarArchive;")));
    }

    @Test
    void rejectsInvalidMagicAndTruncatedClassFiles() {
        ClassFileReferenceReader reader = new ClassFileReferenceReader();

        assertThrows(
                IOException.class,
                () -> reader.read(new ByteArrayInputStream(new byte[8]), "Invalid.class"));
        assertThrows(
                IOException.class,
                () -> reader.read(
                        new ByteArrayInputStream(new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba}),
                        "Truncated.class"));
    }

    private static final class Fixture {
        private static Path read(JarReader reader, Path path) {
            reader.read(path);
            return path;
        }
    }
}
