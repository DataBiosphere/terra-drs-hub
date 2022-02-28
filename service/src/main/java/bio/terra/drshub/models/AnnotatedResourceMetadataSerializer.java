package bio.terra.drshub.models;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Checksum;
import io.github.ga4gh.drs.model.DrsObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.jackson.JsonComponent;

@JsonComponent
public class AnnotatedResourceMetadataSerializer extends JsonSerializer<AnnotatedResourceMetadata> {

  private static final Pattern gsUriParseRegex =
      Pattern.compile("gs://(?<bucket>[^/]+)/(?<name>.+)", Pattern.CASE_INSENSITIVE);

  @Override
  public void serialize(
      AnnotatedResourceMetadata value, JsonGenerator jgen, SerializerProvider provider)
      throws IOException {
    jgen.writeStartObject();

    var drsProvider = value.getDrsProvider();
    var drsMetadata = value.getDrsMetadata();

    for (var f : value.getRequestedFields()) {
      if (f.equals(Fields.BOND_PROVIDER)) {
        jgen.writeStringField(
            Fields.BOND_PROVIDER, drsProvider.getBondProvider().map(Enum::toString).orElse(null));
      }

      if (f.equals(Fields.FILE_NAME)) {
        jgen.writeStringField(Fields.FILE_NAME, drsMetadata.getFileName().orElse(null));
      }
      if (f.equals(Fields.LOCALIZATION_PATH)) {
        jgen.writeStringField(
            Fields.LOCALIZATION_PATH, drsMetadata.getLocalizationPath().orElse(null));
      }
      if (f.equals(Fields.ACCESS_URL)) {
        jgen.writePOJOField(Fields.ACCESS_URL, drsMetadata.getAccessUrl().orElse(null));
      }
      if (f.equals(Fields.GOOGLE_SERVICE_ACCOUNT)) {
        jgen.writeStringField(
            Fields.GOOGLE_SERVICE_ACCOUNT, drsMetadata.getBondSaKey().orElse(null));
      }

      if (drsMetadata.getDrsResponse().isPresent()) {

        var response = drsMetadata.getDrsResponse().get();

        if (f.equals(Fields.TIME_CREATED)) {
          jgen.writePOJOField(Fields.TIME_CREATED, response.getCreatedTime());
        }
        if (f.equals(Fields.TIME_UPDATED)) {
          jgen.writePOJOField(Fields.TIME_UPDATED, response.getUpdatedTime());
        }
        if (f.equals(Fields.HASHES)) {
          jgen.writePOJOField(Fields.HASHES, getHashesMap(response.getChecksums()));
        }
        if (f.equals(Fields.SIZE)) {
          jgen.writeNumberField(Fields.SIZE, response.getSize());
        }
        if (f.equals(Fields.CONTENT_TYPE)) {
          jgen.writeStringField(Fields.CONTENT_TYPE, response.getMimeType());
        }

        var gsUrl = getGcsAccessURL(response).map(AccessURL::getUrl);
        if (f.equals(Fields.GS_URI)) {
          jgen.writeStringField(Fields.GS_URI, gsUrl.orElse(null));
        }

        var gsFileInfo = gsUrl.map(gsUriParseRegex::matcher);
        if (gsFileInfo.map(Matcher::matches).orElse(false)) {
          if (f.equals(Fields.BUCKET)) {
            jgen.writeStringField(Fields.BUCKET, gsFileInfo.get().group("bucket"));
          }
          if (f.equals(Fields.NAME)) {
            jgen.writeStringField(Fields.NAME, gsFileInfo.get().group("name"));
          }
        }
      }
    }
    jgen.writeEndObject();
  }

  private Map<String, String> getHashesMap(List<Checksum> checksums) {
    return checksums.isEmpty()
        ? null
        : checksums.stream().collect(Collectors.toMap(Checksum::getType, Checksum::getChecksum));
  }

  private Optional<AccessURL> getGcsAccessURL(DrsObject drsObject) {
    return drsObject.getAccessMethods().stream()
        .filter(m -> m.getType() == AccessMethod.TypeEnum.GS)
        .findFirst()
        .map(AccessMethod::getAccessUrl);
  }
}
