package bio.terra.drshub.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.services.AuthService;
import bio.terra.drshub.services.GoogleStorageService;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class TestUtils {

  public static void setupSignedUrlMocks(
      AuthService authService,
      GoogleStorageService googleStorageService,
      String googleProject,
      URL url) {

    var storage = mock(Storage.class);
    var serviceAccountKey = new SaKeyObject().data("TestServiceAccountKey");

    when(authService.fetchUserServiceAccount(any(DrsProvider.class), any(BearerToken.class)))
        .thenReturn(serviceAccountKey);
    when(googleStorageService.getAuthedStorage(eq(serviceAccountKey), eq(googleProject)))
        .thenReturn(storage);
    when(storage.signUrl(
            any(BlobInfo.class),
            any(Long.class),
            any(TimeUnit.class),
            any(Storage.SignUrlOption.class)))
        .thenReturn(url);
  }
}
