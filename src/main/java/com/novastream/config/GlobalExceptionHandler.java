package com.novastream.config;

import com.novastream.util.ResponseHandler;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(
    GlobalExceptionHandler.class
  );

  @Autowired
  private ResponseHandler responseHandler;

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Object> handleNotFound(
    NoSuchElementException ex,
    WebRequest request
  ) {
    logger.debug(
      "Resource not found: {}",
      ex.getMessage(),
      request.getDescription(false)
    );
    return responseHandler.create(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(
    org.springframework.web.servlet.NoHandlerFoundException.class
  )
  public ResponseEntity<Object> handleNotFoundEndpoint(
    org.springframework.web.servlet.NoHandlerFoundException ex,
    WebRequest request
  ) {
    logger.debug(
      "Resource not found: {}",
      ex.getMessage(),
      request.getDescription(false)
    );
    return responseHandler.create(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(
    org.springframework.web.HttpRequestMethodNotSupportedException.class
  )
  public ResponseEntity<Object> handleMethodNotAllowed(
    org.springframework.web.HttpRequestMethodNotSupportedException ex,
    WebRequest request
  ) {
    logger.debug(
      "Method not allowed: {}",
      ex.getMessage(),
      request.getDescription(false)
    );
    return responseHandler.create(
      HttpStatus.METHOD_NOT_ALLOWED,
      "HTTP method not supported"
    );
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Object> handleBadRequest(
    IllegalArgumentException ex,
    WebRequest request
  ) {
    logger.debug(
      "Bad request: {}",
      ex.getMessage(),
      request.getDescription(false)
    );
    return responseHandler.create(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleGeneric(
    Exception ex,
    WebRequest request
  ) {
    String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
    String requestDescription = request.getDescription(false);

    if (ex.getCause() instanceof OutOfMemoryError) {
      logger.debug(
        "OutOfMemoryError occurred during request: {} - Triggering GC",
        requestDescription
      );
      System.gc();
      if (acceptHeader != null && acceptHeader.contains("video")) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
      }
      return responseHandler.create(
        HttpStatus.SERVICE_UNAVAILABLE,
        "The server is temporarily overloaded."
      );
    }

    if (
      ex.getCause() instanceof org.apache.catalina.connector.ClientAbortException ||
      ex instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException
    ) {
      logger.debug(
        "Client disconnected during streaming: {}",
        requestDescription
      );
      return ResponseEntity.status(HttpStatus.OK).build();
    }

    if (acceptHeader != null && acceptHeader.contains("video")) {
      logger.debug(
        "Error during video streaming: {}",
        ex.getMessage(),
        requestDescription,
        ex
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    logger.debug(
      "Unhandled exception: {}",
      ex.getMessage(),
      requestDescription,
      ex
    );
    return responseHandler.create(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Something went wrong. Please try again later."
    );
  }
}
