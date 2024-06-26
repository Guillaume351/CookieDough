package com.cookiebuild.cookiedough.listener;

import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldEventListener implements Listener {

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, Boolean.FALSE);
        event.getWorld().setGameRule(GameRule.DO_WEATHER_CYCLE, Boolean.FALSE);

        event.getWorld().setTime(0);
    }
}
