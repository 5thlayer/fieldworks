---
status: accepted
---

# A pack sets an ore block's default amount

ADR 0002 froze every patch number as Fieldworks' own, the default amount among them. A pack that plays differently
from a vanilla world, such as a skyblock that sells ore blocks from a shop, needs blocks that run out sooner, and had
no way to say so.

So the default amount is a block data map, `fieldworks:default_amount`, one whole number of at least 1 per ore block,
kept on the server. Fieldworks ships it in its own data like any default, so a pack maker has a file to copy, and a
pack overrides one ore without touching the rest; it is read again on each reload. Nothing else comes loose: a
patch's amounts, its shape and its stages stay Fieldworks' own and frozen. An ore block a pack leaves without an
entry starts at 1, the floor a patch block already has, and the reload logs it.

No block stores what it started with, so blocks already in the world follow a change, half-mined ones included,
whose amount left and stage move with it. Keeping them steady would mean new saved state for every drawn block; a
pack that changes the number mid-world takes that on itself.

This supersedes ADR 0002's "The numbers are Fieldworks' own, frozen" clause for the default amount only; the rest of
that ADR stands.

## Considered Options

- **A folder of Fieldworks' own, one file per resource.** A data map already merges per entry across packs and
  reloads with the rest of the data, with no listener of our own.
- **Defaults only in the jar's code or classpath, a data file only when a pack writes one.** The pack maker would
  have nothing to copy, and the number would live in two places.
