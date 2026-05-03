package xuan.cat.fartherviewdistance.code.branch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BranchVersionCacheTest {
    @Test
    void cachesSuccessfulVersionByMetadataUrl() throws Exception {
        final BranchVersionCache cache = BranchVersionCache.create(BranchVersionCacheTest.testDirectory(),
                "https://maven.pkg.github.com/owner/repo/xuan/cat/fartherviewdistance/fartherviewdistance-branch-1.21.11/maven-metadata.xml");

        cache.writeSuccess("1.21.11.1002", 1_000L);

        final BranchVersionCache.Entry entry = cache.read().orElseThrow();
        assertEquals("1.21.11.1002", entry.optionalVersion().orElseThrow());
        assertEquals(1_000L, entry.checkedAtMillis());
        assertEquals(1_000L, entry.successAtMillis());
        assertTrue(entry.isFresh(2_000L, Duration.ofSeconds(2)));
    }

    @Test
    void recordsNegativeLookupWithoutLosingPreviousVersion() throws Exception {
        final BranchVersionCache cache = BranchVersionCache.create(BranchVersionCacheTest.testDirectory(),
                "https://example.test/maven-metadata.xml");
        cache.writeSuccess("1.21.11.1002", 1_000L);

        cache.writeFailure(cache.read().orElseThrow(), "HTTP 503", 2_000L);

        final BranchVersionCache.Entry entry = cache.read().orElseThrow();
        assertEquals("1.21.11.1002", entry.optionalVersion().orElseThrow());
        assertEquals(2_000L, entry.checkedAtMillis());
        assertEquals(1_000L, entry.successAtMillis());
        assertEquals("HTTP 503", entry.failure());
    }

    @Test
    void staleEntriesAreNotFresh() {
        final BranchVersionCache.Entry entry = new BranchVersionCache.Entry(null, 1_000L, 0L, "not found");

        assertFalse(entry.isFresh(70_000L, Duration.ofSeconds(60)));
    }

    private static Path testDirectory() throws Exception {
        final Path directory = Path.of("build", "test-branch-version-cache", Long.toString(System.nanoTime()));
        Files.createDirectories(directory);
        return directory;
    }
}
