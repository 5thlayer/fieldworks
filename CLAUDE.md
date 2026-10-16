# Fieldworks

A 5thlayer Module, released on its own to Modrinth and CurseForge. See `GLOSSARY.md` for the domain glossary.

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word. Asking a session to steward a PR is the user's word for that PR's pushes, its rebases onto `main`, and its merge, under the `steward` skill.

Anything that changes the Module's behaviour gets a `/code-review`. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server with only Groundworks beside the Module, and names each one it ran; it fails if it ran none. The `skillworks:gametest-loop` skill runs a subset, `--tests 'fieldworks:<glob>' 1`, one glob a run, so a ticket's new game tests share a name prefix that selects them all. FTB Chunks, Jade and EMI or JEI are on the dev client only. `python3 -m unittest discover scripts/tests` checks the committed sprites match `scripts/build-textures.py`. A new game test class is registered by a line in `FieldworksGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that `sh ./gradlew buildGametestStructures` writes. CI (`.github/workflows/ci.yml`) runs all three on every push, the first two through the libworks plugin's shared `module-ci.yml`, and never publishes. The build, the game test server, publishing and the release tasks come from the libworks convention plugin (libworks ADR 0003); `libworks { }` in `build.gradle` names Groundworks' range and the nested libworks-runtime. Data only the Module's own runs load lives in `src/gametest/resources`, which never ships (libworks ADR 0004): `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client, the datapacks that stand in for what a Consumer fills. Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`.

Vanilla's code, with NeoForge's patches, is in `build/moddev/artifacts/minecraft-patched-*-sources.jar`; NeoForge's own classes (`net/neoforged/...`) are in `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`, `<version>` being `neoforge_version` in `gradle.properties` (the cache keeps older ones). Read either with `unzip -p <jar> <path>/X.java`, and check a claim about vanilla or NeoForge behaviour there before stating it.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through the libworks plugin's `release` task, which uploads last with its `upload` task when `gradle.properties` names a project, and a published version never changes, in `~/.m2` or on either site. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach another Module follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
