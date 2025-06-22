package com.novastream.util;

import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ResponseHandler {

  public ResponseEntity<Object> create(HttpStatus status) {
    return ResponseEntity.status(status).body(null);
  }

  public ResponseEntity<Object> create(HttpStatus status, String message) {
    return ResponseEntity
      .status(status)
      .body(buildResponse(message, status, null));
  }

  public ResponseEntity<Object> create(HttpStatus status, Object data) {
    return ResponseEntity
      .status(status)
      .body(buildResponse(null, status, data));
  }

  public ResponseEntity<Object> create(
    HttpStatus status,
    String message,
    Object data
  ) {
    return ResponseEntity
      .status(status)
      .body(buildResponse(message, status, data));
  }

  public ResponseEntity<Object> stream(
    HttpStatus status,
    Resource resource,
    MediaType mediaType
  ) {
    return stream(status, resource, mediaType, null);
  }

  public ResponseEntity<Object> stream(
    HttpStatus status,
    Resource resource,
    MediaType mediaType,
    HttpHeaders headers
  ) {
    ResponseEntity.BodyBuilder responseBuilder = ResponseEntity
      .status(status)
      .contentType(mediaType);
    if (headers != null) {
      responseBuilder.headers(headers);
    }

    return responseBuilder.body(resource);
  }

  private static Map<String, Object> buildResponse(
    String message,
    HttpStatus status,
    Object data
  ) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", status);
    if (message != null) {
      response.put("message", message);
    }
    if (data != null) {
      response.put("data", data);
    }
    return response;
  }
}
