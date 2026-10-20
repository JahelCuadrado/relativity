package com.einsteinmods.relativity.client.exit;

import com.einsteinmods.relativity.client.mixin.MinecraftAccessor;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

/**
 * Waits for servers to stop on the render thread the way vanilla's {@code Minecraft#disconnect}
 * does: "Saving world" screen ({@code Gui.SAVING_LEVEL}), then {@code RenderSystem#pumpEvents} and
 * {@code Minecraft#renderFrame(false)} until {@code MinecraftServer#isShutdown}, so the window
 * keeps responding instead of freezing.
 */
final class SavingScreen {
  private SavingScreen() {}

  /**
   * @param restoreScreen put the previous screen back afterwards (its flow continues); false when
   *     quitting
   */
  static void showUntilSaved(
      Minecraft minecraft, List<IntegratedServer> servers, boolean restoreScreen) {
    @Nullable Screen previous = minecraft.gui.screen();
    minecraft.gui.setScreen(new GenericMessageScreen(Gui.SAVING_LEVEL));
    SDLEventHandler events = ((MinecraftAccessor) minecraft).relativity$sdlEventHandler();
    for (IntegratedServer server : servers) {
      while (!server.isShutdown()) {
        RenderSystem.pumpEvents(events);
        minecraft.renderFrame(false);
      }
    }
    if (restoreScreen) {
      minecraft.gui.setScreen(previous);
    }
  }
}
