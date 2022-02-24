package bio.terra.drshub.models;

import io.github.ga4gh.drs.model.AccessMethod;

public enum AccessMethodConfigTypeEnum {
  gs(AccessMethod.TypeEnum.GS),
  s3(AccessMethod.TypeEnum.S3),
  https(AccessMethod.TypeEnum.HTTPS);

  private final AccessMethod.TypeEnum returnedEquivalent;

  AccessMethodConfigTypeEnum(AccessMethod.TypeEnum returnedEquivalent) {
    this.returnedEquivalent = returnedEquivalent;
  }

  public AccessMethod.TypeEnum getReturnedEquivalent() {
    return this.returnedEquivalent;
  }
}
