package com.novastream.dto;

import com.novastream.model.Subtitle;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaDto {

  private String id;
  private String name;
  private Boolean isDirectory;
  private Long size;
  private Long filesCount;
  private List<Subtitle> subtitleLanguages;
}
