# New Paper Version via Branch Autoload

Checklist for releasing support for a new Minecraft/Paper version without asking server owners to replace the core plugin jar.

## Goal

- Keep the same core `FartherViewDistance-11.0.0.jar` on servers.
- Publish only a new Maven branch artifact.
- On the next restart, `BranchPluginLoader` detects the server version, downloads the branch jar, and the plugin enables normally.

## 1) Confirm the target version

Example:

- New Minecraft/Paper version: `26.1.3`
- Expected Maven artifact: `fartherviewdistance-branch-26.1.3`
- First branch artifact version: `26.1.3.1000`

For a bugfix on the same Minecraft/Paper version, do not create a new artifact. Publish a higher version under the same artifact:

- `26.1.3.1001`
- `26.1.3.1002`

The loader picks the highest version from `maven-metadata.xml`.

## 2) Add an entry to `branchModules`

Edit `build.gradle` and add a new entry to the `branchModules` list.

Template:

```groovy
[
        taskName: "26_1_3",
        minecraftVersion: "26.1.3",
        requiresMinecraftVersion: "26.1.3",
        branchVersion: "26.1.3.1000",
        packageName: "xuan.cat.fartherviewdistance.branch.generated.v26_1_3",
        className: "GeneratedBranchProvider_26_1_3"
]
```

Fields:

- `taskName`: version with `_`, used in Gradle task names.
- `minecraftVersion`: version reported by the server.
- `requiresMinecraftVersion`: Paper classpath used to compile this branch.
- `branchVersion`: Maven artifact version; increment it when fixing the same branch.
- `packageName` / `className`: generated ServiceLoader provider.

For `26.x`, the build automatically uses Java 25 and Paper dependency `26.x.build.+`.

## 3) Adjust NMS code if needed

Run:

```powershell
.\gradlew.bat clean jar
```

If it compiles, the generated baseline branch is valid.
If it fails on NMS/Paper classes, adjust the real branch code under:

- `src/main/java/xuan/cat/fartherviewdistance/code/branch`

Then run `clean jar` again.

## 4) Test locally before publishing

Publish to the local folder Maven repository:

```powershell
.\gradlew.bat publishBranchModulesToLocal
```

In the test server, configure:

```yaml
branch-autoload:
  enable: true
  repository-url: "file:///C:/projetos/FartherViewDistance_1.20.1/build/branch-repo"
```

When starting the new Paper version, expected logs:

- `Loaded local external branch jar ...fartherviewdistance-branch-26.1.3-26.1.3.1000.jar`
- `Loaded branch provider external-26.1.3 for Minecraft 26.1.3 (...)`

## 5) Publish only the new package

For ImperioGames:

```powershell
.\gradlew.bat publishBranchModulesToGitHub -Pgpr.repoUrl=https://repo.imperio.games/public/ -PmavenServerId=imperiogames
```

By default, publishing skips artifacts that already exist with the same version.
To republish the same version:

```powershell
.\gradlew.bat publishBranchModulesToGitHub -Pgpr.repoUrl=https://repo.imperio.games/public/ -PmavenServerId=imperiogames -Pbranch.publish.force=true
```

Operational preference: avoid force for public releases. Increment `branchVersion` to `26.1.3.1001` when shipping a fix.

## 6) Automate with GitHub Actions

Workflows:

- `.github/workflows/validate-branch-module.yml`
- `.github/workflows/publish-branch-module.yml`

### Pull request validation

When a PR changes `build.gradle`, loader/providers, or workflows, `Validate Branch Modules` runs:

```bash
./gradlew --no-daemon clean jar
./gradlew --no-daemon test -Pminecraft_version=26.1.2 -Ppaper_dependency_version=26.1.2.build.+ -Pjava=25
```

This verifies that the core and aggregate branch jars still compile before publishing.

### Manual publish

In GitHub Actions:

1. Open `Actions`
2. Select `Publish Branch Module`
3. Click `Run workflow`
4. Fill `repository_url` if publishing outside GitHub Packages.

For GitHub Packages in the same repository, leave `repository_url` empty.

For ImperioGames, use:

```text
https://repo.imperio.games/public/
```

### Secrets for external Maven repositories

For ImperioGames or another external Maven repository, configure GitHub Actions secrets:

- `Settings` -> `Secrets and variables` -> `Actions`
- `BRANCH_MAVEN_USER`
- `BRANCH_MAVEN_PASSWORD`

If these secrets are not set, the workflow falls back to:

- user: `${{ github.actor }}`
- password/token: `${{ secrets.GITHUB_TOKEN }}`

That works for GitHub Packages in the same repository.

### Tag-based publish

You can also publish by pushing a tag:

```powershell
git tag branch-v26.1.3.1000
git push origin branch-v26.1.3.1000
```

Tag publishing uses the default repository:

```text
https://maven.pkg.github.com/<owner>/<repo>
```

For ImperioGames, prefer manual `workflow_dispatch` with `repository_url`, unless you customize the workflow to make ImperioGames the default.

### Force publish in CI

In `Run workflow`, enable `force_publish` only when you need to republish the exact same Maven version.

Preferred release flow: increment `branchVersion` and publish a new version.

## 7) What server owners need to do

Nothing to the core jar, as long as they already run a core version with the current `BranchPluginLoader`.

Their server `config.yml` should have:

```yaml
branch-autoload:
  enable: true
  repository-url: "https://repo.imperio.games/public/"
```

On the next restart with Paper `26.1.3`, the plugin:

1. detects `26.1.3`,
2. looks for `fartherviewdistance-branch-26.1.3`,
3. downloads the highest published version,
4. saves the jar under `plugins/FartherViewDistance/branch-libraries`,
5. loads the provider through ServiceLoader.

## 8) When the core jar must change

Replace the core only when changing a shared contract:

- `BranchProvider`
- interfaces under `xuan.cat.fartherviewdistance.api.branch`
- loader/cache/autoload behavior
- shared code used by branch jars

For normal NMS/Paper updates, publish only the branch artifact.

## 9) Quick Troubleshooting

`No external branch artifact found ...`

- The artifact for the server version is not published yet.
- Check `maven-metadata.xml` in the remote repository.
- Clear `plugins/FartherViewDistance/branch-cache` to discard old test history.

`No compatible branch provider found ...`

- The jar downloaded, but the provider does not support the exact version reported by Paper.
- Check `minecraftVersion` and generated provider `supportsMinecraftVersion`.

`NoClassDefFoundError: ... BranchProvider`

- The server is using an old core jar.
- Replace the core once with the current build; future Paper versions should then come through packages.
