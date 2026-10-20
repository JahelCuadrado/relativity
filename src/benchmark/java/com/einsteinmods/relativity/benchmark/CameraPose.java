package com.einsteinmods.relativity.benchmark;

/** Where the spectator camera stands and looks (Minecraft yaw: 0 = south, -90 = east). */
record CameraPose(double x, double y, double z, float yaw, float pitch) {}
