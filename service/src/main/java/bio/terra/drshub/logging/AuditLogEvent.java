package bio.terra.drshub.logging;

import bio.terra.drshub.generated.model.ServiceName;
import bio.terra.drshub.models.AccessUrlAuthEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableAuditLogEvent.class)
public interface AuditLogEvent extends WithAuditLogEvent {

  @JsonInclude(Include.NON_EMPTY)
  Optional<String> getClientIP();

  @JsonInclude(Include.NON_EMPTY)
  Optional<String> getProviderName();

  @JsonInclude(Include.NON_EMPTY)
  Optional<String> getDRSUrl();

  @JsonInclude(Include.NON_EMPTY)
  Optional<AccessUrlAuthEnum> getAuthType();

  AuditLogEventType getAuditLogEventType();

  @JsonInclude(Include.NON_EMPTY)
  Optional<ServiceName> getServiceName();

  class Builder extends ImmutableAuditLogEvent.Builder {}
}
