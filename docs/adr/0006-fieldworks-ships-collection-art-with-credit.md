---
status: accepted
---

# Fieldworks ships openly licensed art from art collections, with credit

Fieldworks wears art published for reuse in an art collection, under an open licence that allows
commercial use, credited in `NOTICE` and annotated in `REUSE.toml`: today Malcolm Riley's
unused-textures (CC BY 4.0) for the ores, crude oil, sulfur and the uranium items. Where no collection
fits, `scripts/build-textures.py` generates the sprite. Art taken from another mod, and art under a
non-commercial licence, stays out.

This is stricter than the suite's rule, which allows third-party art under any open licence that
permits redistribution, from an art collection or another mod
([5thlayer/factoryworks ADR-0130](https://github.com/5thlayer/factoryworks/blob/main/docs/adr/0130-the-suite-ships-third-party-art-under-open-licences-with-credit.md),
superseding ADR-0122). Fieldworks is a Library released on its own: art drawn for reuse, credited,
reads better than a generated stand-in, and art lifted from another mod reads as taken from it.

## Considered Options

- **Only art Fieldworks made.** That would mean generated sprites in place of the collection art the
  ores already wear.
- **Any openly licensed art, credited.** Rejected: art lifted from another mod reads as taken from
  that mod, whatever its licence allows.
