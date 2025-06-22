package com.novastream.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class BinaryExtractor {

  private static final Map<String, File> extractedBinaries = new HashMap<>();

  public static void extractBinaries() {
    try {
      extractedBinaries.put(
        "ffmpeg",
        extractBinary("/bin/ffmpeg.exe", "ffmpeg.exe")
      );
      extractedBinaries.put(
        "ffprobe",
        extractBinary("/bin/ffprobe.exe", "ffprobe.exe")
      );
    } catch (IOException e) {
      System.out.println("Failed to extract binaries: " + e);
    }
  }

  private static File extractBinary(String resourcePath, String outputFileName)
    throws IOException {
    File tempFile = new File(
      System.getProperty("java.io.tmpdir"),
      outputFileName
    );

    if (tempFile.exists()) {
      System.out.println(
        "Binary already exists: " + tempFile.getAbsolutePath()
      );
      return tempFile;
    }

    InputStream in = BinaryExtractor.class.getResourceAsStream(resourcePath);
    if (in == null) {
      System.out.println("Resource not found: " + resourcePath);
    }

    try (OutputStream out = new FileOutputStream(tempFile)) {
      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    }

    tempFile.setExecutable(true);
    System.out.println("Extracted binary to: " + tempFile.getAbsolutePath());
    return tempFile;
  }

  public static String getBinaryPath(String name) {
    File file = extractedBinaries.get(name);
    return file != null ? file.getAbsolutePath() : null;
  }
}
