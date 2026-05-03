package xuan.cat.fartherviewdistance.code.branch;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import io.papermc.paper.ServerBuildInfo;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public final class BranchPluginLoader implements PluginLoader {
    private static final String DEFAULT_REPOSITORY_URL = "https://maven.pkg.github.com/EuSouVoce/FartherViewDistance_1.20.1";
    private static final String DEFAULT_GROUP_ID = "xuan.cat.fartherviewdistance";
    private static final String DEFAULT_ARTIFACT_PREFIX = "fartherviewdistance-branch";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3500;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 3500;
    private static final long DEFAULT_CACHE_TTL_SECONDS = 21_600L;

    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        final BranchLoaderConfig config = BranchLoaderConfig.load(classpathBuilder.getContext().getDataDirectory());
        classpathBuilder.getContext().getLogger().info("FartherViewDistance branch autoload config: "
                + config.sourceDescription() + ", repository=" + config.repositoryUrl());
        if (!config.enabled()) {
            classpathBuilder.getContext().getLogger().info("FartherViewDistance branch autoload disabled in config.yml");
            return;
        }

        final String minecraftVersion = this.detectMinecraftVersion(config.minecraftVersion(), classpathBuilder);
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            classpathBuilder.getContext().getLogger().warn("Could not resolve server minecraft version during bootstrap; skipping branch autoload");
            return;
        }

        final String repositoryUrl = config.repositoryUrl();
        final String repositoryUser = this.firstNonBlank(
            config.repositoryUser(),
            System.getenv("FVD_BRANCH_REPO_USER"),
            System.getenv("GITHUB_ACTOR"));
        final String repositoryToken = this.firstNonBlank(
            config.repositoryToken(),
            System.getenv("FVD_BRANCH_REPO_TOKEN"),
            System.getenv("GITHUB_TOKEN"));
        final String groupId = config.groupId();
        final String artifactPrefix = config.artifactPrefix();
        final String artifactId = artifactPrefix + "-" + minecraftVersion;

        final Optional<String> latestVersion = this.resolveLatestVersion(repositoryUrl, groupId, artifactId,
            repositoryUser, repositoryToken, config, classpathBuilder);
        if (latestVersion.isEmpty()) {
            classpathBuilder.getContext().getLogger().info("No external branch artifact found for " + artifactId + " at " + repositoryUrl + "; using built-in providers only");
            return;
        }

        if (this.tryAddLocalBranchJar(classpathBuilder, repositoryUrl, groupId, artifactId, latestVersion.get())) {
            return;
        }

        final Optional<Path> downloadedBranchJar = this.downloadBranchJar(repositoryUrl, groupId, artifactId,
                latestVersion.get(), repositoryUser, repositoryToken, config, classpathBuilder);
        if (downloadedBranchJar.isEmpty()) {
            classpathBuilder.getContext().getLogger().warn("Could not download external branch jar for " + artifactId
                    + "; using built-in providers only");
            return;
        }

        final Path branchJar = downloadedBranchJar.get();
        classpathBuilder.addLibrary(new JarLibrary(branchJar));
        classpathBuilder.getContext().getLogger().info("Loaded external branch jar " + branchJar + " for "
                + groupId + ":" + artifactId + ":" + latestVersion.get());
    }

    private boolean tryAddLocalBranchJar(final PluginClasspathBuilder classpathBuilder, final String repositoryUrl,
            final String groupId, final String artifactId, final String version) {
        final Optional<Path> localRepository = BranchPluginLoader.toLocalRepositoryPath(repositoryUrl);
        if (localRepository.isEmpty()) {
            return false;
        }

        final Path artifactJar = BranchPluginLoader.toArtifactJarPath(localRepository.get(), groupId, artifactId,
                version);
        if (!Files.isRegularFile(artifactJar)) {
            classpathBuilder.getContext().getLogger().warn("Local branch artifact jar not found: " + artifactJar
                    + "; using built-in providers only");
            return true;
        }

        classpathBuilder.addLibrary(new JarLibrary(artifactJar));
        classpathBuilder.getContext().getLogger().info("Loaded local external branch jar " + artifactJar);
        return true;
    }

    private Optional<Path> downloadBranchJar(final String repositoryUrl, final String groupId, final String artifactId,
            final String version, final String repositoryUser, final String repositoryToken,
            final BranchLoaderConfig config, final PluginClasspathBuilder classpathBuilder) {
        final String artifactJarUrl = BranchPluginLoader.toArtifactJarUrl(repositoryUrl, groupId, artifactId, version);
        final Path artifactJar = config.libraryDirectory()
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar")
                .toAbsolutePath()
                .normalize();

        if (Files.isRegularFile(artifactJar)) {
            return Optional.of(artifactJar);
        }

        try {
            Files.createDirectories(artifactJar.getParent());
            Files.write(artifactJar, this.fetchBytes(artifactJarUrl, repositoryUser, repositoryToken, config));
            return Optional.of(artifactJar);
        } catch (final IOException exception) {
            classpathBuilder.getContext().getLogger().warn("Could not download branch jar from " + artifactJarUrl
                    + ": " + exception.getMessage());
            try {
                Files.deleteIfExists(artifactJar);
            } catch (final IOException ignored) {
            }
            return Optional.empty();
        }
    }

    private String detectMinecraftVersion(final String configuredVersion, final PluginClasspathBuilder classpathBuilder) {
        final String forced = configuredVersion;
        if (forced != null && !forced.isBlank()) {
            return forced.trim();
        }

        try {
            final String fromBuildInfo = ServerBuildInfo.buildInfo().minecraftVersionId();
            if (fromBuildInfo != null && !fromBuildInfo.isBlank()) {
                return fromBuildInfo.trim();
            }
        } catch (final Throwable throwable) {
            classpathBuilder.getContext().getLogger().debug("Could not read Paper ServerBuildInfo minecraft version",
                    throwable);
        }

        try {
            final String fromBukkit = Bukkit.getMinecraftVersion();
            if (fromBukkit != null && !fromBukkit.isBlank()) {
                return fromBukkit.trim();
            }
        } catch (final Throwable ignored) {
        }

        try {
            final String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion != null && !bukkitVersion.isBlank()) {
                final int cut = bukkitVersion.indexOf('-');
                return (cut > 0 ? bukkitVersion.substring(0, cut) : bukkitVersion).trim();
            }
        } catch (final Throwable ignored) {
        }

        return null;
    }

    private Optional<String> resolveLatestVersion(final String repositoryUrl, final String groupId,
            final String artifactId, final String repositoryUser, final String repositoryToken,
            final BranchLoaderConfig config, final PluginClasspathBuilder classpathBuilder) {
        final String metadataUrl = BranchPluginLoader.toMetadataUrl(repositoryUrl, groupId, artifactId);
        final boolean cacheEnabled = config.cacheEnabled();
        final boolean forceRefresh = config.forceRefresh();
        final boolean offline = config.offline();
        final Duration cacheTtl = Duration.ofSeconds(Math.max(config.cacheTtlSeconds(), 60L));
        final long nowMillis = System.currentTimeMillis();

        final BranchVersionCache cache = BranchVersionCache.create(config.cacheDirectory(), metadataUrl);
        final BranchVersionCache.Entry cached = cacheEnabled ? cache.read().orElse(null) : null;
        if (cacheEnabled && !forceRefresh && cached != null && cached.isFresh(nowMillis, cacheTtl)) {
            final Optional<String> cachedVersion = cached.optionalVersion();
            if (cachedVersion.isPresent()) {
                return cachedVersion;
            }
            classpathBuilder.getContext().getLogger().info("Retrying branch metadata for " + artifactId
                    + " despite a fresh cached miss"
                    + (cached.failure() == null ? "" : " (" + cached.failure() + ")"));
        }

        if (offline) {
            if (cached != null && cached.optionalVersion().isPresent()) {
                classpathBuilder.getContext().getLogger().info("Using cached branch dependency for " + artifactId
                        + " because branch autoload is offline");
                return cached.optionalVersion();
            }
            return Optional.empty();
        }

        final String metadataXml;
        try {
            metadataXml = this.fetch(metadataUrl, repositoryUser, repositoryToken, config);
        } catch (final IOException exception) {
            if (cacheEnabled) {
                this.writeFailure(cache, cached, exception.getMessage(), nowMillis);
            }
            if (cached != null && cached.optionalVersion().isPresent()) {
                classpathBuilder.getContext().getLogger().warn("Could not refresh branch metadata for " + artifactId
                        + " (" + exception.getMessage() + "); using cached version " + cached.optionalVersion().get());
                return cached.optionalVersion();
            }
            classpathBuilder.getContext().getLogger().warn("Could not load branch metadata for " + artifactId
                    + " from " + metadataUrl + ": " + exception.getMessage());
            return Optional.empty();
        }

        final Optional<String> latestVersion = BranchPluginLoader.resolveLatestVersionFromMetadata(metadataXml);
        if (cacheEnabled) {
            try {
                if (latestVersion.isPresent()) {
                    cache.writeSuccess(latestVersion.get(), nowMillis);
                } else {
                    cache.writeFailure(cached, "metadata contained no versions", nowMillis);
                }
            } catch (final IOException exception) {
                classpathBuilder.getContext().getLogger().warn("Could not write branch metadata cache: "
                        + exception.getMessage());
            }
        }

        if (latestVersion.isEmpty() && cached != null && cached.optionalVersion().isPresent()) {
            classpathBuilder.getContext().getLogger().warn("Branch metadata for " + artifactId
                    + " contained no versions; using cached version " + cached.optionalVersion().get());
            return cached.optionalVersion();
        }
        if (latestVersion.isEmpty()) {
            classpathBuilder.getContext().getLogger().info("Branch metadata for " + artifactId
                    + " contained no versions at " + metadataUrl);
        }
        return latestVersion;
    }

    static String toMetadataUrl(final String repositoryUrl, final String groupId, final String artifactId) {
        final String normalized = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        final String groupPath = groupId.replace('.', '/');
        return normalized + groupPath + "/" + artifactId + "/maven-metadata.xml";
    }

    static Optional<Path> toLocalRepositoryPath(final String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return Optional.empty();
        }

        final String trimmed = repositoryUrl.trim();
        try {
            final URI uri = URI.create(trimmed);
            final String scheme = uri.getScheme();
            if ("file".equalsIgnoreCase(scheme)) {
                return Optional.of(Path.of(uri).toAbsolutePath().normalize());
            }
            if (scheme != null && scheme.length() == 1 && trimmed.length() > 1 && trimmed.charAt(1) == ':') {
                return Optional.of(Path.of(trimmed).toAbsolutePath().normalize());
            }
            if (scheme != null) {
                return Optional.empty();
            }
        } catch (final IllegalArgumentException ignored) {
        }

        try {
            return Optional.of(Path.of(trimmed).toAbsolutePath().normalize());
        } catch (final InvalidPathException ignored) {
            return Optional.empty();
        }
    }

    static Path toArtifactJarPath(final Path repositoryRoot, final String groupId, final String artifactId,
            final String version) {
        return repositoryRoot.resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar")
                .toAbsolutePath()
                .normalize();
    }

    static String toArtifactJarUrl(final String repositoryUrl, final String groupId, final String artifactId,
            final String version) {
        final String normalized = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        final String groupPath = groupId.replace('.', '/');
        return normalized + groupPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version
                + ".jar";
    }

    static Optional<String> resolveLatestVersionFromMetadata(final String metadataXml) {
        final List<String> versions = BranchPluginLoader.parseVersions(metadataXml);
        if (versions.isEmpty()) {
            return Optional.empty();
        }

        return versions.stream()
                .max(Comparator.comparing(BranchResolver.ComparableVersion::parse));
    }

    private String fetch(final String url, final String repositoryUser, final String repositoryToken,
            final BranchLoaderConfig config)
            throws IOException {
        return new String(this.fetchBytes(url, repositoryUser, repositoryToken, config), StandardCharsets.UTF_8);
    }

    private byte[] fetchBytes(final String url, final String repositoryUser, final String repositoryToken,
            final BranchLoaderConfig config)
            throws IOException {
        final URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(Math.max(config.connectTimeoutMillis(), 250));
        connection.setReadTimeout(Math.max(config.readTimeoutMillis(), 250));
        connection.setRequestProperty("User-Agent", "FartherViewDistance-BranchLoader");
        if (repositoryUser != null && repositoryToken != null) {
            final String basic = repositoryUser + ":" + repositoryToken;
            final String value = "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", value);
        }

        if (connection instanceof HttpURLConnection httpConnection) {
            final int status = httpConnection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
        }

        try (InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    static List<String> parseVersions(final String metadataXml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            final Document document;
            try (InputStream input = new ByteArrayInputStream(metadataXml.getBytes(StandardCharsets.UTF_8))) {
                document = factory.newDocumentBuilder().parse(input);
            }

            final List<String> versions = new ArrayList<>();
            final NodeList versionNodes = document.getElementsByTagName("version");
            for (int index = 0; index < versionNodes.getLength(); index++) {
                final String text = versionNodes.item(index).getTextContent();
                if (text != null && !text.isBlank()) {
                    versions.add(text.trim());
                }
            }

            final NodeList releaseNodes = document.getElementsByTagName("release");
            if (releaseNodes.getLength() > 0) {
                final String release = releaseNodes.item(0).getTextContent();
                if (release != null && !release.isBlank()) {
                    versions.add(release.trim());
                }
            }

            final NodeList latestNodes = document.getElementsByTagName("latest");
            if (latestNodes.getLength() > 0) {
                final String latest = latestNodes.item(0).getTextContent();
                if (latest != null && !latest.isBlank()) {
                    versions.add(latest.trim());
                }
            }

            return versions;
        } catch (final Exception ignored) {
            return List.of();
        }
    }

    private String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void writeFailure(final BranchVersionCache cache, final BranchVersionCache.Entry cached,
            final String failure, final long nowMillis) {
        try {
            cache.writeFailure(cached, failure, nowMillis);
        } catch (final IOException ignored) {
        }
    }

    private record BranchLoaderConfig(
            boolean enabled,
            String minecraftVersion,
            String repositoryUrl,
            String repositoryUser,
            String repositoryToken,
            String groupId,
            String artifactPrefix,
            boolean cacheEnabled,
            Path cacheDirectory,
            Path libraryDirectory,
            long cacheTtlSeconds,
            boolean forceRefresh,
            boolean offline,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            String sourceDescription) {
        static BranchLoaderConfig load(final Path dataDirectory) {
            final Path configPath = dataDirectory.resolve("config.yml");
            final FileConfiguration configuration = YamlConfiguration.loadConfiguration(configPath.toFile());
            final String sourceDescription = configPath.toFile().isFile()
                    ? configPath.toAbsolutePath().toString()
                    : configPath.toAbsolutePath() + " not found; using defaults";
            final String prefix = "branch-autoload.";
            final String cachePrefix = prefix + "cache.";

            return new BranchLoaderConfig(
                    configuration.getBoolean(prefix + "enable", true),
                    blankToNull(configuration.getString(prefix + "minecraft-version", "")),
                    stringValue(configuration, prefix + "repository-url", BranchPluginLoader.DEFAULT_REPOSITORY_URL),
                    blankToNull(configuration.getString(prefix + "repository-user", "")),
                    blankToNull(configuration.getString(prefix + "repository-token", "")),
                    stringValue(configuration, prefix + "group-id", BranchPluginLoader.DEFAULT_GROUP_ID),
                    stringValue(configuration, prefix + "artifact-prefix", BranchPluginLoader.DEFAULT_ARTIFACT_PREFIX),
                    configuration.getBoolean(cachePrefix + "enable", true),
                    Path.of(stringValue(configuration, cachePrefix + "directory",
                            "plugins/FartherViewDistance/branch-cache")),
                    Path.of(stringValue(configuration, prefix + "library-directory",
                            "plugins/FartherViewDistance/branch-libraries")),
                    Math.max(configuration.getLong(cachePrefix + "ttl-seconds",
                            BranchPluginLoader.DEFAULT_CACHE_TTL_SECONDS), 60L),
                    configuration.getBoolean(cachePrefix + "force-refresh", false),
                    configuration.getBoolean(prefix + "offline", false),
                    Math.max(configuration.getInt(prefix + "connect-timeout-millis",
                            BranchPluginLoader.DEFAULT_CONNECT_TIMEOUT_MILLIS), 250),
                    Math.max(configuration.getInt(prefix + "read-timeout-millis",
                            BranchPluginLoader.DEFAULT_READ_TIMEOUT_MILLIS), 250),
                    sourceDescription);
        }

        private static String stringValue(final FileConfiguration configuration, final String path,
                final String defaultValue) {
            final String value = configuration.getString(path, defaultValue);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }

        private static String blankToNull(final String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
