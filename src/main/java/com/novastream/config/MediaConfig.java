package com.novastream.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
@Getter
@Setter
public class MediaConfig {

  private String basePath;
  private long maxSize = 1024 * 1024;
  private int chunkSize = 1024 * 1024;
  private int maxConcurrentStreams = 10;
}
