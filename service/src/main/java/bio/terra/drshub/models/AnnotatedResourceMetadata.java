package bio.terra.drshub.models;

import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.generated.model.ResourceMetadata;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@JsonSerialize(using = AnnotatedResourceMetadataSerializer.class)
public class AnnotatedResourceMetadata extends ResourceMetadata {

  private List<String> requestedFields;

  private DrsMetadata drsMetadata;

  private DrsProvider drsProvider;

  private String ip;
}
