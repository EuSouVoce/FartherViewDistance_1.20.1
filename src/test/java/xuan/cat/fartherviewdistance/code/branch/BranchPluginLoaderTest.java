package xuan.cat.fartherviewdistance.code.branch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BranchPluginLoaderTest {
    @Test
    void buildsMetadataUrlFromRepositoryGroupAndArtifact() {
        final String metadataUrl = BranchPluginLoader.toMetadataUrl(
                "https://repo.imperio.games/public",
                "xuan.cat.fartherviewdistance",
                "fartherviewdistance-branch-1.21.11");

        assertEquals(
                "https://repo.imperio.games/public/xuan/cat/fartherviewdistance/fartherviewdistance-branch-1.21.11/maven-metadata.xml",
                metadataUrl);
    }

    @Test
    void resolvesNewestVersionFromMavenMetadata() {
        final String metadataXml = """
                <metadata>
                  <groupId>xuan.cat.fartherviewdistance</groupId>
                  <artifactId>fartherviewdistance-branch-1.21.11</artifactId>
                  <versioning>
                    <latest>1.21.11.1001</latest>
                    <release>1.21.11.1002</release>
                    <versions>
                      <version>1.21.11.999</version>
                      <version>1.21.11.1002</version>
                      <version>1.21.11.1001</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        final Optional<String> resolved = BranchPluginLoader.resolveLatestVersionFromMetadata(metadataXml);

        assertTrue(resolved.isPresent());
        assertEquals("1.21.11.1002", resolved.get());
    }

    @Test
    void ignoresInvalidMetadata() {
        assertTrue(BranchPluginLoader.resolveLatestVersionFromMetadata("<metadata").isEmpty());
    }

    @Test
    void detectsFileRepositoryAsLocalPath() {
        final Optional<Path> repository = BranchPluginLoader.toLocalRepositoryPath(
                "file:///C:/projetos/FartherViewDistance_1.20.1/build/branch-repo");

        assertTrue(repository.isPresent());
        assertEquals(Path.of("C:/projetos/FartherViewDistance_1.20.1/build/branch-repo")
                .toAbsolutePath()
                .normalize(), repository.get());
    }

    @Test
    void buildsLocalArtifactJarPath() {
        final Path artifactJar = BranchPluginLoader.toArtifactJarPath(
                Path.of("build/branch-repo"),
                "xuan.cat.fartherviewdistance",
                "fartherviewdistance-branch-26.1.2",
                "26.1.2.1000");

        assertEquals(Path.of("build/branch-repo/xuan/cat/fartherviewdistance/fartherviewdistance-branch-26.1.2/26.1.2.1000/fartherviewdistance-branch-26.1.2-26.1.2.1000.jar")
                .toAbsolutePath()
                .normalize(), artifactJar);
    }

    @Test
    void buildsArtifactJarUrlFromRepositoryGroupArtifactAndVersion() {
        final String artifactJarUrl = BranchPluginLoader.toArtifactJarUrl(
                "https://repo.imperio.games/public/",
                "xuan.cat.fartherviewdistance",
                "fartherviewdistance-branch-26.1.2",
                "26.1.2.1000");

        assertEquals(
                "https://repo.imperio.games/public/xuan/cat/fartherviewdistance/fartherviewdistance-branch-26.1.2/26.1.2.1000/fartherviewdistance-branch-26.1.2-26.1.2.1000.jar",
                artifactJarUrl);
    }
}
