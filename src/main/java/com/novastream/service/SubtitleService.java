package com.novastream.service;

import com.novastream.dto.MediaStreamDto;
import com.novastream.model.Subtitle;
import com.novastream.util.BinaryExtractor;
import com.novastream.util.PathCache;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class SubtitleService {

  private static final Logger logger = LoggerFactory.getLogger(
    SubtitleService.class
  );

  private final String ffmpegPath = BinaryExtractor.getBinaryPath("ffmpeg");

  private final String ffprobePath = BinaryExtractor.getBinaryPath("ffprobe");

  @Autowired
  private PathCache pathCache;

  private Boolean isVideoFile(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return false;
    }

    try {
      Path path = Paths.get(file.getAbsolutePath());
      String mimeType = Files.probeContentType(path);
      return mimeType != null && mimeType.startsWith("video");
    } catch (IOException e) {
      return false;
    }
  }

  private String getLanguageName(String code) {
    if (code == null || code.isBlank() || code.equalsIgnoreCase("und")) {
      return "Unknown";
    }

    Locale locale = Locale.forLanguageTag(code);
    String name = locale.getDisplayLanguage(Locale.ENGLISH);
    return name;
  }

  public List<Subtitle> getSubtitleLanguages(File videoFile) {
    List<Subtitle> subtitleLanguages = new ArrayList<>();
    String videoName = videoFile
      .getName()
      .substring(0, videoFile.getName().lastIndexOf('.'));
    File parent = videoFile.getParentFile();

    @SuppressWarnings("unused")
    File[] externalSubs = parent.listFiles((dir, name) ->
      name.toLowerCase().startsWith(videoName.toLowerCase()) &&
      name.toLowerCase().endsWith(".srt")
    );

    if (externalSubs != null) {
      for (@SuppressWarnings("unused") File sub : externalSubs) {
        String lang = "und";

        subtitleLanguages.add(new Subtitle(getLanguageName(lang), lang));
      }
    }

    try {
      Process probe = new ProcessBuilder(
        ffprobePath,
        "-v",
        "error",
        "-select_streams",
        "s",
        "-show_entries",
        "stream=index,codec_name:stream_tags=language",
        "-of",
        "csv=p=0",
        videoFile.getAbsolutePath()
      )
        .redirectErrorStream(true)
        .start();

      InputStream stream = probe.getInputStream();
      String output = new String(stream.readAllBytes());
      stream.close();
      probe.waitFor();

      for (String line : output.split("\n")) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          String[] parts = trimmed.split(",");
          if (parts.length >= 2) {
            String codec = parts[1].trim().toLowerCase();
            String lang = (parts.length >= 3 && !parts[2].isEmpty())
              ? parts[2].trim().toLowerCase()
              : "und";

            if (
              codec.equals("ass") ||
              codec.equals("subrip") ||
              codec.equals("ssa") ||
              codec.equals("srt")
            ) {
              subtitleLanguages.add(new Subtitle(getLanguageName(lang), lang));
            }
          } else {
            subtitleLanguages.add(new Subtitle(getLanguageName("und"), "und"));
          }
        }
      }
    } catch (IOException | InterruptedException e) {
      logger.debug("Failed to fetch subtitles: " + e.getMessage());
    }

    return subtitleLanguages;
  }

  private Boolean areSubtitlesAlreadyExtracted(File videoFile) {
    String videoName = videoFile
      .getName()
      .substring(0, videoFile.getName().lastIndexOf('.'));
    File parent = videoFile.getParentFile();

    File subsFolder = new File(parent, "subs" + File.separator + videoName);
    return subsFolder.exists() && subsFolder.isDirectory();
  }

  @Async
  public void extractSubtitles(File videoFile) {
    if (areSubtitlesAlreadyExtracted(videoFile)) {
      logger.debug("Subtitles already extracted for: " + videoFile.getName());
      return;
    }

    String videoName = videoFile
      .getName()
      .substring(0, videoFile.getName().lastIndexOf('.'));
    File parent = videoFile.getParentFile();

    String subsFolderPath =
      parent.getAbsolutePath() +
      File.separator +
      "subs" +
      File.separator +
      videoName;
    File subsFolder = new File(subsFolderPath);
    subsFolder.mkdirs();

    @SuppressWarnings("unused")
    File[] externalSubs = parent.listFiles((dir, name) ->
      name.toLowerCase().startsWith(videoName.toLowerCase()) &&
      name.toLowerCase().endsWith(".srt")
    );
    if (externalSubs != null && externalSubs.length > 0) {
      for (int i = 0; i < externalSubs.length; i++) {
        File srt = externalSubs[i];
        String subFileName = "und";
        if (externalSubs.length > 1) subFileName += "-" + i;
        subFileName += ".srt";
        File dest = new File(subsFolder, subFileName);
        try {
          Files.copy(
            srt.toPath(),
            dest.toPath(),
            StandardCopyOption.REPLACE_EXISTING
          );
        } catch (IOException e) {
          logger.debug("Failed to copy external subtitle: " + srt.getName());
          continue;
        }
      }
    }

    try {
      Process probe = new ProcessBuilder(
        "ffprobe",
        "-v",
        "error",
        "-select_streams",
        "s",
        "-show_entries",
        "stream=index,codec_name:stream_tags=language",
        "-of",
        "csv=p=0",
        videoFile.getAbsolutePath()
      )
        .start();

      InputStream stream = probe.getInputStream();
      String output = new String(stream.readAllBytes());
      stream.close();
      probe.waitFor();

      List<Integer> subtitleIndices = new ArrayList<>();
      List<String> subtitleLangs = new ArrayList<>();
      List<String> subtitleCodecs = new ArrayList<>();

      String[] lines = output.split("\n");

      for (String line : lines) {
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] parts = line.split(",");
        if (parts.length >= 1) {
          try {
            int streamIndex = Integer.parseInt(parts[0].trim());
            String codec = parts[1].trim().toLowerCase();
            String lang = (parts.length >= 3 && !parts[2].isEmpty())
              ? parts[2].trim().toLowerCase()
              : "und";
            subtitleIndices.add(streamIndex);
            subtitleCodecs.add(codec);
            subtitleLangs.add(lang);
          } catch (NumberFormatException e) {
            logger.debug("Invalid stream index in ffprobe output: " + line);
          }
        }
      }

      Map<String, Integer> langCounter = new HashMap<>();

      for (int i = 0; i < subtitleIndices.size(); i++) {
        int streamIndex = subtitleIndices.get(i);
        String codec = subtitleCodecs.get(i);
        String lang = subtitleLangs.get(i);

        if (
          !(
            codec.equals("ass") ||
            codec.equals("subrip") ||
            codec.equals("ssa") ||
            codec.equals("srt")
          )
        ) {
          logger.debug(
            "Skipping image-based subtitle stream " +
            lang +
            " (" +
            codec +
            ") for \"" +
            videoName +
            "\""
          );
          continue;
        }

        langCounter.put(lang, langCounter.getOrDefault(lang, 0) + 1);
        String subFileName = lang;
        int count = langCounter.get(lang);
        if (count > 1) {
          subFileName += "-" + (count - 1);
        }
        subFileName += ".srt";
        String subFilePath = subsFolderPath + File.separator + subFileName;

        ProcessBuilder builder = new ProcessBuilder(
          ffmpegPath,
          "-y",
          "-i",
          videoFile.getAbsolutePath(),
          "-map",
          "0:" + streamIndex,
          "-c:s",
          "srt",
          subFilePath
        )
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = builder.start();
        int exitCode = p.waitFor();

        if (exitCode != 0) {
          logger.debug(
            "Failed to extract embedded subtitle stream " + streamIndex
          );
        } else {
          logger.debug("Extracted subtitle: " + subFileName);
        }
      }
    } catch (IOException | InterruptedException e) {
      logger.debug(
        "Subtitle extraction failed for " +
        videoFile.getName() +
        ": " +
        e.getMessage()
      );
    }
  }

  public MediaStreamDto getSubtitlesForVideo(String videoId, String langCode) {
    String videoPath = pathCache.getPath(videoId);
    File videoFile = new File(videoPath);
    if (videoFile == null || !videoFile.exists() || !isVideoFile(videoFile)) {
      throw new RuntimeException("Video not found for id: " + videoId);
    }

    String videoName = videoFile
      .getName()
      .substring(0, videoFile.getName().lastIndexOf('.'));
    File parent = videoFile.getParentFile();
    File subsFolder = new File(parent, "subs" + File.separator + videoName);

    if (!subsFolder.exists() || !subsFolder.isDirectory()) {
      throw new RuntimeException(
        "No subtitles folder found for video: " + videoName
      );
    }

    @SuppressWarnings("unused")
    File[] subtitleFiles = subsFolder.listFiles((dir, name) -> {
      String lowerName = name.toLowerCase(Locale.ENGLISH);
      return lowerName.equals(langCode.toLowerCase(Locale.ENGLISH) + ".srt");
    });

    if (subtitleFiles == null || subtitleFiles.length == 0) {
      throw new RuntimeException(
        "No subtitles found for language '" +
        langCode +
        "' in video: " +
        videoName
      );
    }

    File subtitleFile = subtitleFiles[0];

    try {
      Path path = subtitleFile.toPath();
      if (!Files.exists(path) || !Files.isReadable(path)) {
        throw new IOException("Subtitle file not found or unreadable.");
      }
      FileSystemResource resource = new FileSystemResource(path);
      HttpHeaders headers = new HttpHeaders();
      headers.add(
        HttpHeaders.CONTENT_DISPOSITION,
        "inline; filename=\"" + subtitleFile.getName() + "\""
      );
      headers.setContentLength(Files.size(path));

      return new MediaStreamDto(
        resource,
        headers,
        MediaType.parseMediaType(MimeTypeUtils.TEXT_PLAIN_VALUE),
        HttpStatus.OK
      );
    } catch (IOException e) {
      throw new RuntimeException(
        "Failed to load subtitle: " + e.getMessage(),
        e
      );
    }
  }
}
