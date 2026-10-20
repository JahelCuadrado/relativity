package com.einsteinmods.relativity.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import java.lang.management.ManagementFactory;
import java.util.Comparator;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import oshi.SystemInfo;

/** JVM, OS, CPU, GPU and loaded-mod description written into every session report. */
final class EnvironmentInfo {
  private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;

  private EnvironmentInfo() {}

  /** Must run on the render thread (reads the GPU device). */
  static JsonObject collect() {
    JsonObject environment = new JsonObject();
    JsonObject jvm = new JsonObject();
    jvm.addProperty("version", Runtime.version().toString());
    jvm.addProperty("vendor", System.getProperty("java.vendor"));
    jvm.addProperty("vm", System.getProperty("java.vm.name"));
    jvm.addProperty("maxHeapMiB", Runtime.getRuntime().maxMemory() / BYTES_PER_MEBIBYTE);
    JsonArray arguments = new JsonArray();
    ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(arguments::add);
    jvm.add("arguments", arguments);
    environment.add("jvm", jvm);

    JsonObject os = new JsonObject();
    os.addProperty("name", System.getProperty("os.name"));
    os.addProperty("version", System.getProperty("os.version"));
    os.addProperty("arch", System.getProperty("os.arch"));
    environment.add("os", os);

    JsonObject cpu = new JsonObject();
    cpu.addProperty(
        "name", new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName());
    cpu.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
    environment.add("cpu", cpu);

    DeviceInfo device = RenderSystem.getDevice().getDeviceInfo();
    JsonObject gpu = new JsonObject();
    gpu.addProperty("name", device.name());
    gpu.addProperty("vendor", device.vendorName());
    gpu.addProperty("driver", device.driverInfo());
    gpu.addProperty("backend", device.backendName());
    environment.add("gpu", gpu);

    JsonArray mods = new JsonArray();
    FabricLoader.getInstance().getAllMods().stream()
        .filter(mod -> mod.getContainingMod().isEmpty())
        .map(ModContainer::getMetadata)
        .sorted(Comparator.comparing(ModMetadata::getId))
        .forEach(metadata -> mods.add(metadata.getId() + " " + metadata.getVersion()));
    environment.add("mods", mods);
    return environment;
  }
}
