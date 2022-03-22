package bio.terra.drshub.models;

import static bio.terra.drshub.services.MetadataService.getGcsAccessURL;
import static bio.terra.drshub.services.MetadataService.getHashesMap;
import static bio.terra.drshub.services.MetadataService.gsUriParseRegex;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import org.springframework.boot.jackson.JsonComponent;

@JsonComponent
public class AnnotatedResourceMetadataSerializer extends JsonSerializer<AnnotatedResourceMetadata> {

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
          jsonGenerator.writeStringField(
              Fields.TIME_CREATED, formatter.format(response.getCreatedTime().toInstant()));
        }
        if (f.equals(Fields.TIME_UPDATED)) {
          jsonGenerator.writeStringField(
              Fields.TIME_UPDATED, formatter.format(response.getUpdatedTime().toInstant()));
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
}
