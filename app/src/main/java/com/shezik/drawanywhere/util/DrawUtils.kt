/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere.util

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt

internal fun distanceSquared(p1: Offset, p2: Offset): Float {
    return (p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2)
}

internal fun distance(p1: Offset, p2: Offset): Float {
    return sqrt(distanceSquared(p1, p2))
}

internal fun distancePointToLineSegment(p: Offset, a: Offset, b: Offset): Float {
    val ap = Offset(p.x - a.x, p.y - a.y)  // Vector from a to p
    val ab = Offset(b.x - a.x, b.y - a.y)  // Vector from a to b

    val ab2 = ab.x.pow(2) + ab.y.pow(2)  // Squared length of segment ab

    if (ab2 == 0f) {  // a and b are the same point
        return distance(p, a)
    }

    // Parameter t of the closest point on the line containing ab
    val t = (ap.x * ab.x + ap.y * ab.y) / ab2

    val closest =
        if (t < 0.0f) {
            // Closest point is a
            a
        } else if (t > 1.0f) {
            // Closest point is b
            b
        } else {
            // Closest point lies on the segment
            Offset(a.x + t * ab.x, a.y + t * ab.y)
        }
    return distance(p, closest)
}

internal fun hitTestRectEdge(
    point: Offset,
    left: Float, top: Float, right: Float, bottom: Float,
    threshold: Float
): Boolean {
    // Top edge
    if (distancePointToLineSegment(point, Offset(left, top), Offset(right, top)) <= threshold) return true
    // Bottom edge
    if (distancePointToLineSegment(point, Offset(left, bottom), Offset(right, bottom)) <= threshold) return true
    // Left edge
    if (distancePointToLineSegment(point, Offset(left, top), Offset(left, bottom)) <= threshold) return true
    // Right edge
    if (distancePointToLineSegment(point, Offset(right, top), Offset(right, bottom)) <= threshold) return true
    return false
}