---
status: accepted
---

# A found patch is marked for every player

FactoryWorks Core marked a patch only on the map of a team whose Radar had charted it, or of a player
who walked past it. The Radar is not ported (FactoryWorks ADR-0127), so nothing decides who sees a
marker.

**Decision.**

- **A patch is found when the chunk holding its centre is first sent to any player.** The finding is
  saved with the world, per dimension.
- **Every player in the dimension is sent one marker per found patch**: its resource, its centre and
  what is left. A player is sent them all at login and on entering a dimension, and each new one as it
  is found.
- **A marker is a snapshot, refreshed every five seconds.** It is sent again only when its amount
  changed, and once with nothing left, which removes it. An oil field's marker shows its wells' summed
  yield and is never removed.
- **The map is drawn by FTB Chunks' public icon API**, in one package that loads only when
  `ftbchunks` is present. Without it markers are received and not drawn. Other map mods are added
  beside it behind the same renderer interface.
- **What a player was sent is not saved.** The client keeps its markers in memory only, so the next
  login is sent them again.

**Considered: sending a player only the patches they walked past.** It needs the per-player record
Core kept, for a marker that tells a player nothing the ground does not.

**Considered: waypoints.** A player can delete one and removing one needs a second call that can
miss. An icon is rebuilt from the client's own markers on each refresh, so removal is exact.

**Consequences.**

- A patch is unmarked until someone's client is sent its centre chunk, so a player who has not
  explored near it learns of it only from another's finding.
- `PatchMarker.CRUDE_OIL` stands for an oil field. It draws as `fieldworks:crude_oil_bucket`, since crude is a fluid (ADR 0004).
