package bio.terra.drshub.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.drshub.config.DrsHubConfig;
import bio.terra.drshub.config.DrsProvider;
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

  private static final Pattern compactIdRegex =
      Pattern.compile("(?:dos|drs)://(?<compactId>dg\\.[0-9a-z-]+).*", Pattern.CASE_INSENSITIVE);

  private static final Pattern hostNameRegex =
      Pattern.compile("(?:dos|drs)://(?<hostname>[^?/]+\\.[^?/]+).*", Pattern.CASE_INSENSITIVE);

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
   * that if the host part of the URI is of the form dg.[0-9a-z-]+ then it is a Compact Identifier.
   *
   * <p>If you update *any* of the below be sure to link to the supporting docs and update the
   * comments above!
   */
  public UriComponents getUriComponents(String drsUri) {

    var compactIdMatch = compactIdRegex.matcher(drsUri);
    final String dnsHost;
    final String strippedPath;
    var hostNameMatch = hostNameRegex.matcher(drsUri);

    if (compactIdMatch.matches()) {
      var matchedGroup = compactIdMatch.group("compactId");
      var host = Optional.ofNullable(drsHubConfig.getCompactIdHosts().get(matchedGroup));
      if (host.isPresent()) {
        dnsHost = host.get();
        strippedPath = drsUri.replaceAll("(?:dos|drs)://", "").replace(matchedGroup + "/", "");
      } else {
        throw new BadRequestException(
            String.format(
                "Could not find matching host for compact id [%s].",
                compactIdMatch.group("compactId")));
      }
    } else if (hostNameMatch.matches()) {
      dnsHost = hostNameMatch.group("hostname");
      strippedPath = drsUri.replaceAll("(?:dos|drs)://", "").replace(dnsHost + "/", "");
    } else {
      throw new BadRequestException(String.format("[%s] is not a valid DRS URI.", drsUri));
    }

    URI strippedUri = URI.create(strippedPath);
    if (strippedUri.getQuery() != null) {
      throw new BadRequestException("DRSHub does not support query params in DRS URIs");
    }

    return UriComponentsBuilder.newInstance()
        .host(dnsHost)
        .path(URI.create(strippedPath).getPath())
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
