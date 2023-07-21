package bio.terra.drshub.models;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.github.ga4gh.drs.model.AccessMethod;
import io.github.ga4gh.drs.model.AccessURL;
import io.github.ga4gh.drs.model.Checksum;
import io.github.ga4gh.drs.model.DrsObject;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Date;
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
      AnnotatedResourceMetadata value, JsonGenerator jsonGenerator, SerializerProvider provider)
      throws IOException {
    jsonGenerator.writeStartObject();

    var drsMetadata = value.getDrsMetadata();

    for (var f : value.getRequestedFields()) {
      if (f.equals(Fields.BOND_PROVIDER)) {
        jsonGenerator.writeStringField(
            Fields.BOND_PROVIDER,
            value.getDrsProvider().getBondProvider().map(Enum::toString).orElse(null));
      }

      if (f.equals(Fields.FILE_NAME)) {
        jsonGenerator.writeStringField(Fields.FILE_NAME, drsMetadata.getFileName());
      }
      if (f.equals(Fields.LOCALIZATION_PATH)) {
        jsonGenerator.writeStringField(Fields.LOCALIZATION_PATH, drsMetadata.getLocalizationPath());
      }
      if (f.equals(Fields.ACCESS_URL)) {
        jsonGenerator.writePOJOField(Fields.ACCESS_URL, drsMetadata.getAccessUrl());
      }
      if (f.equals(Fields.GOOGLE_SERVICE_ACCOUNT)) {
        jsonGenerator.writePOJOField(Fields.GOOGLE_SERVICE_ACCOUNT, drsMetadata.getBondSaKey());
      }

      if (drsMetadata.getDrsResponse() != null) {

        var response = drsMetadata.getDrsResponse();
        var formatter = DateTimeFormatter.ISO_INSTANT;

        if (f.equals(Fields.TIME_CREATED)) {
          var maybeDate =
              Optional.ofNullable(response.getCreatedTime())
                  .map(Date::toInstant)
                  .map(formatter::format);
          jsonGenerator.writeStringField(Fields.TIME_CREATED, maybeDate.orElse(null));
        }
        if (f.equals(Fields.TIME_UPDATED)) {
          var maybeDate =
              Optional.ofNullable(response.getUpdatedTime())
                  .map(Date::toInstant)
                  .map(formatter::format);
          jsonGenerator.writeStringField(Fields.TIME_UPDATED, maybeDate.orElse(null));
        }
        if (f.equals(Fields.HASHES)) {
          jsonGenerator.writePOJOField(Fields.HASHES, getHashesMap(response.getChecksums()));
        }
        if (f.equals(Fields.SIZE)) {
          jsonGenerator.writeNumberField(Fields.SIZE, response.getSize());
        }
        if (f.equals(Fields.CONTENT_TYPE)) {
          jsonGenerator.writeStringField(Fields.CONTENT_TYPE, response.getMimeType());
        }

        var gsUrl = getGcsAccessURL(response);
        if (f.equals(Fields.GS_URI)) {
          jsonGenerator.writeStringField(Fields.GS_URI, gsUrl.orElse(null));
        }

        var gsFileInfo = gsUrl.map(gsUriParseRegex::matcher);
        if (gsFileInfo.map(Matcher::matches).orElse(false)) {
          if (f.equals(Fields.BUCKET)) {
            jsonGenerator.writeStringField(Fields.BUCKET, gsFileInfo.get().group("bucket"));
          }
          if (f.equals(Fields.NAME)) {
            jsonGenerator.writeStringField(Fields.NAME, gsFileInfo.get().group("name"));
          }
        }
      }
    }
    jsonGenerator.writeEndObject();
  }

  private Map<String, String> getHashesMap(List<Checksum> checksums) {
    return checksums.isEmpty()
        ? null
        : checksums.stream().collect(Collectors.toMap(Checksum::getType, Checksum::getChecksum));
  }

  private Optional<String> getGcsAccessURL(DrsObject drsObject) {
    return drsObject.getAccessMethods().stream()
        .filter(m -> m.getType() == AccessMethod.TypeEnum.GS)
        .findFirst()
        .map(AccessMethod::getAccessUrl)
        .map(AccessURL::getUrl);
  }
}
