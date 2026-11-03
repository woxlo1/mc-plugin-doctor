package io.github.woxlo1.plugindoctor.core.compatibility;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.jar.JarArchiveEntry;
import io.github.woxlo1.plugindoctor.core.jar.JarReadLimits;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ClassFileScanner {
    private static final String ANALYZER_ID = "compatibility.class-file";
    private final JarReadLimits limits;
    private final ClassFileReferenceReader referenceReader;

    public ClassFileScanner() {
        this(JarReadLimits.defaults(), new ClassFileReferenceReader());
    }

    public ClassFileScanner(JarReadLimits limits, ClassFileReferenceReader referenceReader) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.referenceReader = Objects.requireNonNull(referenceReader, "referenceReader");
    }

    public ClassFileScanResult scan(JarAnalysisReport report) {
        return scan(report, (path, contents) -> {});
    }

    /** Supplies bounded, hash-verified class bytes; consumers must not modify the byte array. */
    public ClassFileScanResult scan(
            JarAnalysisReport report, BiConsumer<String, byte[]> classFileConsumer) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(classFileConsumer, "classFileConsumer");
        if (report.entries().size() > limits.maximumEntries()) {
            throw rejected(
                    "CLASS_ENTRY_LIMIT_EXCEEDED",
                    "Archive report exceeds the class scanning entry limit.");
        }
        Map<String, JarArchiveEntry> expectedEntries = new HashMap<>();
        Set<String> duplicatePaths = new HashSet<>();
        for (JarArchiveEntry archiveEntry : report.entries()) {
            if (!archiveEntry.directory() && archiveEntry.path().endsWith(".class")) {
                if (expectedEntries.putIfAbsent(archiveEntry.path(), archiveEntry) != null) {
                    duplicatePaths.add(archiveEntry.path());
                }
            }
        }

        List<ClassFileInfo> classes = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        Set<String> scannedPaths = new HashSet<>();
        Set<String> encounteredPaths = new HashSet<>();
        Set<String> observedDuplicatePaths = new HashSet<>();
        long totalBytes = 0;
        try (ZipFile zipFile = new ZipFile(report.source().toFile())) {
            var entries = zipFile.entries();
            int entryCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > limits.maximumEntries()) {
                    throw rejected(
                            "CLASS_ENTRY_LIMIT_EXCEEDED",
                            "Archive exceeds the class scanning entry limit.");
                }
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String path = entry.getName();
                JarArchiveEntry expected = expectedEntries.get(path);
                boolean firstOccurrence = encounteredPaths.add(path);
                if (duplicatePaths.contains(path)) {
                    if (!firstOccurrence) {
                        observedDuplicatePaths.add(path);
                    }
                    continue;
                }
                if (expected == null || !firstOccurrence || !scannedPaths.add(path)) {
                    throw rejected(
                            "CLASS_ARCHIVE_CHANGED",
                            "Class entry inventory changed after JAR analysis: " + path);
                }
                validateEntryMetadata(entry, expected, path);
                long remainingTotal = limits.maximumTotalUncompressedBytes() - totalBytes;
                byte[] contents = readEntry(zipFile, entry, remainingTotal, path);
                totalBytes += contents.length;
                if (!sha256(contents).equals(expected.sha256())) {
                    throw rejected(
                            "CLASS_ARCHIVE_CHANGED",
                            "Class entry content changed after JAR analysis: " + path);
                }
                ClassFileInfo classFile;
                try {
                    classFile = referenceReader.read(new ByteArrayInputStream(contents), path);
                } catch (IOException | IllegalArgumentException exception) {
                    findings.add(invalidClassFile(path));
                    continue;
                }
                classFileConsumer.accept(path, contents);
                classes.add(classFile);
            }
            if (!observedDuplicatePaths.containsAll(duplicatePaths)) {
                Set<String> disappeared = new HashSet<>(duplicatePaths);
                disappeared.removeAll(observedDuplicatePaths);
                throw rejected(
                        "CLASS_ARCHIVE_CHANGED",
                        "Duplicate class entries changed after JAR analysis: " + disappeared);
            }
            Set<String> expectedPaths = new HashSet<>(expectedEntries.keySet());
            expectedPaths.removeAll(duplicatePaths);
            if (!scannedPaths.containsAll(expectedPaths)) {
                expectedPaths.removeAll(scannedPaths);
                throw rejected(
                        "CLASS_ARCHIVE_CHANGED",
                        "Class entries disappeared after JAR analysis: " + expectedPaths);
            }
        } catch (AnalysisException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AnalysisException(
                    "CLASS_SCAN_FAILED",
                    ANALYZER_ID,
                    "Unable to scan class files in " + report.source() + ": " + safeMessage(exception),
                    exception);
        }
        return new ClassFileScanResult(classes, findings);
    }

    private void validateEntryMetadata(ZipEntry entry, JarArchiveEntry expected, String path) {
        long size = entry.getSize();
        long compressedSize = entry.getCompressedSize();
        if (size != expected.uncompressedSize()
                || compressedSize != expected.compressedSize()
                || size > limits.maximumEntryBytes()) {
            throw rejected(
                    "CLASS_ARCHIVE_CHANGED",
                    "Class entry metadata changed after JAR analysis: " + path);
        }
        if (size > 0
                && (compressedSize <= 0
                        || size / (double) compressedSize > limits.maximumCompressionRatio())) {
            throw rejected(
                    "JAR_COMPRESSION_RATIO_EXCEEDED",
                    "Class entry exceeds the compression ratio limit: " + path);
        }
    }

    private byte[] readEntry(
            ZipFile zipFile, ZipEntry entry, long remainingTotal, String path) throws IOException {
        long size = entry.getSize();
        if (size < 0 || size > remainingTotal || size > limits.maximumEntryBytes()) {
            throw rejected(
                    size > remainingTotal ? "JAR_TOTAL_SIZE_LIMIT_EXCEEDED" : "JAR_ENTRY_TOO_LARGE",
                    "Class entry exceeds the configured read limits: " + path);
        }
        if (size > Integer.MAX_VALUE) {
            throw rejected(
                    "JAR_ENTRY_TOO_LARGE",
                    "Class entry exceeds the in-memory parsing limit: " + path);
        }
        ByteArrayOutputStream output =
                new ByteArrayOutputStream((int) Math.min(size, 8192));
        try (InputStream input = zipFile.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            long readTotal = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                readTotal += read;
                if (readTotal > size || readTotal > remainingTotal
                        || readTotal > limits.maximumEntryBytes()) {
                    throw rejected(
                            "CLASS_ARCHIVE_CHANGED",
                            "Class entry exceeded its recorded size while scanning: " + path);
                }
                output.write(buffer, 0, read);
            }
        }
        if (output.size() != size) {
            throw rejected(
                    "CLASS_ARCHIVE_CHANGED",
                    "Class entry size changed after JAR analysis: " + path);
        }
        return output.toByteArray();
    }

    private static Finding invalidClassFile(String path) {
        return new Finding(
                "compatibility.invalid-class-file",
                ANALYZER_ID,
                Severity.HIGH,
                "compatibility",
                "Invalid class file",
                "The class entry could not be parsed as a valid Java class file.",
                List.of("Entry: " + path),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(path),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                1.0,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static AnalysisException rejected(String code, String message) {
        return new AnalysisException(code, ANALYZER_ID, message);
    }

    private static String sha256(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
