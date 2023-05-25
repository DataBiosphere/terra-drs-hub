package bio.terra.drshub.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Fields {

  public static final String ACCESS_URL = "accessUrl";
  public static final String BOND_PROVIDER = "bondProvider";
  public static final String BUCKET = "bucket";
  public static final String CONTENT_TYPE = "contentType";
  public static final String FILE_NAME = "fileName";
  public static final String GOOGLE_SERVICE_ACCOUNT = "googleServiceAccount";
  public static final String GS_URI = "gsUri";
  public static final String HASHES = "hashes";
  public static final String LOCALIZATION_PATH = "localizationPath";
  public static final String NAME = "name";
  public static final String SIZE = "size";
  public static final String TIME_CREATED = "timeCreated";
  public static final String TIME_UPDATED = "timeUpdated";

  public static final List<String> CORE_FIELDS =
      List.of(
          BUCKET,
          CONTENT_TYPE,
          FILE_NAME,
          GS_URI,
          HASHES,
          LOCALIZATION_PATH,
          NAME,
          SIZE,
          TIME_CREATED,
          TIME_UPDATED);

  public static final List<String> ALL_FIELDS =
      Collections.unmodifiableList(
          new ArrayList<>() {
            {
              addAll(CORE_FIELDS);
              add(ACCESS_URL);
              add(BOND_PROVIDER);
              add(GOOGLE_SERVICE_ACCOUNT);
            }
          });

  public static final List<String> DEFAULT_FIELDS =
      Collections.unmodifiableList(
          new ArrayList<>() {
            {
              addAll(CORE_FIELDS);
              add(GOOGLE_SERVICE_ACCOUNT);
            }
          });

  public static final List<String> METADATA_FIELDS =
      Collections.unmodifiableList(
          new ArrayList<>() {
            {
              addAll(CORE_FIELDS);
              add(ACCESS_URL);
            }
          });

  public static final List<String> BOND_SA_FIELDS = List.of(GOOGLE_SERVICE_ACCOUNT);

  public static final List<String> ACCESS_URL_FIELDS = List.of(ACCESS_URL);

  public static boolean overlap(List<String> requestedFields, List<String> serviceFields) {
    return serviceFields.stream().anyMatch(requestedFields::contains);
  }

  public static boolean shouldRequestObjectInfo(List<String> requestedFields) {
    return overlap(requestedFields, METADATA_FIELDS);
  }
}
