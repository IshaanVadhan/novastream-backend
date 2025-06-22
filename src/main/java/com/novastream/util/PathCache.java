package com.novastream.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PathCache {

  private final Map<String, String> idToPathMap = new ConcurrentHashMap<>();

  public void cache(String id, String path) {
    idToPathMap.putIfAbsent(id, path);
  }

  public String getPath(String id) {
    return idToPathMap.get(id);
  }

  public boolean containsId(String id) {
    return idToPathMap.containsKey(id);
  }

  public void clear() {
    idToPathMap.clear();
  }
}
