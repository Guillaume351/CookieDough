package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;

/** Bounded straight-line breadcrumbs rendered only to the guided player. */
final class SkyblockGuidanceTrail {
    static final int MAX_POINTS = 14;
    private static final double POINT_SPACING = 0.9;

    record Point(double x, double y, double z) { }

    private SkyblockGuidanceTrail() { }

    static List<Point> between(Point start, Point destination) {
        if (start == null || destination == null || !finite(start) || !finite(destination)) {
            return List.of();
        }
        double deltaX = destination.x() - start.x();
        double deltaY = destination.y() - start.y();
        double deltaZ = destination.z() - start.z();
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (!Double.isFinite(distance) || distance <= 0.0) {
            return List.of();
        }
        int pointCount = Math.min(MAX_POINTS, Math.max(1, (int) Math.ceil(distance / POINT_SPACING)));
        List<Point> points = new ArrayList<>(pointCount);
        for (int index = 1; index <= pointCount; index++) {
            double progress = (double) index / pointCount;
            points.add(new Point(
                    start.x() + deltaX * progress,
                    start.y() + deltaY * progress,
                    start.z() + deltaZ * progress));
        }
        return List.copyOf(points);
    }

    private static boolean finite(Point point) {
        return Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }
}
