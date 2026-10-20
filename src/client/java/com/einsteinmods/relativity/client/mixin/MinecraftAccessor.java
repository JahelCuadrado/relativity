package com.einsteinmods.relativity.client.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The event handler vanilla pumps while it waits for a world to save. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
  @Accessor("sdlEventHandler")
  SDLEventHandler relativity$sdlEventHandler();
}
