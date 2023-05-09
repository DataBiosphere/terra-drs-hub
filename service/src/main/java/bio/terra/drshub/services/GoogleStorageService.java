package bio.terra.drshub.services;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.drshub.DrsHubException;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class GoogleStorageService {

  public Storage getAuthedStorage(SaKeyObject saKey, String googleProject) {
    final ServiceAccountCredentials creds;
    try (var saKeyInputStream =
        new ByteArrayInputStream(saKey.getData().toString().getBytes(StandardCharsets.UTF_8))) {
      creds = ServiceAccountCredentials.fromStream(saKeyInputStream);
    } catch (IOException ex) {
      throw new DrsHubException("Could not parse credentials from Bond");
    }
    return StorageOptions.newBuilder()
        .setProjectId(googleProject)
        .setCredentials(creds)
        .build()
        .getService();
  }
}
