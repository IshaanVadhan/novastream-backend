package com.novastream.controller;

import com.novastream.dto.MediaDto;
import com.novastream.dto.MediaStreamDto;
import com.novastream.service.MediaService;
import com.novastream.service.SubtitleService;
import com.novastream.util.ResponseHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/media")
public class MediaController {

  @Autowired
  private MediaService mediaService;

  @Autowired
  private SubtitleService subtitleService;

  @Autowired
  private ResponseHandler responseHandler;

  @GetMapping("/ping")
  public ResponseEntity<Object> getConfig() {
    return responseHandler.create(HttpStatus.OK, "pong");
  }

  @GetMapping("/list")
  public ResponseEntity<Object> listAll(
    @RequestParam(required = false) String id
  ) {
    List<MediaDto> mediaDto = mediaService.listMedia(id);
    return responseHandler.create(HttpStatus.OK, mediaDto);
  }

  @GetMapping("/stream")
  public ResponseEntity<Object> stream(
    @RequestParam String videoId,
    @RequestHeader(value = "Range", required = false) String range
  ) {
    MediaStreamDto MediaStreamDto = mediaService.streamVideo(videoId, range);
    return responseHandler.stream(
      MediaStreamDto.status,
      MediaStreamDto.resource,
      MediaStreamDto.mediaType,
      MediaStreamDto.headers
    );
  }

  @GetMapping("/subtitles")
  public ResponseEntity<Object> getSubtitles(
    @RequestParam String videoId,
    @RequestParam String lang
  ) {
    MediaStreamDto MediaStreamDto = subtitleService.getSubtitlesForVideo(
      videoId,
      lang
    );
    return responseHandler.stream(
      MediaStreamDto.status,
      MediaStreamDto.resource,
      MediaStreamDto.mediaType,
      MediaStreamDto.headers
    );
  }
}
