package bio.terra.drshub.services;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public record SignedUrlService(
    DrsHubConfig drsHubConfig,
    AuthService authService,
    DrsProviderService drsProviderService,
    GoogleStorageService googleStorageService,
    AuditLogger auditLogger) {

  public URL getSignedUrl(
      String bucket,
      String objectName,
      String dataObjectUri,
      String googleProject,
      BearerToken bearerToken,
      String ip) {

    var components = drsProviderService.getUriComponents(dataObjectUri);
    var drsProvider = drsProviderService.determineDrsProvider(components);
    SaKeyObject saKey = authService.fetchUserServiceAccount(drsProvider, bearerToken);
    Storage storage = googleStorageService.getAuthedStorage(saKey, googleProject);
    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).build();
    var duration = drsHubConfig.getSignedUrlDuration();

    var logEvent =
        new AuditLogEvent.Builder()
            .dRSUrl(dataObjectUri)
            .providerName(drsProvider.getName())
            .auditLogEventType(AuditLogEventType.GetSignedUrl)
            .clientIP(ip)
            .build();
    auditLogger.logEvent(logEvent);

    return storage.signUrl(
        blobInfo, duration.toMinutes(), TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
  }
}
