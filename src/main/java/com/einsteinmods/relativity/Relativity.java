package com.einsteinmods.relativity;

import com.einsteinmods.relativity.config.RelativitySettings;
import net.fabricmc.api.ModInitializer;

/** Common (client + server) entrypoint. */
public final class Relativity implements ModInitializer {
  public static final String MOD_ID = "relativity";

  @Override
  public void onInitialize() {
    // Reads the settings file now, so a broken file is reported (and replaced) at startup.
    RelativitySettings.startup();
  }
}
