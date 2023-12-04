package bio.terra.drshub.services;

import bio.terra.bond.model.SaKeyObject;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.DrsHubException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import bio.terra.drshub.generated.model.RequestObject.CloudPlatformEnum;
import bio.terra.drshub.logging.AuditLogEvent;
import bio.terra.drshub.logging.AuditLogEventType;
import bio.terra.drshub.logging.AuditLogger;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import bio.terra.drshub.models.Fields;
import bio.terra.drshub.util.AsyncUtils;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.github.ga4gh.drs.model.AccessMethod;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
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
    DrsResolutionService drsResolutionService,
    AuditLogger auditLogger,
    AsyncUtils asyncUtils) {

  public URL getSignedUrl(
      String bucket,
      String objectName,
      String dataObjectUri,
      String googleProject,
      BearerToken bearerToken,
      String ip) {

    var components = drsProviderService.getUriComponents(dataObjectUri);
    var drsProvider = drsProviderService.determineDrsProvider(components);

    var logEvent =
        new AuditLogEvent.Builder()
            .dRSUrl(dataObjectUri)
            .providerName(drsProvider.getName())
            .auditLogEventType(AuditLogEventType.GetSignedUrl)
            .clientIP(Optional.ofNullable(ip))
            .build();
    auditLogger.logEvent(logEvent);

    if (drsProvider.getAccessMethodByType(AccessMethod.TypeEnum.GS).getAuth()
        == AccessUrlAuthEnum.current_request) {
      return getSignedUrlFromSam(
          bearerToken, String.format("gs://%s/%s", bucket, objectName), googleProject);
    } else {
      return getSignedUrlFromDrsProvider(
          bearerToken, drsProvider, googleProject, bucket, objectName, dataObjectUri, ip);
    }
  }

  private URL getSignedUrlFromSam(
      BearerToken bearerToken, String gsPath, String requesterPaysProject) {
    try {
      return new URL(authService.getSignedUrlForBlob(bearerToken, gsPath, requesterPaysProject));
    } catch (MalformedURLException ex) {
      throw new DrsHubException("Could not parse signed URL from Sam", ex);
    }
  }

  private URL getSignedUrlFromDrsProvider(
      BearerToken bearerToken,
      DrsProvider drsProvider,
      String googleProject,
      String bucket,
      String objectName,
      String dataObjectUri,
      String ip) {
    SaKeyObject saKey = authService.fetchUserServiceAccount(drsProvider, bearerToken);
    Storage storage = googleStorageService.getAuthedStorage(saKey, googleProject);

    final BlobInfo blobInfo;
    if (bucket == null || objectName == null) {
      blobInfo =
          BlobInfo.newBuilder(getBlobIdFromDrsUri(dataObjectUri, bearerToken, ip, googleProject))
              .build();
    } else {
      blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).build();
    }

    var duration = drsHubConfig.getSignedUrlDuration();

    return storage.signUrl(
        blobInfo, duration.toMinutes(), TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
  }

  private BlobId getBlobIdFromDrsUri(
      String dataObjectUri, BearerToken bearerToken, String ip, String googleProject) {
    var objectFuture =
        drsResolutionService.resolveDrsObject(
            dataObjectUri,
            CloudPlatformEnum.GS,
            Fields.CORE_FIELDS,
            bearerToken,
            true,
            ip,
            googleProject);
    return asyncUtils.runAndCatch(objectFuture, result -> BlobId.fromGsUtilUri(result.getGsUri()));
  }
}
