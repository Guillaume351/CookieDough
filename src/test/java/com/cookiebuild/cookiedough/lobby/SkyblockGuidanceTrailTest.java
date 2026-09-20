package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyblockGuidanceTrailTest {
    @Test
    void capsLongTrailsAndStillEndsAtTheNpc() {
        SkyblockGuidanceTrail.Point start = new SkyblockGuidanceTrail.Point(0.0, 8.3, 0.0);
        SkyblockGuidanceTrail.Point destination = new SkyblockGuidanceTrail.Point(100.0, 9.0, -25.0);

        var points = SkyblockGuidanceTrail.between(start, destination);

        assertEquals(SkyblockGuidanceTrail.MAX_POINTS, points.size());
        assertEquals(destination, points.get(points.size() - 1));
        assertTrue(points.get(0).x() > start.x());
        assertTrue(points.get(0).z() < start.z());
    }

    @Test
    void rejectsDegenerateOrNonFiniteTrails() {
        SkyblockGuidanceTrail.Point point = new SkyblockGuidanceTrail.Point(1.0, 2.0, 3.0);
        assertTrue(SkyblockGuidanceTrail.between(point, point).isEmpty());
        assertTrue(SkyblockGuidanceTrail.between(
                new SkyblockGuidanceTrail.Point(Double.NaN, 0.0, 0.0), point).isEmpty());
        assertTrue(SkyblockGuidanceTrail.between(null, point).isEmpty());
    }
}
