// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker.client;

/** A map that draws the client's patch markers. FTB Chunks is the only one. */
public interface MapMarkerRenderer {

    /** The markers changed; the map reads them again from {@link PatchMapClient#markers}. */
    void markersChanged();
}
