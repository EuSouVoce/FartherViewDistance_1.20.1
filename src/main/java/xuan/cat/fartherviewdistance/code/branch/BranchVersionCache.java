package xuan.cat.fartherviewdistance.code.branch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

final class BranchVersionCache {
    private static final String KEY_METADATA_URL = "metadataUrl";
    private static final String KEY_VERSION = "version";
    private static final String KEY_CHECKED_AT = "checkedAtMillis";
    private static final String KEY_SUCCESS_AT = "successAtMillis";
    private static final String KEY_FAILURE = "failure";

    private final Path file;
    private final String metadataUrl;

    private BranchVersionCache(final Path file, final String metadataUrl) {
        this.file = file;
        this.metadataUrl = metadataUrl;
    }

    static BranchVersionCache create(final Path directory, final String metadataUrl) {
        return new BranchVersionCache(directory.resolve(BranchVersionCache.cacheFileName(metadataUrl)), metadataUrl);
    }

    Optional<Entry> read() {
        if (!Files.isRegularFile(this.file)) {
            return Optional.empty();
        }

        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(this.file)) {
            properties.load(input);
        } catch (final IOException exception) {
            return Optional.empty();
        }

        if (!this.metadataUrl.equals(properties.getProperty(BranchVersionCache.KEY_METADATA_URL))) {
            return Optional.empty();
        }

        final long checkedAt = BranchVersionCache.parseLong(properties.getProperty(BranchVersionCache.KEY_CHECKED_AT));
        final long successAt = BranchVersionCache.parseLong(properties.getProperty(BranchVersionCache.KEY_SUCCESS_AT));
        final String version = BranchVersionCache.blankToNull(properties.getProperty(BranchVersionCache.KEY_VERSION));
        final String failure = BranchVersionCache.blankToNull(properties.getProperty(BranchVersionCache.KEY_FAILURE));
        return Optional.of(new Entry(version, checkedAt, successAt, failure));
    }

    void writeSuccess(final String version, final long nowMillis) throws IOException {
        this.write(new Entry(version, nowMillis, nowMillis, null));
    }

    void writeFailure(final Entry previous, final String failure, final long nowMillis) throws IOException {
        final String retainedVersion = previous == null ? null : previous.version;
        final long retainedSuccessAt = previous == null ? 0L : previous.successAtMillis;
        this.write(new Entry(retainedVersion, nowMillis, retainedSuccessAt, failure));
    }

    private void write(final Entry entry) throws IOException {
        Files.createDirectories(this.file.getParent());
        final Properties properties = new Properties();
        properties.setProperty(BranchVersionCache.KEY_METADATA_URL, this.metadataUrl);
        properties.setProperty(BranchVersionCache.KEY_CHECKED_AT, Long.toString(entry.checkedAtMillis));
        properties.setProperty(BranchVersionCache.KEY_SUCCESS_AT, Long.toString(entry.successAtMillis));
        properties.setProperty(BranchVersionCache.KEY_VERSION, entry.version == null ? "" : entry.version);
        properties.setProperty(BranchVersionCache.KEY_FAILURE, entry.failure == null ? "" : entry.failure);

        try (OutputStream output = Files.newOutputStream(this.file)) {
            properties.store(output, "FartherViewDistance branch metadata cache");
        }
    }

    static String cacheFileName(final String metadataUrl) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(metadataUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash) + ".properties";
        } catch (final NoSuchAlgorithmException exception) {
            return Integer.toUnsignedString(metadataUrl.hashCode()) + ".properties";
        }
    }

    private static long parseLong(final String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException exception) {
            return 0L;
        }
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record Entry(String version, long checkedAtMillis, long successAtMillis, String failure) {
        Optional<String> optionalVersion() {
            return Optional.ofNullable(this.version);
        }

        boolean isFresh(final long nowMillis, final Duration ttl) {
            return this.checkedAtMillis > 0L
                    && nowMillis >= this.checkedAtMillis
                    && nowMillis - this.checkedAtMillis < ttl.toMillis();
        }
    }
}
