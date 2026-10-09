# Fieldworks

Ore patches and oil wells for NeoForge, and the Harvester that works them. Patches of iron, copper,
coal, stone, uranium and sulfur generate beside vanilla ores, each behind a generation switch; oil
wells yield crude oil that decays to a floor and never runs dry. Patch ore is mined by hand with
vanilla pickaxes, or by the Harvester, in a burner tier and an FE tier.

Fieldworks plays alone in vanilla and requires only Groundworks. It began as FactoryWorks Core in
[5thlayer/factoryworks](https://github.com/5thlayer/factoryworks), whose Showcase pack consumes it
(FactoryWorks ADR-0127).

## Building

`sh ./gradlew build` builds the jar and runs the JUnit tests; `sh ./gradlew runGameTestServer` runs the
game tests headless. Releases go through `scripts/release.sh` (`docs/agents/releases.md`).

## Licence

MIT, under REUSE.
