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
package io.gravitee.am.repository.jdbc.management.api.model;

import io.gravitee.am.common.oidc.ApplicationType;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.JWKSet;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("clients")
public class JdbcClient {
    @Id
    private String id;

    private String clientId;

    private String clientSecret;

    private List<String> redirectUris;

    private List<String> authorizedGrantTypes;

    private List<String> responseTypes;

    //Default value must be web.
    private String applicationType;

    private List<String> contacts;

    private String clientName;

    private String logoUri;

    private String clientUri;

    private String policyUri;

    private String tosUri;

    private String jwksUri;

    private JWKSet jwks;

    private String sectorIdentifierUri;

    private String subjectType;

    private String idTokenSignedResponseAlg;

    private String idTokenEncryptedResponseAlg;

    private String idTokenEncryptedResponseEnc;

    private String userinfoSignedResponseAlg;

    private String userinfoEncryptedResponseAlg;

    private String userinfoEncryptedResponseEnc;

    private String requestObjectSigningAlg;

    private String requestObjectEncryptionAlg;

    private String requestObjectEncryptionEnc;

    private String tokenEndpointAuthMethod;

    private String tokenEndpointAuthSigningAlg;

    private Integer defaultMaxAge;

    private Boolean requireAuthTime;

    private List<String> defaultACRvalues;

    private String initiateLoginUri;

    private List<String> requestUris;

    private List<String> scopes;

    private String softwareId;

    private String softwareVersion;

    private String softwareStatement;

    private String registrationAccessToken;

    private String registrationClientUri;

    private Date clientIdIssuedAt;

    private Date clientSecretExpiresAt;

    private List<String> autoApproveScopes;

    private int accessTokenValiditySeconds;

    private int refreshTokenValiditySeconds;

    private int idTokenValiditySeconds;

    private String tlsClientAuthSubjectDn;

    private String tlsClientAuthSanDns;

    private String tlsClientAuthSanUri;

    private String tlsClientAuthSanIp;

    private String tlsClientAuthSanEmail;

    private String authorizationSignedResponseAlg;

    private String authorizationEncryptedResponseAlg;

    private String authorizationEncryptedResponseEnc;

    /**
     * Security domain associated to the client
     */
    private String domain;

    /**
     * Client enabled.
     */
    private boolean enabled;

    /**
     * The Client creation date
     */
    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * The Client last updated date
     */
    @Column("updated_at")
    private LocalDateTime updatedAt;

    private Set<String> identities;

    private String certificate;

    private Set<String> factors;

    private boolean enhanceScopesWithUserPermissions;

    private Map<String, Integer> scopeApprovals;

    private AccountSettings accountSettings;

    private LoginSettings loginSettings;

    private List<TokenClaim> tokenCustomClaims;

    private boolean template;

    private Map<String, Object> metadata;
}
