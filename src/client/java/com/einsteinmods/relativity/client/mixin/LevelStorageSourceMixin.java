package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.exit.PendingWorldSaves;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Before a world folder is opened again, waits for its background save. Every client flow that
 * touches a world folder goes through one of these two methods, the only ones that build a {@code
 * LevelStorageAccess} (which takes the session lock): {@code WorldOpenFlows#createWorldAccess}
 * (play), {@code WorldSelectionList.WorldListEntry#editWorld} (edit, rename, backup, optimize),
 * {@code #recreateWorld}, {@code #doDeleteWorld}, {@code
 * CreateWorldScreen#createNewWorldDirectory}, {@code TitleScreen} (demo world) and Realms {@code
 * FileDownload}. Other worlds pass straight through. Cost: one check per call when nothing is
 * saving.
 */
@Mixin(LevelStorageSource.class)
abstract class LevelStorageSourceMixin {
  @Shadow
  public abstract Path getLevelPath(String levelId);

  @Inject(
      method = {"validateAndCreateAccess", "createAccess"},
      at = @At("HEAD"))
  private void relativity$awaitBackgroundSave(
      String levelId, CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> callback) {
    if (PendingWorldSaves.hasPending()) {
      PendingWorldSaves.awaitSave(getLevelPath(levelId));
    }
  }
}
