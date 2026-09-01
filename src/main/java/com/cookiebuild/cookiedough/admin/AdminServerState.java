package com.cookiebuild.cookiedough.admin;

public final class AdminServerState {
    private volatile boolean maintenance;
    private volatile boolean draining;

    public boolean maintenance() {
        return maintenance;
    }

    public boolean draining() {
        return draining;
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
        if (!maintenance) draining = false;
    }

    public void startDrain() {
        maintenance = true;
        draining = true;
    }
}
