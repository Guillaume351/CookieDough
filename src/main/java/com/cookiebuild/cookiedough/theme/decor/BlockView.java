package com.cookiebuild.cookiedough.theme.decor;

/** Read-only world access for the decor planner (snapshots at runtime, maps in tests). */
public interface BlockView {
    /** Upper-case material name, {@code AIR} for any air or anything outside the view. */
    String type(int x, int y, int z);

    /** Full block data, e.g. {@code minecraft:wall_torch[facing=north]}. */
    String data(int x, int y, int z);

    static boolean isAir(String type) {
        return type == null || type.equals("AIR") || type.equals("CAVE_AIR") || type.equals("VOID_AIR");
    }
}
