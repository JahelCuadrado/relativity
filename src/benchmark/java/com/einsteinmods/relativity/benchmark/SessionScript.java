package com.einsteinmods.relativity.benchmark;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One benchmark session, scripted from a daemon thread: startup to title screen and a fresh world
 * (fixed seed, spectator), chunk generation while flying, a dense storage base in view, a mob farm
 * in view, an item-frame wall in view, and world exits. Measured rounds run without JFR; each
 * scenario then gets one extra profiling round recorded to its own {@code .jfr}, so profiling never
 * skews the numbers. Each scene lowers the render distance to the smallest one that holds it (the
 * client census checks nothing went missing); startup, flight and exits keep the options file's.
 * With a toggle ({@code -Ptoggle}), scene rounds alternate it off/on. Results go to {@code
 * session.json} in the session directory, then the game quits.
 */
final class SessionScript implements Runnable {
  static final String WORLD_NAME = "relativity-benchmark";

  private static final Logger LOGGER = LoggerFactory.getLogger("relativity-benchmark");
  private static final long WORLD_SEED = 20261018L;
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration WORLD_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration EXIT_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration SETTLE = Duration.ofSeconds(5);
  private static final Duration GENERATION_POLL = Duration.ofSeconds(1);
  private static final Duration GENERATION_QUIET_TIMEOUT = Duration.ofMinutes(2);
  private static final int GENERATION_QUIET_POLLS = 2;
  private static final long POLL_MILLIS = 5L;
  private static final long NOT_SEEN = -1L;
  private static final double NANOS_PER_MILLI = 1_000_000.0;
  private static final double NANOS_PER_SECOND = 1_000_000_000.0;

  private static final int FLIGHT_Y = 200;
  private static final int FLIGHT_LANE_Z = 0;
  private static final float EAST_YAW = -90f;
  private static final float LEVEL_PITCH = 0f;
  private static final int FLIGHT_STEP_BLOCKS = 32;
  private static final Duration FLIGHT_STEP_INTERVAL = Duration.ofMillis(125);
  private static final Duration FLIGHT_WARM_UP = Duration.ofSeconds(5);
  private static final Duration FLIGHT_ROUND = Duration.ofSeconds(10);
  private static final int FLIGHT_ROUNDS = 3;

  private static final Duration SCENE_SETTLE = Duration.ofSeconds(10);
  private static final Duration SCENE_WARM_UP = Duration.ofSeconds(10);
  private static final Duration SCENE_ROUND = Duration.ofSeconds(10);
  private static final int SCENE_ROUNDS = 5;
  private static final int DEFAULT_SCENE_AB_ROUND_PAIRS = 3;
  private static final int SCENE_AB_ROUND_PAIRS =
      Integer.getInteger("relativity.benchmark.pairs", DEFAULT_SCENE_AB_ROUND_PAIRS);
  private static final Duration AB_SWITCH_SETTLE = Duration.ofSeconds(1);

  /** Off/on round pairs of the random-tick experiment after each scene's measured rounds. */
  private static final int RANDOM_TICK_AB_PAIRS = 2;

  private static final Duration PROFILING_ROUND = Duration.ofSeconds(10);

  private static final int EXIT_REJOIN_ROUNDS = 2;
  private static final Duration EXIT_REJOIN_IDLE = Duration.ofSeconds(10);

  /**
   * Entity distance scaling used with the scenes' render distances (all at most {@link
   * #MAX_SCENE_RENDER_DISTANCE}): keeps vanilla's entity view scale ({@code clamp(renderDistance /
   * 8, 1, 2.5) * scaling}, {@code LevelExtractor#extractVisibleEntities}) at its value for render
   * distance 12, so mobs render as far as before.
   */
  private static final double SCENE_ENTITY_DISTANCE_SCALING = 1.5;

  private static final int MAX_SCENE_RENDER_DISTANCE = 8;

  /** Calibrations per probe clock before a scene's rounds; the first warms the code path up. */
  private static final int PROBE_CALIBRATIONS = 2;

