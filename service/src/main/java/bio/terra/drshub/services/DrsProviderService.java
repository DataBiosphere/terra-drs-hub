package bio.terra.drshub.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public record DrsProviderService(DrsHubConfig drsHubConfig) {

  static final String COMPACT_ID_PREFIX_GROUP = "compactIdPrefix";

  @VisibleForTesting
  static final Pattern compactIdRegex =
      Pattern.compile(
          "(?:dos|drs)://(?<compactIdPrefix>(dg|drs)\\.[0-9a-z-]+):.*", Pattern.CASE_INSENSITIVE);

  static final String HOST_NAME_GROUP = "hostname";
  @VisibleForTesting
  static final Pattern hostNameRegex =
      Pattern.compile("(?:dos|drs)://(?<hostname>[^?/:]+\\.[^?/:]+)/.*", Pattern.CASE_INSENSITIVE);

  static final String SCHEME_GROUP = "scheme";
  @VisibleForTesting
  static final Pattern schemeRegex =
      Pattern.compile("(?<scheme>dos|drs)://", Pattern.CASE_INSENSITIVE);

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

    var compactIdMatch = compactIdRegex.matcher(drsUri);
    final String hostString;
    final String strippedPath;
    var hostNameMatch = hostNameRegex.matcher(drsUri);
    var schemeMatch = schemeRegex.matcher(drsUri);
    var matchedScheme = schemeMatch.group(SCHEME_GROUP);

    if (!schemeMatch.find(0)) {
      throw new BadRequestException("DRSHub does not support DRS URIs without a scheme");
    }

    var drsUriWithoutScheme = schemeMatch.replaceFirst("");

    // TODO ID-565: If ID is compact we need to url encode any slashes
    if (compactIdMatch.find(0)) {
      // This is a compact id with a colon separator
      var matchedPrefixGroup = compactIdMatch.group(COMPACT_ID_PREFIX_GROUP);
      var host = Optional.ofNullable(drsHubConfig.getCompactIdHosts().get(matchedPrefixGroup));
      if (host.isPresent()) {
        hostString = host.get();
        strippedPath = drsUriWithoutScheme.replace(matchedPrefixGroup + ":", "");
        log.info(String.format("Matched a compact ID and stripped path: %s", strippedPath));
      } else {
        throw new BadRequestException(
            String.format(
                "Could not find matching host for compact id [%s].",
                compactIdMatch.group(COMPACT_ID_PREFIX_GROUP)));
      }
    } else if (hostNameMatch.find(0)) {
      // This is a hostname with a slash separator
      hostString = hostNameMatch.group(HOST_NAME_GROUP);
      strippedPath = drsUriWithoutScheme.replace(hostString + "/", "");
      log.info(String.format("Matched a hostname ID and stripped path: %s", strippedPath));
    } else {
      throw new BadRequestException(String.format("[%s] is not a valid DRS URI.", drsUri));
    }

    URI strippedUri = URI.create(strippedPath);
    if (strippedUri.getQuery() != null) {
      throw new BadRequestException("DRSHub does not support query params in DRS URIs");
    }

    var builtUri =
        UriComponentsBuilder.newInstance()
            .scheme(matchedScheme)
            .host(hostString)
            .path(strippedUri.getPath())
            .build();
    log.info("built URI: " + builtUri);

    return builtUri;
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
