package com.novastream.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Media {

  private String id;
  private String name;
  private String path;
  private Boolean isDirectory;
  private Long size;
  private Long filesCount;
  private List<Subtitle> subtitleLanguages;
}
