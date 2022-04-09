package bio.terra.drshub.models;

public enum BondProviderEnum {
  dcf_fence("dcf-fence"),
  fence("fence"),
  anvil("anvil"),
  kids_first("kids-first");

  private String uriValue;

  BondProviderEnum(String uriValue) {
    this.uriValue = uriValue;
  }

  public String getUriValue() {
    return uriValue;
  }
}
