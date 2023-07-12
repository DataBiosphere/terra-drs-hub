package bio.terra.drshub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.common.iam.BearerToken;
import bio.terra.drshub.BaseTest;
import bio.terra.sam.client.auth.OAuth;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("Unit")
public class SamApiFactoryTest extends BaseTest {

  @Autowired private SamApiFactory samApiFactory;

  @Test
  void testApiFactory() {
    var token = "12345";
    var api = samApiFactory.getApi(new BearerToken(token));
    OAuth auth = (OAuth) api.getApiClient().getAuthentication("authorization");
    assertEquals(token, auth.getAccessToken());
  }
}
