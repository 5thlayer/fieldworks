// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.oil;

import java.util.List;

/** Implemented by the structure piece that places a crude-oil field. */
public interface OilFieldSource {

    int centreX();

    int centreZ();

    /** Every well the field drew, before any is turned away by an ore column. */
    List<OilField.Well> wells();
}
