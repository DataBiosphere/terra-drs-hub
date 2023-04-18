package bio.terra.drshub.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public record DrsProviderService(DrsHubConfig drsHubConfig) {

  static final String COMPACT_ID_PREFIX_GROUP = "compactIdPrefix";
  static final String HOST_NAME_GROUP = "hostname";
  static final String PATH_GROUP = "path";
  static final String SCHEME_GROUP = "scheme";

  @VisibleForTesting
  static final Pattern compactIdRegex =
      Pattern.compile(
          "(?<scheme>dos|drs)://(?<compactIdPrefix>(dg|drs)\\.[0-9a-z-]+):(?<path>.*)",
          Pattern.CASE_INSENSITIVE);
  @VisibleForTesting
  static final Pattern hostNameRegex =
      Pattern.compile(
          "(?<scheme>dos|drs)://(?<hostname>[^?/:]+\\.[^?/:]+)/(?<path>.*)",
          Pattern.CASE_INSENSITIVE);

  /**
   * DRS schemes are allowed as of <a
   * href="https://ga4gh.github.io/data-repository-service-schemas/preview/release/drs-1.2.0/docs/">DRS
   * 1.2</a>
   *
   * <p>DOS is still supported as a URI scheme in case there are URIs in that form in the wild
   * however all providers support DRS 1.2. So parsing the URI treats DOS and DRS interchangeably
   * but the resolution is all DRS 1.2
   *
   * <p>Note: GA4GH Compact Identifier based URIs are incompatible with W3C/IETF URIs and the
   * various standard libraries that parse them because they use colons as a delimiter. However,
   * there are some Compact Identifier based URIs that use slashes as a delimiter. This code assumes
   * that if the host part of the URI is of the form dg.[0-9a-z-]+ or drs.[0-9a-z-]+ then it is a
   * Compact Identifier.
   *
   * <p>Hostname ID: drs://hostname/id
   *
   * <p>https://drs.example.org/ga4gh/drs/v1/objects/314159
   *
   * <p>Compact ID: drs://prefix:accession
   *
   * <p>drs://dg.anv0:f51fc329-b09e-4e16-b1a9-2f60ebc428ab
   *
   * <p>If you update *any* of the below be sure to link to the supporting docs and update the
   * comments above!
   */
  public UriComponents getUriComponents(String drsUri) {
    UriComponents uriComponents;

    var compactIdMatch = compactIdRegex.matcher(drsUri);
    var hostNameMatch = hostNameRegex.matcher(drsUri);

    if (compactIdMatch.find(0)) {
      uriComponents = getCompactIdUriComponents(compactIdMatch);
    } else if (hostNameMatch.find(0)) {
      uriComponents = getHostnameUriComponents(hostNameMatch);
    } else {
      throw new BadRequestException(String.format("[%s] is not a valid DRS URI.", drsUri));
    }

    // Validate url
    if (uriComponents.getScheme() == null) {
      throw new BadRequestException("DRSHub does not support DRS URIs without a scheme");
    }

    if (uriComponents.getQuery() != null) {
      throw new BadRequestException("DRSHub does not support query params in DRS URIs");
    }

    log.info("built URI: " + uriComponents);

    return uriComponents;
  }

  // TODO ID-565: If ID is compact we need to url encode any slashes
  private UriComponents getCompactIdUriComponents(Matcher compactIdMatch) {

    var matchedPrefixGroup = compactIdMatch.group(COMPACT_ID_PREFIX_GROUP);
    var host = Optional.ofNullable(drsHubConfig.getCompactIdHosts().get(matchedPrefixGroup));
    if (host.isEmpty()) {
      throw new BadRequestException(
          String.format(
              "Could not find matching host for compact id [%s].",
              compactIdMatch.group(COMPACT_ID_PREFIX_GROUP)));
    }

    var hostString = host.get();
    var strippedPath = compactIdMatch.group(PATH_GROUP);
    log.info(String.format("Matched a compact ID and stripped path: %s", strippedPath));
    return UriComponentsBuilder.newInstance()
        .scheme(compactIdMatch.group(SCHEME_GROUP))
        .host(hostString)
        .path(strippedPath)
        .build();
  }

  private UriComponents getHostnameUriComponents(Matcher hostNameMatch) {

    var hostString = hostNameMatch.group(HOST_NAME_GROUP);
    var strippedPath = hostNameMatch.group(PATH_GROUP);
    log.info(String.format("Matched a hostname ID and stripped path: %s", strippedPath));
    return UriComponentsBuilder.newInstance()
        .scheme(hostNameMatch.group(SCHEME_GROUP))
        .host(hostString)
        .path(strippedPath)
        .build();
  }

  public DrsProvider determineDrsProvider(UriComponents uriComponents) {
    var host = uriComponents.getHost();
    assert host != null;

    if (host.endsWith("dataguids.org")) {
      throw new BadRequestException(
          "dataguids.org data has moved. See: https://support.terra.bio/hc/en-us/articles/360060681132");
    }

    var providers = drsHubConfig.getDrsProviders();

    return providers.values().stream()
        .filter(p -> host.matches(p.getHostRegex()))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException(
                    String.format(
                        "Could not determine DRS provider for id `%s`",
                        uriComponents.toUriString())));
  }
}
