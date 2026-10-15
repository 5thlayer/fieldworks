# Replacing collection art with art of our own

Fieldworks does not replace the art it takes from art collections with art it made itself. The ore
blocks, crude oil, sulfur and the uranium items wear Malcolm Riley's
[unused-textures](https://github.com/malcolmriley/unused-textures), a set he published for others to
reuse under CC BY 4.0, and they keep it.

## Why this is out of scope

Fieldworks' own ADR 0006 (`docs/adr/0006-fieldworks-ships-collection-art-with-credit.md`) keeps it:
Fieldworks is a Module released on its own, and art drawn for reuse, credited in `NOTICE` and
`REUSE.toml`, reads better than a generated stand-in. The suite allows it too: it ships third-party
art under any open licence that permits redistribution
([5thlayer/factoryworks ADR-0130](https://github.com/5thlayer/factoryworks/blob/main/docs/adr/0130-the-suite-ships-third-party-art-under-open-licences-with-credit.md)),
so replacing this art would buy nothing a player sees.

What stays out is narrower: art taken from another mod, and art under a non-commercial licence. A
request to replace one of those is in scope. So is one to replace a generated sprite, such as the
stone ore's boulders, with collection art or art of our own.

## Prior requests

- [#18](https://github.com/5thlayer/fieldworks/issues/18): "Ores and crude oil wear Fieldworks' own art, not malcolmriley's"