  private static final String TITLE_SCREEN_MARK = "titleScreen";
  private static final String WORLD_CREATION_MARK = "worldCreation";
  private static final String WORLD_JOINED_MARK = "worldJoined";

  private static final List<Scene> SCENES =
      List.of(
          new Scene(
              "denseBase",
              "dense-base",
              DenseBaseScene.CAMERA,
              DenseBaseScene.RENDER_DISTANCE,
              SCENE_SETTLE,
              DenseBaseScene::build,
              DenseBaseScene::census,
              DenseBaseScene::clientCensus),
          new Scene(
              "mobFarm",
              "mob-farm",
              MobFarmScene.CAMERA,
              MobFarmScene.RENDER_DISTANCE,
              SCENE_SETTLE,
              MobFarmScene::build,
              MobFarmScene::census,
              MobFarmScene::clientCensus),
          new Scene(
              "itemFrameWall",
              "item-frame-wall",
              ItemFrameWallScene.CAMERA,
              ItemFrameWallScene.RENDER_DISTANCE,
              SCENE_SETTLE,
              ItemFrameWallScene::build,
              ItemFrameWallScene::census,
              ItemFrameWallScene::clientCensus),
          new Scene(
              "village",
              "village",
              VillageScene.CAMERA,
              VillageScene.RENDER_DISTANCE,
              VillageScene.SETTLE,
              VillageScene::build,
              VillageScene::census,
              VillageScene::clientCensus),
          new Scene(
              "sorter",
              "sorter",
              SorterScene.CAMERA,
              SorterScene.RENDER_DISTANCE,
              SCENE_SETTLE,
              SorterScene::build,
              SorterScene::census,
              SorterScene::clientCensus));

  private final BenchmarkConfig config;
  private final GameControl control;
  private final Minecraft minecraft;
  private final long modInitUptimeMillis;
  private Optional<ToggleAlternation> alternation = Optional.empty();
  private int flightX;

  SessionScript(BenchmarkConfig config, Minecraft minecraft, long modInitUptimeMillis) {
    this.config = config;
    this.control = new GameControl(minecraft);
    this.minecraft = minecraft;
    this.modInitUptimeMillis = modInitUptimeMillis;
  }

  @Override
  public void run() {
    JsonObject session = new JsonObject();
    session.addProperty("profile", config.profile());
    session.addProperty("session", config.session());
    try {
      if (config.toggleKey().isPresent()) {
        alternation = Optional.of(ToggleAlternation.forKey(config.toggleKey().get()));
        session.addProperty("toggle", config.toggleKey().get());
      }
      session.add("cpuClock", ThreadCpuClock.describe());
      runScenarios(session);
      session.addProperty("status", "ok");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      fail(session, e);
    } catch (ExecutionException
        | TimeoutException
        | IOException
        | ParseException
        | RuntimeException e) {
      fail(session, e);
    } finally {
      alternation.ifPresent(ToggleAlternation::restore);
    }
    writeSession(session);
    minecraft.execute(minecraft::stop);
  }

  private static void fail(JsonObject session, Exception error) {
    LOGGER.error("Benchmark session failed", error);
    session.addProperty("status", "failed");
    session.addProperty("error", error.toString());
  }

  private void runScenarios(JsonObject session)
      throws InterruptedException,
          ExecutionException,
          TimeoutException,
          IOException,
          ParseException {
    long titleNanos = control.awaitClient(this::isTitleReady, STARTUP_TIMEOUT);
    double titleUptimeMillis = uptimeMillisAt(titleNanos);
    StartupPhases.mark(TITLE_SCREEN_MARK, epochMillisAt(titleNanos));
    session.add("environment", control.callOnClient(EnvironmentInfo::collect));
    Thread.sleep(SETTLE);
    session.add("startup", measureWorldCreation(titleUptimeMillis));
    configureWorld();
    Thread.sleep(SETTLE);
    session.add("chunkGeneration", measureChunkGeneration());
    ClientView startView = control.callOnClient(this::currentView);
    for (Scene scene : SCENES) {
      session.add(scene.key(), measureScene(scene));
    }
    control.runOnClient(() -> applyView(startView));
    session.add("worldExit", measureWorldExits());
  }

