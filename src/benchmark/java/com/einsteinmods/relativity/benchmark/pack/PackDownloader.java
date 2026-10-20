package com.einsteinmods.relativity.benchmark.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Dev-only: fills the pack directory with exactly the pinned {@link PackMod#ALL} jars. Each file
 * URL comes from the Modrinth API ({@code GET /v2/version/{id}}), the version number and file name
 * must match the pin, and the sha512 is verified before the jar is kept. Jars already present with
 * the right hash are not downloaded again; any other jar in the directory is removed.
 */
public final class PackDownloader {
  private static final String API_VERSION_URL = "https://api.modrinth.com/v2/version/";
  private static final String USER_AGENT = "EinsteinMods/relativity-benchmark (dev-only)";
  private static final String HASH_ALGORITHM = "SHA-512";
  private static final String JAR_SUFFIX = ".jar";
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final int HTTP_OK = 200;

  private PackDownloader() {}

  public static void main(String[] arguments) throws IOException, InterruptedException {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Usage: PackDownloader <pack directory>");
    }
    Path directory = Path.of(arguments[0]);
    Files.createDirectories(directory);
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    Set<String> pinnedFiles = new HashSet<>();
    for (PackMod mod : PackMod.ALL) {
      pinnedFiles.add(mod.fileName());
      Path target = directory.resolve(mod.fileName());
      if (Files.exists(target) && mod.sha512().equals(sha512(Files.readAllBytes(target)))) {
        continue;
      }
      byte[] jar = get(client, fileUrl(client, mod), mod.name());
      String actual = sha512(jar);
      if (!mod.sha512().equals(actual)) {
        throw new IOException(mod.name() + ": sha512 mismatch, got " + actual);
      }
      Files.write(target, jar);
      System.out.println("Downloaded " + mod.name() + " " + mod.versionNumber());
    }
    removeUnpinnedJars(directory, pinnedFiles);
    System.out.println("Pack ready: " + PackMod.ALL.size() + " jars in " + directory);
  }

  private static URI fileUrl(HttpClient client, PackMod mod)
      throws IOException, InterruptedException {
    byte[] body = get(client, URI.create(API_VERSION_URL + mod.versionId()), mod.name());
    JsonObject version =
        JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
    String versionNumber = version.get("version_number").getAsString();
    if (!mod.versionNumber().equals(versionNumber)) {
      throw new IOException(
          mod.name() + ": pinned " + mod.versionNumber() + ", API has " + versionNumber);
    }
    for (JsonElement element : version.getAsJsonArray("files")) {
      JsonObject file = element.getAsJsonObject();
      if (mod.fileName().equals(file.get("filename").getAsString())) {
        return URI.create(file.get("url").getAsString());
      }
    }
    throw new IOException(
        mod.name() + ": file " + mod.fileName() + " not in version " + mod.versionId());
  }

  private static byte[] get(HttpClient client, URI uri, String what)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != HTTP_OK) {
      throw new IOException(what + ": HTTP " + response.statusCode() + " for " + uri);
    }
    return response.body();
  }

  private static String sha512(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(HASH_ALGORITHM + " is required by every JDK", e);
    }
  }

  private static void removeUnpinnedJars(Path directory, Set<String> pinnedFiles)
      throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString();
        if (name.endsWith(JAR_SUFFIX) && !pinnedFiles.contains(name)) {
          Files.delete(file);
          System.out.println("Removed unpinned " + name);
        }
      }
    }
  }
}
