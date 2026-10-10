---
status: accepted
---

# Every Harvester input and output reaches a hopper

Fieldworks must be automatable in a world with no mod but Groundworks, where nothing moves fluid. So
every input and output of the Harvester is reachable with hoppers and chests alone, and NeoForge's
fluid capability is a second route for pipe mods, never the only one.

Fluids cross as filled containers. A **drain slot** takes a filled container of a Drilling Fluid and
empties it into the Drilling Fluid tank; a **fill slot** takes an empty container and fills it from the
product tank (crude). The item handler routes each item by what it holds, not by the face it enters,
so one hopper feeding mud bottles and empty buckets together fills both slots. Emptied and filled
containers join the result in the **output buffer**, which the Harvester pushes out of its output face
into whatever item handler is there. It never drops items in the world.

The product tank is pushed the same way: crude goes into any fluid handler at the output face, since
some pipe mods' pipes (Pipeworks') pull from nothing. The Drilling Fluid tank stays passive, because
whatever supplies it already pushes.

Crude oil therefore gets a bucket, `fieldworks:crude_oil_bucket`, superseding ADR 0002's "no
bucket": without one, a world with no pipe mod could pump crude and never move it.

## Considered Options

- **Fluids through the capability only.** Drilling Fluids and crude would then need a pipe mod, and a
  vanilla world could neither feed a fluid recipe nor take crude out.
- **Drilling Fluids as items.** A tag of items consumed per operation would need no tank, but
  contradicts the fluid input of 5thlayer/fieldworks#1 and still leaves crude with no item route.
- **One container slot.** A stack of bottles waiting for room in the Drilling Fluid tank blocks the
  empty buckets that would make that room, so a floor-rate crude recipe jams.
