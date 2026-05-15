/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.model;

import java.time.Duration;
import java.util.List;

/**
 * Parsed projection of a fetched CIMD metadata document, surfaced to the UI as a preview before
 * confirming application creation. The {@code metadataJson} carries the raw response body so the
 * subsequent create call can persist and re-parse it (e.g. for inline {@code jwks}) without
 * re-fetching.
 */
public record CimdClientMetadata(
        String url,
        String clientId,
        String clientName,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        List<String> scopes,
        List<String> grantTypes,
        List<String> responseTypes,
        List<String> contacts,
        List<String> requestUris,
        String tokenEndpointAuthMethod,
        String applicationType,
        String subjectType,
        String sectorIdentifierUri,
        String idTokenSignedResponseAlg,
        String logoUri,
        String clientUri,
        String policyUri,
        String tosUri,
        String jwksUri,
        Boolean hasInlineJwks,
        String softwareId,
        String softwareVersion,
        String softwareStatement,
        String tlsClientAuthSubjectDn,
        String tlsClientAuthSanDns,
        String tlsClientAuthSanUri,
        String tlsClientAuthSanIp,
        String tlsClientAuthSanEmail,
        Boolean tlsClientCertificateBoundAccessTokens,
        String backchannelTokenDeliveryMode,
        String backchannelClientNotificationEndpoint,
        String backchannelAuthRequestSignAlg,
        Boolean backchannelUserCodeParameter,
        String requestObjectSigningAlg,
        Missing missing,
        String metadataJson,
        Duration ttl
) {
    public record Missing(boolean clientId, boolean clientName) {}
}