  private JsonObject measureWorldCreation(double titleUptimeMillis)
      throws InterruptedException, ExecutionException, TimeoutException {
    LOGGER.info("Benchmark: creating world");
    long createStart = control.callOnClient(this::createWorld);
    StartupPhases.mark(WORLD_CREATION_MARK, epochMillisAt(createStart));
    long joined = control.awaitClient(this::isInWorld, WORLD_TIMEOUT);
    StartupPhases.mark(WORLD_JOINED_MARK, epochMillisAt(joined));
    JsonObject startup = new JsonObject();
    startup.addProperty("jvmToModInitMs", modInitUptimeMillis);
    startup.addProperty("jvmToTitleScreenMs", titleUptimeMillis);
    startup.addProperty("createAndJoinWorldMs", (joined - createStart) / NANOS_PER_MILLI);
    startup.addProperty("jfrRecorded", JfrRecordings.finishStartupRecording());
    startup.add("phaseMarks", StartupPhases.toJson());
    return startup;
  }

  private long createWorld() {
    long start = System.nanoTime();
    minecraft
        .createWorldOpenFlows()
        .createFreshLevel(
            WORLD_NAME,
            new LevelSettings(
                WORLD_NAME,
                GameType.SPECTATOR,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
                true,
                WorldDataConfiguration.DEFAULT),
            new WorldOptions(WORLD_SEED, true, false),
            WorldPresets::createNormalWorldDimensions,
            new TitleScreen());
    return start;
  }

  /** Frozen noon and weather, no natural spawns, no cramming deaths: same world every session. */
  private void configureWorld() throws InterruptedException, ExecutionException {
    IntegratedServer server = control.server();
    control.runOnServer(
        () -> {
          GameRules rules = server.getGameRules();
          rules.set(GameRules.ADVANCE_TIME, false, server);
          rules.set(GameRules.ADVANCE_WEATHER, false, server);
          rules.set(GameRules.SPAWN_MOBS, false, server);
          rules.set(GameRules.SPAWN_MONSTERS, false, server);
          rules.set(GameRules.MAX_ENTITY_CRAMMING, 0, server);
          CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
          server.getCommands().performPrefixedCommand(source, "time set noon");
          server.getCommands().performPrefixedCommand(source, "weather clear");
        });
  }

  private JsonObject measureChunkGeneration()
      throws InterruptedException,
          ExecutionException,
          TimeoutException,
          IOException,
          ParseException {
    LOGGER.info("Benchmark: chunk generation flight");
    JsonObject scenario = new JsonObject();
    scenario.addProperty(
        "blocksPerSecond", FLIGHT_STEP_BLOCKS * NANOS_PER_SECOND / FLIGHT_STEP_INTERVAL.toNanos());
    flightX = 0;
    teleport(flightPose());
    scenario.addProperty("startAreaSettled", awaitGenerationQuiet());
    Thread.sleep(SETTLE);
    fly(FLIGHT_WARM_UP);
    JsonArray rounds = new JsonArray();
    for (int round = 0; round < FLIGHT_ROUNDS; round++) {
      rounds.add(recordRound(FLIGHT_ROUND, this::fly));
    }
    scenario.add("rounds", rounds);
    Recording recording = JfrRecordings.start(jfrFile("chunk-generation"));
    fly(PROFILING_ROUND);
    JfrRecordings.finish(recording);
    return scenario;
  }

