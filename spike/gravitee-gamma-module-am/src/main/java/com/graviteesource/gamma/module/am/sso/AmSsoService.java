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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.apim.plugin.gamma.api.identity.AmConnection;
import io.gravitee.apim.plugin.gamma.api.identity.AmConnectionRepository;
import io.gravitee.apim.plugin.gamma.api.identity.AmNotConfiguredException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the AM console login URL: {@code <consoleUrl>/management/auth/cockpit?token=<sso token>}.
 *
 * <p>Spike: the token comes from a Cockpit-compatible issuer over HTTP (cockpit-mock locally). In
 * production that is the Cloud backend, which holds the key AM trusts under alias {@code cockpit-client}.
 */
public class AmSsoService {

  private final AmConnectionRepository amConnectionRepository;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String consoleUrl;
  private final URI issuerUrl;

  public AmSsoService(
    AmConnectionRepository amConnectionRepository,
    HttpClient httpClient,
    ObjectMapper objectMapper,
    String consoleUrl,
    String issuerUrl
  ) {
    this.amConnectionRepository = amConnectionRepository;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.consoleUrl = consoleUrl.replaceAll("/+$", "");
    this.issuerUrl = URI.create(issuerUrl);
  }

  public String consoleLoginUrl(String orgId, String userId) {
    AmConnection connection = amConnectionRepository
      .findByOrg(orgId)
      .filter(c -> c.amOrganizationId() != null)
      .orElseThrow(AmNotConfiguredException::new);
    String token = mintToken(userId, connection);
    return (
      consoleUrl +
      "/management/auth/cockpit?token=" +
      URLEncoder.encode(token, StandardCharsets.UTF_8)
    );
  }

  private String mintToken(String userId, AmConnection connection) {
    Map<String, String> claims = new LinkedHashMap<>();
    claims.put("sub", userId);
    claims.put("org", connection.amOrganizationId());
    if (connection.environmentId() != null) {
      claims.put("env", connection.environmentId());
    }
    claims.put("redirectUri", consoleUrl);
    try {
      HttpRequest request = HttpRequest.newBuilder(issuerUrl)
        .header("Content-Type", "application/json")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            objectMapper.writeValueAsString(claims)
          )
        )
        .build();
      HttpResponse<String> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
          "SSO issuer answered " + response.statusCode() + " for user " + userId
        );
      }
      JsonNode body = objectMapper.readTree(response.body());
      return body.get("token").asText();
    } catch (IOException e) {
      throw new IllegalStateException(
        "SSO issuer call failed for user " + userId,
        e
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
        "SSO issuer call interrupted for user " + userId,
        e
      );
    }
  }
}
