package xuan.cat.fartherviewdistance.code.branch;

import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;

public final class BranchResolver {
    private static final String DEFAULT_LIBRARY_DIRECTORY = "plugins/FartherViewDistance/branch-libraries";
    private static final List<URLClassLoader> EXTERNAL_PROVIDER_CLASSLOADERS = new ArrayList<>();

    private BranchResolver() {
    }

    public static Optional<SelectedBranch> resolve(final String minecraftVersion, final Logger logger) {
        return BranchResolver.resolve(minecraftVersion, logger, BranchResolver.class.getClassLoader(),
                Path.of(BranchResolver.DEFAULT_LIBRARY_DIRECTORY));
    }

    public static Optional<SelectedBranch> resolve(final String minecraftVersion, final Logger logger, final ClassLoader classLoader) {
        return BranchResolver.resolve(minecraftVersion, logger, classLoader,
                Path.of(BranchResolver.DEFAULT_LIBRARY_DIRECTORY));
    }

    public static Optional<SelectedBranch> resolve(final String minecraftVersion, final Logger logger,
            final ClassLoader classLoader, final Path libraryDirectory) {
        final ClassLoader effectiveClassLoader = classLoader == null
                ? BranchResolver.class.getClassLoader()
                : classLoader;
        final List<BranchProvider> providers = new ArrayList<>();

        providers.add(new BuiltInBranchProvider());

        BranchResolver.loadExternalProviderForVersion(minecraftVersion, effectiveClassLoader, libraryDirectory, logger)
                .ifPresent(providers::add);

        return providers.stream()
                .filter(provider -> BranchResolver.isCompatible(provider, minecraftVersion, logger))
                .max(Comparator.comparing(provider -> ComparableVersion.parse(provider.branchVersion())))
                .map(provider -> new SelectedBranch(
                        provider,
                        provider.createMinecraft(),
                        provider.createPacket()));
    }

    private static Optional<BranchProvider> loadExternalProviderForVersion(final String minecraftVersion,
            final ClassLoader pluginClassLoader, final Path libraryDirectory, final Logger logger) {
        if (libraryDirectory == null || !Files.isDirectory(libraryDirectory)) {
            return Optional.empty();
        }

        try {
            final String prefix = "fartherviewdistance-branch-" + minecraftVersion + "-";
            final List<Path> candidateJars;
            try (var stream = Files.walk(libraryDirectory)) {
                candidateJars = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .toList();
            }

            final Optional<Path> newestJar = candidateJars.stream().max(Comparator.comparing(path -> {
                final String fileName = path.getFileName().toString();
                final String version = fileName.substring(prefix.length(), fileName.length() - ".jar".length());
                return ComparableVersion.parse(version);
            }));

            if (newestJar.isEmpty()) {
                return Optional.empty();
            }

            final Path jar = newestJar.get();
            final String fileName = jar.getFileName().toString();
            final String branchVersion = fileName.substring(prefix.length(), fileName.length() - ".jar".length());
            final URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, pluginClassLoader);
            synchronized (BranchResolver.EXTERNAL_PROVIDER_CLASSLOADERS) {
                BranchResolver.EXTERNAL_PROVIDER_CLASSLOADERS.add(loader);
            }

            final Class<?> minecraftCodeClass = Class.forName(
                    "xuan.cat.fartherviewdistance.code.branch.MinecraftCode",
                    true,
                    loader);
            final Class<?> packetCodeClass = Class.forName(
                    "xuan.cat.fartherviewdistance.code.branch.PacketCode",
                    true,
                    loader);

            final BranchMinecraft minecraft = (BranchMinecraft) minecraftCodeClass.getDeclaredConstructor().newInstance();
            final BranchPacket packet = (BranchPacket) packetCodeClass.getDeclaredConstructor().newInstance();

            logger.info("Loaded external branch implementation from " + jar + " (branch " + branchVersion + ")");
            return Optional.of(new ExternalDirectBranchProvider(minecraftVersion, branchVersion, minecraft, packet));
        } catch (final IOException exception) {
            logger.warning("Could not scan branch library directory " + libraryDirectory + ": "
                    + exception.getMessage());
            return Optional.empty();
        } catch (final Throwable exception) {
            logger.warning("Could not load external branch implementation for " + minecraftVersion + ": "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isCompatible(final BranchProvider provider, final String minecraftVersion,
            final Logger logger) {
        try {
            return provider.supportsMinecraftVersion(minecraftVersion);
        } catch (final Throwable throwable) {
            logger.warning("Failed compatibility check for provider " + provider.id() + ": " + throwable.getMessage());
            return false;
        }
    }

    public record SelectedBranch(BranchProvider provider, BranchMinecraft minecraft, BranchPacket packet) {
    }

    private static final class ExternalDirectBranchProvider implements BranchProvider {
        private final String minecraftVersion;
        private final String branchVersion;
        private final BranchMinecraft minecraft;
        private final BranchPacket packet;

        private ExternalDirectBranchProvider(final String minecraftVersion, final String branchVersion,
                final BranchMinecraft minecraft, final BranchPacket packet) {
            this.minecraftVersion = minecraftVersion;
            this.branchVersion = branchVersion;
            this.minecraft = minecraft;
            this.packet = packet;
        }

        @Override
        public String id() {
            return "external-" + this.minecraftVersion;
        }

        @Override
        public String branchVersion() {
            return this.branchVersion;
        }

        @Override
        public boolean supportsMinecraftVersion(final String currentMinecraftVersion) {
            return this.minecraftVersion.equals(currentMinecraftVersion);
        }

        @Override
        public BranchMinecraft createMinecraft() {
            return this.minecraft;
        }

        @Override
        public BranchPacket createPacket() {
            return this.packet;
        }
    }

    static final class ComparableVersion implements Comparable<ComparableVersion> {
        private final List<Integer> parts;

        private ComparableVersion(final List<Integer> parts) {
            this.parts = parts;
        }

        static ComparableVersion parse(final String version) {
            final String[] rawParts = version == null ? new String[0] : version.trim().split("\\.");
            final List<Integer> parsed = new ArrayList<>(rawParts.length);
            for (final String rawPart : rawParts) {
                parsed.add(ComparableVersion.parsePart(rawPart));
            }
            return new ComparableVersion(parsed);
        }

        private static int parsePart(final String rawPart) {
            if (rawPart == null || rawPart.isEmpty()) {
                return 0;
            }

            final StringBuilder digits = new StringBuilder();
            for (int index = 0; index < rawPart.length(); index++) {
                final char current = rawPart.charAt(index);
                if (Character.isDigit(current)) {
                    digits.append(current);
                    continue;
                }
                break;
            }

            if (digits.isEmpty()) {
                return 0;
            }

            try {
                return Integer.parseInt(digits.toString());
            } catch (final NumberFormatException exception) {
                return 0;
            }
        }

        @Override
        public int compareTo(final ComparableVersion other) {
            final int max = Math.max(this.parts.size(), other.parts.size());
            for (int i = 0; i < max; i++) {
                final int left = i < this.parts.size() ? this.parts.get(i) : 0;
                final int right = i < other.parts.size() ? other.parts.get(i) : 0;
                if (left != right) {
                    return Integer.compare(left, right);
                }
            }
            return 0;
        }
    }
}