  private JsonObject measureScene(Scene scene)
      throws InterruptedException,
          ExecutionException,
          TimeoutException,
          IOException,
          ParseException {
    LOGGER.info("Benchmark: scene {}", scene.jfrStem());
    JsonObject scenario = new JsonObject();
    ClientView view = new ClientView(scene.renderDistance(), SCENE_ENTITY_DISTANCE_SCALING);
    scenario.addProperty("renderDistance", view.renderDistance());
    scenario.addProperty("entityDistanceScaling", view.entityDistanceScaling());
    control.runOnClient(() -> applyView(view));
    teleport(scene.camera());
    scenario.addProperty("terrainSettled", awaitGenerationQuiet());
    IntegratedServer server = control.server();
    control.runOnServer(() -> scene.builder().accept(server.overworld()));
    Thread.sleep(scene.settle());
    scenario.add(
        "censusBefore", control.callOnServer(() -> scene.census().apply(server.overworld())));
    JsonObject clientCensus = control.callOnClient(() -> scene.clientCensus().apply(minecraft));
    scenario.add("clientCensus", clientCensus);
    if (!clientCensus.get("complete").getAsBoolean()) {
      throw new IllegalStateException(
          "Scene " + scene.jfrStem() + " is incomplete on the client: " + clientCensus);
    }
    Thread.sleep(SCENE_WARM_UP);
    scenario.add("probeCalibration", calibrateProbes());
    JsonArray rounds = new JsonArray();
    if (alternation.isPresent()) {
      ToggleAlternation toggle = alternation.get();
      for (int round = 0; round < 2 * SCENE_AB_ROUND_PAIRS; round++) {
        boolean enabled = ToggleAlternation.enabledInRound(round);
        toggle.apply(enabled);
        Thread.sleep(AB_SWITCH_SETTLE);
        JsonObject measured = recordRound(SCENE_ROUND, Thread::sleep);
        measured.addProperty("toggleEnabled", enabled);
        rounds.add(measured);
      }
      toggle.restore();
    } else {
      for (int round = 0; round < SCENE_ROUNDS; round++) {
        rounds.add(recordRound(SCENE_ROUND, Thread::sleep));
      }
    }
    scenario.add("rounds", rounds);
    scenario.add("randomTicksAb", measureRandomTicksOff());
    Recording recording = JfrRecordings.start(jfrFile(scene.jfrStem()));
    Thread.sleep(PROFILING_ROUND);
    JfrRecordings.finish(recording);
    scenario.add(
        "censusAfter", control.callOnServer(() -> scene.census().apply(server.overworld())));
    return scenario;
  }

