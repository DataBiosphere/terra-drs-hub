package bio.terra.drshub.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

@Tag("Unit")
class DrsApiClientFactoryTest {

  private DrsApiClientFactory drsApiClientFactory;

  @BeforeEach
  void setup() {
    drsApiClientFactory = new DrsApiClientFactory();
  }

  @Test
  void testCreateClientWithCommonRestTemplate() {
    var restTemplate = new RestTemplate();
    var apiClient1 = drsApiClientFactory.createClient(restTemplate);
    var apiClient2 = drsApiClientFactory.createClient(restTemplate);

    assertThat("Factory creates new ApiClients", apiClient1, not(apiClient2));
  }

  @Test
  void testCreateClientsWithDistinctRestTemplates() {
    var restTemplate1 = new RestTemplate();
    var restTemplate2 = new RestTemplate();

    var apiClient1 = drsApiClientFactory.createClient(restTemplate1);
    var apiClient2 = drsApiClientFactory.createClient(restTemplate2);

    assertThat("RestTemplates are distinct", restTemplate1, not(restTemplate2));
    assertThat("Factory creates new ApiClients", apiClient1, not(apiClient2));
  }
}
