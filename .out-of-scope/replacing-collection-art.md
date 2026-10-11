# Replacing collection art with art of our own

Fieldworks does not replace the art it takes from art collections with art it made itself. The ore
blocks, crude oil, sulfur and the uranium items wear Malcolm Riley's
[unused-textures](https://github.com/malcolmriley/unused-textures), a set he published for others to
reuse under CC BY 4.0, and they keep it.

## Why this is out of scope

The suite as a whole ships only art it made
([5thlayer/factoryworks ADR-0122](https://github.com/5thlayer/factoryworks/blob/main/docs/adr/0122-factoryworks-ships-only-art-it-made.md)),
and a request to bring Fieldworks in line with it is the natural thing to file. Fieldworks decided
otherwise in its own ADR 0006 (`docs/adr/0006-fieldworks-ships-collection-art-with-credit.md`): it is
a Library released on its own, and art drawn for reuse, credited in `NOTICE` and `REUSE.toml`, reads
better than a generated stand-in.

What stays out is narrower: art taken from another mod, and art under a non-commercial licence. A
request to replace one of those is in scope. So is one to replace a generated sprite, such as the
stone ore's boulders, with collection art or art of our own.

## Prior requests

- [#18](https://github.com/5thlayer/fieldworks/issues/18): "Ores and crude oil wear Fieldworks' own art, not malcolmriley's"