  /**
   * Experiment after a scene's measured rounds: pairs of rounds with random ticks off ({@code
   * randomTickSpeed 0}) then back on. The scenes are built of blocks that never tick randomly, so
   * only the surrounding terrain's random ticks stop: the off/on difference is the most any
   * random-tick optimization could save in that scene.
   */
  private JsonArray measureRandomTicksOff() throws InterruptedException, ExecutionException {
    IntegratedServer server = control.server();
    int speed = control.callOnServer(() -> server.getGameRules().get(GameRules.RANDOM_TICK_SPEED));
    JsonArray rounds = new JsonArray();
    try {
      for (int round = 0; round < 2 * RANDOM_TICK_AB_PAIRS; round++) {
        boolean enabled = round % 2 == 1;
        int roundSpeed = enabled ? speed : 0;
        control.runOnServer(
            () -> server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, roundSpeed, server));
        Thread.sleep(AB_SWITCH_SETTLE);
        JsonObject measured = recordRound(SCENE_ROUND, Thread::sleep);
        measured.addProperty("randomTicksEnabled", enabled);
        rounds.add(measured);
      }
    } finally {
      control.runOnServer(
          () -> server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, speed, server));
    }
    return rounds;
  }

  /** Times the probes' own cost on the render and server threads, right before measuring. */
  private JsonObject calibrateProbes() throws InterruptedException, ExecutionException {
    JsonObject calibration = new JsonObject();
    for (int run = 0; run < PROBE_CALIBRATIONS; run++) {
      calibration.add("render", control.callOnClient(ProbeClock.RENDER::calibrate));
      calibration.add("server", control.callOnServer(ProbeClock.SERVER::calibrate));
    }
    return calibration;
  }

  private ClientView currentView() {
    return new ClientView(
        minecraft.options.renderDistance().get(), minecraft.options.entityDistanceScaling().get());
  }

  /** Render thread only; the integrated server follows the render distance on its next tick. */
  private void applyView(ClientView view) {
    minecraft.options.renderDistance().set(view.renderDistance());
    minecraft.options.entityDistanceScaling().set(view.entityDistanceScaling());
  }

  private JsonObject measureWorldExits()
      throws InterruptedException,
          ExecutionException,
          TimeoutException,
          IOException,
          ParseException {
    LOGGER.info("Benchmark: world exits");
    JsonObject scenario = new JsonObject();
    JsonArray afterSession = new JsonArray();
    afterSession.add(exitWorld("afterSession"));
    JsonArray afterRejoin = new JsonArray();
    JsonArray rejoins = new JsonArray();
    for (int round = 0; round < EXIT_REJOIN_ROUNDS; round++) {
      rejoins.add(rejoinWorld());
      afterRejoin.add(exitWorld("afterRejoin"));
    }
    rejoinWorld();
    Recording recording = JfrRecordings.start(jfrFile("world-exit"));
    exitWorld("profiling");
    JfrRecordings.finish(recording);
    scenario.add("exitsAfterSession", afterSession);
    scenario.add("exitsAfterRejoin", afterRejoin);
    scenario.add("rejoinMs", rejoins);
    return scenario;
  }

  private double rejoinWorld() throws InterruptedException, ExecutionException, TimeoutException {
    long start =
        control.callOnClient(
            () -> {
              long now = System.nanoTime();
              minecraft
                  .createWorldOpenFlows()
                  .openWorld(WORLD_NAME, () -> LOGGER.warn("Benchmark world open cancelled"));
              return now;
            });
    long joined = control.awaitClient(this::isInWorld, WORLD_TIMEOUT);
    teleport(MobFarmScene.CAMERA);
    Thread.sleep(EXIT_REJOIN_IDLE);
    return (joined - start) / NANOS_PER_MILLI;
  }

  /**
   * "Save and Quit to Title": the same {@code Minecraft#disconnectFromWorld} call the pause screen
   * makes, timed from the start of that call to the call's return, the title screen, and the
   * integrated server reporting shutdown.
   */
  private JsonObject exitWorld(String label)
      throws InterruptedException, ExecutionException, TimeoutException {
    IntegratedServer server = control.server();
    CompletableFuture<DisconnectCall> call =
        minecraft.submit(
            () -> {
              long start = System.nanoTime();
              minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
              return new DisconnectCall(start, System.nanoTime());
            });
    long serverStopped = NOT_SEEN;
    while (!call.isDone()) {
      if (serverStopped == NOT_SEEN && server.isShutdown()) {
        serverStopped = System.nanoTime();
      }
      Thread.sleep(POLL_MILLIS);
    }
    DisconnectCall disconnect = call.get();
    long title = control.awaitClient(this::isTitleReady, EXIT_TIMEOUT);
    long deadline = System.nanoTime() + EXIT_TIMEOUT.toNanos();
    while (serverStopped == NOT_SEEN && System.nanoTime() < deadline) {
      if (server.isShutdown()) {
        serverStopped = System.nanoTime();
      } else {
        Thread.sleep(POLL_MILLIS);
      }
    }
    JsonObject exit = new JsonObject();
    exit.addProperty("label", label);
    exit.addProperty("disconnectCallMs", (disconnect.end() - disconnect.start()) / NANOS_PER_MILLI);
    exit.addProperty("toTitleScreenMs", (title - disconnect.start()) / NANOS_PER_MILLI);
    exit.addProperty(
        "serverStoppedMs",
        serverStopped == NOT_SEEN
            ? NOT_SEEN
            : (serverStopped - disconnect.start()) / NANOS_PER_MILLI);
    Thread.sleep(SETTLE);
    return exit;
  }

  private JsonObject recordRound(Duration duration, RoundAction action)
      throws InterruptedException, ExecutionException {
    requireRunningGame("before a round");
    control.runOnClient(
        () -> {
          FrameTimings.start();
          FrameCacheProbe.start();
          ProbeClock.RENDER.start();
        });
    control.runOnServer(
        () -> {
          ServerTimings.start();
          ProbeClock.SERVER.start();
        });
    long chunksBefore = ServerTimings.generatedChunks();
    WorldgenProbes.Snapshot worldgenBefore = WorldgenProbes.snapshot();
    Map<Long, ThreadCpuGroups.ThreadCpu> threadsBefore = ThreadCpuGroups.snapshot();
    long start = System.nanoTime();
    action.run(duration);
    double seconds = (System.nanoTime() - start) / NANOS_PER_SECOND;
    ClientRound client =
        control.callOnClient(
            () ->
                new ClientRound(
                    FrameTimings.stop(), FrameCacheProbe.stop(), ProbeClock.RENDER.stop()));
    ServerRound server =
        control.callOnServer(() -> new ServerRound(ServerTimings.stop(), ProbeClock.SERVER.stop()));
    Map<Long, ThreadCpuGroups.ThreadCpu> threadsAfter = ThreadCpuGroups.snapshot();
    WorldgenProbes.Snapshot worldgenAfter = WorldgenProbes.snapshot();
    long chunks = ServerTimings.generatedChunks() - chunksBefore;
    FrameTimings.Samples frames = client.frames();
    ServerTimings.Samples ticks = server.ticks();
    requireRunningGame("after a round");
    if (ticks.tickDurations().length == 0) {
      throw new IllegalStateException("The integrated server did not tick during a round");
    }
    TimingStats renderWorkCpu = TimingStats.of(frames.renderWorkCpu());
    TimingStats serverTickCpu = TimingStats.of(ticks.tickCpu());
    JsonObject round = new JsonObject();
    round.addProperty("seconds", seconds);
    round.add("frameTime", TimingStats.of(frames.frameIntervals()).toJson());
    round.add("renderFrame", TimingStats.of(frames.renderDurations()).toJson());
    round.add("renderWork", TimingStats.of(frames.renderWorkDurations()).toJson());
    round.add("renderWorkCpu", renderWorkCpu.toJson());
    round.add("serverTick", TimingStats.of(ticks.tickDurations()).toJson());
    round.add("serverTickCpu", serverTickCpu.toJson());
    JsonObject candidates = new JsonObject();
    candidates.add(
        "render",
        client.probes().toJson(frames.renderWorkDurations().length, renderWorkCpu.mean()));
    candidates.add(
        "server", server.probes().toJson(ticks.tickDurations().length, serverTickCpu.mean()));
    candidates.add("worldgen", worldgenAfter.since(worldgenBefore, seconds));
    candidates.add(
        "threadCpuMsPerSecond", ThreadCpuGroups.since(threadsBefore, threadsAfter, seconds));
    round.add("candidates", candidates);
    round.addProperty("generatedChunks", chunks);
    round.addProperty("generatedChunksPerSecond", chunks / seconds);
    round.addProperty("droppedFrameSamples", frames.dropped());
    round.add("itemFrameCache", client.frameCache());
    return round;
  }

  /**
   * Fails the session when the game is paused or a screen is open: the integrated server then stops
   * ticking and syncing, so the round would silently measure an empty world.
   */
  private void requireRunningGame(String when) throws InterruptedException, ExecutionException {
    String problem =
        control.callOnClient(
            () -> {
              if (minecraft.isPaused()) {
                return "the game is paused";
              }
              return minecraft.gui.screen() == null
                  ? ""
                  : "screen " + minecraft.gui.screen().getClass().getName() + " is open";
            });
    if (!problem.isEmpty()) {
      throw new IllegalStateException("Invalid measurement " + when + ": " + problem);
    }
  }

  /** Teleports along +x at a fixed pace, so every session asks for the same new terrain. */
  private void fly(Duration duration) throws InterruptedException, ExecutionException {
    IntegratedServer server = control.server();
    long steps = duration.toNanos() / FLIGHT_STEP_INTERVAL.toNanos();
    long start = System.nanoTime();
    for (long step = 1; step <= steps; step++) {
      long wait = start + step * FLIGHT_STEP_INTERVAL.toNanos() - System.nanoTime();
      if (wait > 0) {
        Thread.sleep(Duration.ofNanos(wait));
      }
      flightX += FLIGHT_STEP_BLOCKS;
      CameraPose pose = flightPose();
      server.execute(() -> teleportPlayer(server, pose));
    }
  }

  private CameraPose flightPose() {
    return new CameraPose(flightX, FLIGHT_Y, FLIGHT_LANE_Z, EAST_YAW, LEVEL_PITCH);
  }

  private void teleport(CameraPose pose) throws InterruptedException, ExecutionException {
    IntegratedServer server = control.server();
    control.runOnServer(() -> teleportPlayer(server, pose));
  }

  private static void teleportPlayer(IntegratedServer server, CameraPose pose) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      player.teleportTo(
          server.overworld(),
          pose.x(),
          pose.y(),
          pose.z(),
          Set.<Relative>of(),
          pose.yaw(),
          pose.pitch(),
          true);
    }
  }

  /** Waits until no new chunk finished generating for two polls in a row (bounded). */
  private boolean awaitGenerationQuiet() throws InterruptedException {
    long deadline = System.nanoTime() + GENERATION_QUIET_TIMEOUT.toNanos();
    long previous = ServerTimings.generatedChunks();
    int quietPolls = 0;
    while (quietPolls < GENERATION_QUIET_POLLS) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(GENERATION_POLL);
      long current = ServerTimings.generatedChunks();
      quietPolls = current == previous ? quietPolls + 1 : 0;
      previous = current;
    }
    return true;
  }

  private boolean isTitleReady() {
    return minecraft.gui.overlay() == null && minecraft.gui.screen() instanceof TitleScreen;
  }

  private boolean isInWorld() {
    return minecraft.player != null && minecraft.level != null && minecraft.gui.screen() == null;
  }

  private Path jfrFile(String scenario) {
    return config.outputDirectory().resolve(scenario + ".jfr");
  }

  private static long epochMillisAt(long nanoTime) {
    return System.currentTimeMillis()
        - Math.round((System.nanoTime() - nanoTime) / NANOS_PER_MILLI);
  }

  private static double uptimeMillisAt(long nanoTime) {
    long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
    return uptime - (System.nanoTime() - nanoTime) / NANOS_PER_MILLI;
  }

  private void writeSession(JsonObject session) {
    Path file = config.outputDirectory().resolve("session.json");
    try {
      Files.createDirectories(config.outputDirectory());
      Files.writeString(
          file,
          new GsonBuilder().setPrettyPrinting().create().toJson(session),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Could not write {}", file, e);
    }
  }

  @FunctionalInterface
  private interface RoundAction {
    void run(Duration duration) throws InterruptedException, ExecutionException;
  }

  private record DisconnectCall(long start, long end) {}

  /** What the render thread hands back at the end of a round, read in one task. */
  private record ClientRound(
      FrameTimings.Samples frames, JsonObject frameCache, ProbeClock.Round probes) {}

  /** What the server thread hands back at the end of a round, read in one task. */
  private record ServerRound(ServerTimings.Samples ticks, ProbeClock.Round probes) {}

  /**
   * A measured scene: where it is, how to build and count it, its render distance and how long it
   * settles after the build before its census and rounds.
   */
  private record Scene(
      String key,
      String jfrStem,
      CameraPose camera,
      int renderDistance,
      Duration settle,
      Consumer<ServerLevel> builder,
      Function<ServerLevel, JsonObject> census,
      Function<Minecraft, JsonObject> clientCensus) {
    Scene {
      if (renderDistance > MAX_SCENE_RENDER_DISTANCE) {
        throw new IllegalArgumentException(
            "Scene render distance above "
                + MAX_SCENE_RENDER_DISTANCE
                + " breaks the entity distance compensation: "
                + jfrStem);
      }
    }
  }

  /** Client options a scene changes: render distance (chunks) and entity distance scaling. */
  private record ClientView(int renderDistance, double entityDistanceScaling) {}
}
