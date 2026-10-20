package com.einsteinmods.relativity.client.mixin;

import com.einsteinmods.relativity.client.exit.PendingWorldSaves;
import java.nio.file.Path;
import net.minecraft.util.DirectoryLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A world still saving in the background is reported as not locked. {@code
 * LevelStorageSource#loadLevelSummaries} calls {@code DirectoryLock#isLocked} for every world in
 * the list; on a lock held by this same JVM {@code FileChannel#tryLock} throws {@code
 * OverlappingFileLockException}, so vanilla would drop the world from the list (or, from another
 * process, grey it out as locked). Opening it then waits in {@link LevelStorageSourceMixin}. Cost:
 * one check per listed world when nothing is saving.
 */
@Mixin(DirectoryLock.class)
abstract class DirectoryLockMixin {
  @Inject(method = "isLocked", at = @At("HEAD"), cancellable = true)
  private static void relativity$backgroundSaveIsNotALock(
      Path directory, CallbackInfoReturnable<Boolean> callback) {
    if (PendingWorldSaves.hasPending() && PendingWorldSaves.isSaving(directory)) {
      callback.setReturnValue(false);
    }
  }
}
