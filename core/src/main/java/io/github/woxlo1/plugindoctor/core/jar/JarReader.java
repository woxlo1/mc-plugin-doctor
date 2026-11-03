package io.github.woxlo1.plugindoctor.core.jar;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarReader {
    private static final String COMPONENT_ID = "jar.reader";
    private static final Set<String> DESCRIPTOR_PATHS =
            Set.of("plugin.yml", "paper-plugin.yml", "bungee.yml", "velocity-plugin.json");
    private final JarReadLimits limits;

    public JarReader() {
        this(JarReadLimits.defaults());
    }

    public JarReader(JarReadLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public JarArchive read(Path source) {
        Objects.requireNonNull(source, "source");
        Path normalizedSource = source.toAbsolutePath().normalize();
        try {
            long fileSize = Files.size(normalizedSource);
            List<JarArchiveEntry> entries = new ArrayList<>();
            List<String> duplicates = new ArrayList<>();
            Set<String> names = new HashSet<>();
            Map<String, String> textEntries = new LinkedHashMap<>();
            long totalUncompressedBytes = 0;

            try (ZipFile zipFile = new ZipFile(normalizedSource.toFile())) {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    if (entries.size() >= limits.maximumEntries()) {
                        throw rejected(
                                "JAR_ENTRY_LIMIT_EXCEEDED",
                                "Archive exceeds the entry limit of " + limits.maximumEntries());
                    }
                    String entryPath = entry.getName();
                    validateEntryPath(entryPath);
                    if (!names.add(entryPath)) {
                        duplicates.add(entryPath);
                    }

                    long declaredSize = entry.getSize();
                    if (declaredSize > limits.maximumEntryBytes()) {
                        throw rejected(
                                "JAR_ENTRY_TOO_LARGE",
                                "Entry exceeds the uncompressed size limit: " + entryPath);
                    }
                    boolean captureText = isTextEntry(entryPath);
                    if (captureText && declaredSize > limits.maximumDescriptorBytes()) {
                        throw rejected(
                                "JAR_DESCRIPTOR_TOO_LARGE",
                                "Metadata entry exceeds the size limit: " + entryPath);
                    }

                    MessageDigest digest = sha256();
                    ByteArrayOutputStream captured = captureText
                            ? new ByteArrayOutputStream(initialCapacity(declaredSize))
                            : null;
                    long entryBytes = 0;
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (read == 0) {
                                continue;
                            }
                            entryBytes = checkedAdd(
                                    entryBytes,
                                    read,
                                    limits.maximumEntryBytes(),
                                    "JAR_ENTRY_TOO_LARGE",
                                    "Entry exceeds the uncompressed size limit: " + entryPath);
                            totalUncompressedBytes = checkedAdd(
                                    totalUncompressedBytes,
                                    read,
                                    limits.maximumTotalUncompressedBytes(),
                                    "JAR_TOTAL_SIZE_LIMIT_EXCEEDED",
                                    "Archive exceeds the total uncompressed size limit.");
                            validateCompressionRatio(entryPath, entryBytes, entry.getCompressedSize());
                            digest.update(buffer, 0, read);
                            if (captured != null) {
                                if (entryBytes > limits.maximumDescriptorBytes()) {
                                    throw rejected(
                                            "JAR_DESCRIPTOR_TOO_LARGE",
                                            "Metadata entry exceeds the size limit: " + entryPath);
                                }
                                captured.write(buffer, 0, read);
                            }
                        }
                    }

                    if (declaredSize >= 0 && entryBytes != declaredSize) {
                        throw rejected(
                                "JAR_ENTRY_SIZE_MISMATCH",
                                "Entry size does not match its directory metadata: " + entryPath);
                    }
                    long compressedSize = Math.max(0, entry.getCompressedSize());
                    entries.add(new JarArchiveEntry(
                            entryPath,
                            entry.isDirectory(),
                            compressedSize,
                            entryBytes,
                            HexFormat.of().formatHex(digest.digest())));
                    if (captured != null) {
                        textEntries.putIfAbsent(
                                entryPath,
                                captured.toString(StandardCharsets.UTF_8));
                    }
                }
            }
            return new JarArchive(normalizedSource, fileSize, entries, duplicates, textEntries);
        } catch (AnalysisException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AnalysisException(
                    "JAR_READ_FAILED",
                    COMPONENT_ID,
                    "Unable to read archive " + normalizedSource + ": " + safeMessage(exception),
                    exception);
        }
    }

    private void validateEntryPath(String path) {
        if (path == null
                || path.isBlank()
                || path.startsWith("/")
                || path.indexOf('\\') >= 0
                || path.indexOf('\0') >= 0
                || path.indexOf(':') >= 0) {
            throw rejected("JAR_UNSAFE_ENTRY", "Rejected unsafe archive entry path: " + path);
        }

        String checkedPath = path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
        if (checkedPath.isEmpty()) {
            throw rejected("JAR_UNSAFE_ENTRY", "Rejected unsafe archive entry path: " + path);
        }
        for (String segment : checkedPath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw rejected("JAR_UNSAFE_ENTRY", "Rejected unsafe archive entry path: " + path);
            }
        }
    }

    private void validateCompressionRatio(String path, long uncompressedBytes, long compressedSize) {
        if (uncompressedBytes == 0) {
            return;
        }
        if (compressedSize <= 0
                || uncompressedBytes / (double) compressedSize > limits.maximumCompressionRatio()) {
            throw rejected(
                    "JAR_COMPRESSION_RATIO_EXCEEDED",
                    "Entry exceeds the compression ratio limit: " + path);
        }
    }

    private static long checkedAdd(
            long current,
            int increment,
            long limit,
            String code,
            String message) {
        if (increment > limit - current) {
            throw rejected(code, message);
        }
        return current + increment;
    }

    private static boolean isTextEntry(String path) {
        return DESCRIPTOR_PATHS.contains(path)
                || path.equalsIgnoreCase(JarFile.MANIFEST_NAME);
    }

    private static int initialCapacity(long size) {
        return size > 0 && size < Integer.MAX_VALUE ? (int) size : 32;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static AnalysisException rejected(String code, String message) {
        return new AnalysisException(code, COMPONENT_ID, message);
    }

    private static String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
