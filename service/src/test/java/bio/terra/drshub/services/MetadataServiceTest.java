package bio.terra.drshub.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.drshub.BaseTest;
import bio.terra.drshub.models.DrsApi;
import io.github.ga4gh.drs.model.Authorizations;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestClientException;

public class MetadataServiceTest extends BaseTest {

  @Autowired private MetadataService metadataService;
  @MockBean private DrsApi drsApi;

  @MockBean private DrsApiFactory drsApiFactory;

  @Test
  void testDrsOptionsEndpoint() {
    var expectedAuthorizations =
        new Authorizations()
            .supportedTypes(
                List.of(
                    Authorizations.SupportedTypesEnum.PASSPORTAUTH,
                    Authorizations.SupportedTypesEnum.BEARERAUTH));

    var cidProviderHost = getProviderHosts("passport");
    var testUri = String.format("drs://%s/12345", cidProviderHost.drsUriHost());

    var resolvedUri = metadataService.getUriComponents(testUri);

    when(drsApiFactory.getApiFromUriComponents(resolvedUri, cidProviderHost.drsProvider()))
        .thenReturn(drsApi);
    when(drsApi.optionsObject(any())).thenReturn(expectedAuthorizations);

    // Authorizations that exist should result in the Authorizations wrapped in Optional
    var authorizations =
        metadataService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertPresent(authorizations);

    // Some DRS Providers return `null` when an object isn't found, instead of a 4xx error.
    // These should be handled like the server doesn't yet support the OPTIONS endpoint
    when(drsApi.optionsObject(any())).thenReturn(null);
    authorizations =
        metadataService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertEmpty(authorizations);

    // A call to an options endpoint that contains an error should also be handled like
    // the provider doesn't yet support the OPTIONS endpoint.
    when(drsApi.optionsObject(any())).thenThrow(new RestClientException("Ruh roh"));
    authorizations =
        metadataService.fetchDrsAuthorizations(cidProviderHost.drsProvider(), resolvedUri);
    assertEmpty(authorizations);
  }
}
