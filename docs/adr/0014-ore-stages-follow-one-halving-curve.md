---
status: accepted
---

# Ore stages follow one halving curve

ADR 0002 froze each resource's stage thresholds as Factorio's: eight hand-picked amounts (15000, 9500, 5500, 2900,
1300, 400, 150, 80) divided by the first, copied twelve times into `amounts.json` and rounded differently in eight of
the copies. No rule produced them.

So one curve serves every resource: stage *i* begins once a block's remaining amount is at or below `0.5^i` of its
starting amount, for i = 0..7, the last at 1/128. `amounts.json` holds the ratio once, as `stage_ratio`; the count of
eight stays in code, since it sizes the blockstate property at registration and matches the sprites. Like the rest of
that file it is the jar's own, and a pack can't change it.

It is an approximation, and ore looks worn sooner: a block reaches its second stage after losing half its amount,
where Factorio's waited for a little over a third.

This supersedes ADR 0002's "The numbers are Fieldworks' own, frozen" clause for the stage thresholds only; the rest of
that ADR stands.

## Considered Options

- **Keep Factorio's thresholds, deduplicated into one list.** Fixes the copies but keeps eight numbers with no rule.
- **Let a pack set the curve or the count.** The count sizes a blockstate property before any datapack loads, and
  no pack has asked for a different curve.
