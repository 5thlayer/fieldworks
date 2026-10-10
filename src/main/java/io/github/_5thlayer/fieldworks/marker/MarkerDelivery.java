// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which patch markers each online player's map has been sent, and with what amount (ADR-0003). A
 * marker is sent again only when its amount changed, and once with nothing left, which removes it.
 * An oil field never reaches nothing, so it is never removed.
 *
 * <p>The client holds its markers in memory only, so what a player was sent is forgotten at logout
 * and not saved: the next login is sent every marker of its dimension again.
 */
public final class MarkerDelivery {

    /** What is left in a patch now. */
    @FunctionalInterface
    public interface Amounts {
        long of(String dimension, PatchMarker marker);
    }

    /** A marker as the client is sent it; an amount of zero removes it. */
    public record Update(PatchMarker marker, long amount) {
    }

    private final Map<UUID, Map<String, Map<PatchMarker, Long>>> sent = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        private final String dimension;
        private final LinkedHashSet<PatchMarker> queue = new LinkedHashSet<>();

        private Session(String dimension) {
            this.dimension = dimension;
        }
    }

    /**
     * The player's dimension as of now. The first call since login, or one naming a new dimension,
     * queues every marker found there.
     */
    public void observe(UUID player, String dimension, Collection<PatchMarker> known) {
        Session session = sessions.get(player);
        if (session != null && session.dimension.equals(dimension)) {
            return;
        }
        session = new Session(dimension);
        sessions.put(player, session);
        session.queue.addAll(known);
    }

    /** A patch just found, queued for every player in its dimension. */
    public void found(String dimension, PatchMarker marker) {
        sessions.values().forEach(session -> {
            if (session.dimension.equals(dimension)) {
                session.queue.add(marker);
            }
        });
    }

    /** Queues every marker a player in {@code dimension} was sent, so a changed amount is sent again. */
    public void refresh(String dimension) {
        sessions.forEach((player, session) -> {
            if (session.dimension.equals(dimension)) {
                session.queue.addAll(sentTo(player, dimension).keySet());
            }
        });
    }

    /**
     * Every queued marker whose amount the player's map does not already show. One that has run out
     * is sent only to a map holding it.
     */
    public List<Update> take(UUID player, Amounts amounts) {
        Session session = sessions.get(player);
        if (session == null || session.queue.isEmpty()) {
            return List.of();
        }
        Map<PatchMarker, Long> shown = sent.computeIfAbsent(player, p -> new HashMap<>())
                .computeIfAbsent(session.dimension, d -> new HashMap<>());
        List<Update> taken = new ArrayList<>();
        for (PatchMarker marker : session.queue) {
            long amount = amounts.of(session.dimension, marker);
            Long before = shown.get(marker);
            if (before == null ? amount > 0 : before != amount) {
                if (amount > 0) {
                    shown.put(marker, amount);
                } else {
                    shown.remove(marker);
                }
                taken.add(new Update(marker, amount));
            }
        }
        session.queue.clear();
        return taken;
    }

    public void logout(UUID player) {
        sessions.remove(player);
        sent.remove(player);
    }

    private Map<PatchMarker, Long> sentTo(UUID player, String dimension) {
        return sent.getOrDefault(player, Map.of()).getOrDefault(dimension, Map.of());
    }
}
