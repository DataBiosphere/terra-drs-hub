package bio.terra.drshub.services;

import bio.terra.bard.api.BardApi;
import bio.terra.bard.model.Event;
import bio.terra.bard.model.EventProperties;
import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.config.DrsHubConfig;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TrackingService {
  static final String APP_ID = "drshub";

  private final BardApiFactory bardApiFactory;
  private final boolean trackInMixpanel;

  private final Map<String, String> bearerTokenCache =
      Collections.synchronizedMap(new PassiveExpiringMap<>(15, TimeUnit.MINUTES));

  public TrackingService(BardApiFactory bardApiFactory, DrsHubConfig config) {
    this.bardApiFactory = bardApiFactory;
    this.trackInMixpanel = config.trackInMixPanel();
  }

  @Async("asyncExecutor")
  public void logEvent(BearerToken bearerToken, String eventName, Map<String, ?> properties) {
    var bardApi = bardApiFactory.getApi(bearerToken);
    // Sync the user profile if needed.
    syncUser(bardApi, bearerToken);
    // Log the user event
    logEvent(bardApi, eventName, properties);
  }

  /**
   * Call the syncProfile endpoint in Bard. This ensures that the Terra user is properly associated
   * with a MixPanel user. Note that this is if mixpanel tracking is enabled.
   */
  private void syncUser(BardApi bardApi, BearerToken bearerToken) {
    String key = bearerToken.getToken();
    bearerTokenCache.computeIfAbsent(key, k -> syncProfile(bardApi) ? "" : null);
  }

  /**
   * Syncs profile info from orchestration to mixpanel to improve querying/reporting capabilities in
   * the mixpanel reports.
   *
   * @return boolean - if the sync request was successful.
   */
  private boolean syncProfile(BardApi bardApi) {
    try {
      bardApi.syncProfile();
      return true;
    } catch (Exception ex) {
      log.warn("Error syncing user profile in bard", ex);
      return false;
    }
  }

  private void logEvent(BardApi bardApi, String eventName, Map<String, ?> properties) {
    EventProperties eventProperties = new EventProperties();
    eventProperties.putAll(properties);
    eventProperties.appId(APP_ID).pushToMixpanel(trackInMixpanel);

    Event event = new Event().event(eventName).properties(eventProperties);

    try {
      bardApi.event(event);
    } catch (Exception ex) {
      log.warn("Error sending event to bard", ex);
    }
  }

  @VisibleForTesting
  void clearCache() {
    bearerTokenCache.clear();
  }
}
