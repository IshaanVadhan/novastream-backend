package com.novastream.service;

import com.novastream.config.MediaConfig;
import com.novastream.dto.MediaDto;
import com.novastream.dto.MediaStreamDto;
import com.novastream.model.Media;
import com.novastream.model.Subtitle;
import com.novastream.util.GenericMapper;
import com.novastream.util.PathCache;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MediaService {

  @Autowired
  private MediaConfig mediaConfig;

  @Autowired
  private SubtitleService subtitleService;

  @Autowired
  private GenericMapper genericMapper;

  @Autowired
  private PathCache pathCache;

  private final AtomicInteger activeStreams = new AtomicInteger(0);

  public static String chooseFolder() {
    try {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Select Media Folder");
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setAcceptAllFileFilterUsed(false);

      int result = chooser.showOpenDialog(null);
      if (result == JFileChooser.APPROVE_OPTION) {
        String selectedPath = chooser.getSelectedFile().getAbsolutePath();
        return selectedPath;
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  private String generateId(String path) {
    try {
      if (!StringUtils.hasText(path)) {
        throw new IllegalArgumentException("Path cannot be null or empty");
      }

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(path.getBytes(StandardCharsets.UTF_8));
      return new BigInteger(1, hash).toString(16);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Unable to generate file ID: ", e);
    }
  }

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

  public List<MediaDto> listMedia(String id) {
    String path;

    if (id == null) {
      path = mediaConfig.getBasePath();
      if (!StringUtils.hasText(path)) {
        throw new IllegalArgumentException(
          "No base media folder selected. Please select a folder first."
        );
      }
    } else {
      path = pathCache.getPath(id);
      if (path == null) {
        throw new IllegalArgumentException("Invalid ID!");
      }
    }

    File directory = new File(path);
    if (!directory.exists() || !directory.isDirectory()) {
      throw new IllegalArgumentException("Invalid directory path!");
    }

    List<MediaDto> children = new ArrayList<>();
    for (File file : directory.listFiles()) {
      List<Subtitle> subtitleLanguages = null;
      if (
        (!file.isDirectory() && !isVideoFile(file)) ||
        (file.getName().equalsIgnoreCase("subs")) ||
        (file.isHidden())
      ) {
        continue;
      }
      if (!file.isDirectory() && isVideoFile(file)) {
        subtitleLanguages = subtitleService.getSubtitleLanguages(file);
        subtitleService.extractSubtitles(file);
      }
      String generatedId = generateId(file.getAbsolutePath());
      Media media = new Media(
        generatedId,
        file.getName(),
        file.getAbsolutePath(),
        file.isDirectory(),
        file.isDirectory() ? null : file.length(),
        file.isDirectory()
          ? (long) (file.list() != null ? file.list().length : 0)
          : null,
        subtitleLanguages
      );
      pathCache.cache(generatedId, file.getAbsolutePath());
      MediaDto mediaDto = genericMapper.toDto(media, MediaDto.class);
      children.add(mediaDto);
    }
    children.sort(
      new Comparator<MediaDto>() {
        @Override
        public int compare(MediaDto a, MediaDto b) {
          if (a.getIsDirectory() && !b.getIsDirectory()) return -1;
          if (!a.getIsDirectory() && b.getIsDirectory()) return 1;
          return a.getName().compareToIgnoreCase(b.getName());
        }
      }
    );
    return children;
  }

  public MediaStreamDto streamVideo(String videoId, String range) {
    if (activeStreams.get() >= mediaConfig.getMaxConcurrentStreams()) {
      throw new RuntimeException(
        "Too many concurrent streams. Please try again later."
      );
    }

    try {
      activeStreams.incrementAndGet();

      String path = pathCache.getPath(videoId);
      if (path == null) {
        throw new IllegalArgumentException("Video not found");
      }

      Path filePath = Paths.get(path);
      if (!Files.exists(filePath)) {
        throw new IllegalArgumentException("Video file not found");
      }

      long fileLength = filePath.toFile().length();
      String contentType = Files.probeContentType(filePath);
      if (contentType == null) {
        contentType = "application/octet-stream";
      }

      if (range == null) {
        Resource resource = new FileSystemResource(filePath);
        return new MediaStreamDto(
          resource,
          new HttpHeaders(),
          MediaType.parseMediaType(contentType),
          HttpStatus.OK
        );
      }

      long rangeStart, rangeEnd;
      try {
        String[] ranges = range.replace("bytes=", "").split("-");
        rangeStart = Long.parseLong(ranges[0]);

        long requestedEnd = ranges.length > 1 && !ranges[1].isEmpty()
          ? Long.parseLong(ranges[1])
          : rangeStart + mediaConfig.getChunkSize() - 1;

        rangeEnd =
          Math.min(requestedEnd, rangeStart + mediaConfig.getChunkSize() - 1);
        rangeEnd = Math.min(rangeEnd, fileLength - 1);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid range format");
      }

      long contentLength = rangeEnd - rangeStart + 1;

      if (contentLength > mediaConfig.getChunkSize()) {
        contentLength = mediaConfig.getChunkSize();
        rangeEnd = rangeStart + contentLength - 1;
      }

      byte[] data = new byte[(int) contentLength];

      try (
        InputStream inputStream = Files.newInputStream(filePath);
        java.io.BufferedInputStream bufferedStream = new java.io.BufferedInputStream(
          inputStream,
          8192
        )
      ) {
        bufferedStream.skip(rangeStart);

        int totalBytesRead = 0;
        int bytesToRead = (int) contentLength;

        while (totalBytesRead < bytesToRead) {
          int bytesRead = bufferedStream.read(
            data,
            totalBytesRead,
            bytesToRead - totalBytesRead
          );
          if (bytesRead == -1) break;
          totalBytesRead += bytesRead;
        }

        if (totalBytesRead < contentLength) {
          byte[] actualData = new byte[totalBytesRead];
          System.arraycopy(data, 0, actualData, 0, totalBytesRead);
          data = actualData;
          contentLength = totalBytesRead;
          rangeEnd = rangeStart + totalBytesRead - 1;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(
          "Content-Range",
          "bytes " + rangeStart + "-" + rangeEnd + "/" + fileLength
        );
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("Accept-Ranges", "bytes");
        headers.setContentLength(contentLength);

        return new MediaStreamDto(
          new ByteArrayResource(data),
          headers,
          MediaType.parseMediaType(contentType),
          HttpStatus.PARTIAL_CONTENT
        );
      }
    } catch (IOException e) {
      throw new RuntimeException("Video stream failed: " + e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error during video streaming", e);
    } finally {
      activeStreams.decrementAndGet();
    }
  }
}
