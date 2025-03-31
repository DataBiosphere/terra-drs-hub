package bio.terra.drshub.models;

public enum ECMFenceProviderEnum {
  dcf_fence("dcf-fence"),
  fence("fence"),
  anvil("anvil"),
  kids_first("kids-first"),
  sage("sage");

  private String uriValue;

  ECMFenceProviderEnum(String uriValue) {
    this.uriValue = uriValue;
  }

  public String getUriValue() {
    return uriValue;
  }
}
