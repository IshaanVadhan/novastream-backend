package com.novastream.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaStreamDto {

  public Resource resource;
  public HttpHeaders headers;
  public MediaType mediaType;
  public HttpStatus status;
}
