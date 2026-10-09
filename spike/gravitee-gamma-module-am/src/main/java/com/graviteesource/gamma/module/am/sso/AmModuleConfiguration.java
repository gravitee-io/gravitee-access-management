/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graviteesource.gamma.module.am.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.apim.plugin.gamma.api.identity.AmConnectionRepository;
import io.gravitee.apim.plugin.gamma.api.identity.ApimAmConnectionRepository;
import io.gravitee.rest.api.service.AmConnectionService;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmModuleConfiguration {

  // Named so it does not clash with the other gamma modules' AmConnectionRepository beans in the parent context.
  @Bean
  public AmConnectionRepository amModuleAmConnectionRepository(
    AmConnectionService amConnectionService
  ) {
    return new ApimAmConnectionRepository(amConnectionService);
  }

  @Bean
  public AmSsoService amSsoService(
    @Qualifier(
      "amModuleAmConnectionRepository"
    ) AmConnectionRepository amConnectionRepository,
    @Value("${modules.am.console-url:http://localhost:4201}") String consoleUrl,
    @Value(
      "${modules.am.sso.issuer-url:http://localhost:8085/_control/sso-token}"
    ) String issuerUrl
  ) {
    // HTTP/1.1: the default h2c upgrade makes a plain Node server drop the connection.
    HttpClient httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(5))
      .build();
    return new AmSsoService(
      amConnectionRepository,
      httpClient,
      new ObjectMapper(),
      consoleUrl,
      issuerUrl
    );
  }
}
