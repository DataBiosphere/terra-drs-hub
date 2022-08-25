package bio.terra.drshub.models;

import io.github.ga4gh.drs.model.Authorizations;

public enum AccessUrlAuthEnum {
  fence_token(Authorizations.SupportedTypesEnum.BEARERAUTH),
  current_request(Authorizations.SupportedTypesEnum.BASICAUTH),
  passport(Authorizations.SupportedTypesEnum.PASSPORTAUTH);

  private Authorizations.SupportedTypesEnum drsAuthType;

  AccessUrlAuthEnum(Authorizations.SupportedTypesEnum drsAuthType) {
    this.drsAuthType = drsAuthType;
  }

  public static AccessUrlAuthEnum fromDrsAuthType(Authorizations.SupportedTypesEnum drsAuthType) {
    return switch (drsAuthType) {
      case NONE -> null;
      case BASICAUTH -> current_request;
      case BEARERAUTH -> fence_token;
      case PASSPORTAUTH -> passport;
    };
  }
}
