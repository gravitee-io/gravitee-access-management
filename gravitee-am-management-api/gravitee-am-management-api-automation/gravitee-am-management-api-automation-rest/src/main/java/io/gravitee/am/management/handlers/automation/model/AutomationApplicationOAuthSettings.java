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
package io.gravitee.am.management.handlers.automation.model;

import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;
import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Automation API view of {@link io.gravitee.am.model.application.ApplicationOAuthSettings}: the OAuth 2.0 / OIDC
 * settings the Management API lets an application change, minus the client secret and the dynamic client
 * registration runtime fields. Fields described as computed are filled in by AM when omitted.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationApplicationOAuthSettings", title = "Application OAuth 2.0 / OIDC settings",
        description = "OAuth 2.0 and OpenID Connect settings of the application. Fields described as computed are " +
                "filled in by AM when omitted. In crossAppAccessSettings, trustDomainId and resourceServerId are " +
                "references by id:<uuid> only. tokenExchangeOAuthSettings, when omitted, is inherited from the domain.")
public class AutomationApplicationOAuthSettings {

    @Schema(description = "OAuth 2.0 grant types the application may use. Computed when omitted, from the application " +
            "type: web authorization_code and password; browser and native authorization_code; service " +
            "client_credentials; resource_server adds client_credentials; agent per kind (user_embedded " +
            "authorization_code; hosted_delegated authorization_code, client_credentials and token exchange; " +
            "autonomous client_credentials and token exchange).", example = "[\"authorization_code\", \"refresh_token\"]")
    private List<String> grantTypes;

    @Schema(description = "OAuth 2.0 / OIDC response types the application may use. Computed from grantTypes when omitted: " +
            "authorization_code adds code, implicit adds token and id_token.", example = "[\"code\"]")
    private List<String> responseTypes;

    @Schema(description = "Redirection URIs. The redirect_uri of an authorization request must match one of them. At least " +
            "one is required for the authorization_code and implicit grants.", example = "[\"https://app.example.com/callback\"]")
    private List<String> redirectUris;

    @Schema(description = "URLs the user may be redirected to after logout (post_logout_redirect_uri).", example = "[\"https://app.example.com/logged-out\"]")
    private List<String> postLogoutRedirectUris;

    @Schema(description = "request_uri values pre-registered by the application.")
    private List<String> requestUris;

    @Schema(description = "URI, using the https scheme, a third party can use to initiate a login by the application.", example = "https://app.example.com/login")
    private String initiateLoginUri;

    @Schema(description = "Scopes the application can request. Each scope must exist in the domain, otherwise the " +
            "request is rejected with 400. Resource server applications always get the uma_protection scope.")
    private List<AutomationScopeSettings> scopeSettings;

    @Schema(description = "Whether the permissions of the user roles are added to the scopes of the issued tokens.", defaultValue = "false")
    private boolean enhanceScopesWithUserPermissions;

    @Schema(description = "When true, users must explicitly select each scope on the consent page; when false, all " +
            "requested scopes are preselected and users can deselect them. Computed (true) when omitted.", defaultValue = "true")
    private Boolean optInScopeSelection;

    @Schema(description = "Access token validity, in seconds. Computed (7200) when omitted.", defaultValue = "7200")
    private Integer accessTokenValiditySeconds;

    @Schema(description = "Refresh token validity, in seconds. Computed (14400) when omitted.", defaultValue = "14400")
    private Integer refreshTokenValiditySeconds;

    @Schema(description = "ID token validity, in seconds. Computed (14400) when omitted.", defaultValue = "14400")
    private Integer idTokenValiditySeconds;

    @Schema(description = "Validity, in seconds, of the identity assertion authorization grants (ID-JAG) issued to the " +
            "application. Computed (300) when omitted.", defaultValue = "300")
    private Integer idJagValiditySeconds;

    @Schema(description = "Whether a refresh token stays valid when used, instead of being replaced by a new one.", defaultValue = "false")
    private boolean disableRefreshTokenRotation;

    @Schema(description = "Custom claims added to the issued tokens.")
    private List<AutomationTokenClaim> tokenCustomClaims;

    @Schema(description = "Custom claims added to the UserInfo responses.")
    private List<AutomationUserInfoClaim> userinfoCustomClaims;

    @Schema(description = "Lightweight JWT: restricts the claims of the issued tokens to an allowlist.")
    private ApplicationLightweightJwtSettings lightweightJwtSettings;

    @Schema(description = "Token exchange (RFC 8693) behavior of the application. When omitted, inherited from the domain.")
    private TokenExchangeOAuthSettings tokenExchangeOAuthSettings;

    @Schema(description = "Client authentication method at the token endpoint: client_secret_basic, client_secret_post, " +
            "client_secret_jwt, private_key_jwt, tls_client_auth, self_signed_tls_client_auth or none. " +
            "Computed when omitted: client_secret_basic on creation, none for browser and native " +
            "applications created without grantTypes, per kind for agents (user_embedded none; " +
            "hosted_delegated and autonomous private_key_jwt).", example = "client_secret_basic")
    private String tokenEndpointAuthMethod;

    @Schema(description = "JWS algorithm the application signs its authentication JWT with, for the private_key_jwt and " +
            "client_secret_jwt methods.", example = "RS256")
    private String tokenEndpointAuthSigningAlg;

    @Schema(description = "URL of the application JSON Web Key Set, holding the keys used to verify its signatures. " +
            "Mutually exclusive with jwks.", example = "https://app.example.com/.well-known/jwks.json")
    private String jwksUri;

    @Schema(description = "JSON Web Key Set of the application passed by value (RFC 7517 section 5): an object " +
            "with a keys array of JWKs. Same use as jwksUri, which it excludes.",
            example = "{\"keys\": [{\"kty\": \"RSA\", \"use\": \"sig\", \"kid\": \"app-key\", \"n\": \"0vx7...\", \"e\": \"AQAB\"}]}")
    private Map<String, Object> jwks;

    @Schema(description = "Expected subject distinguished name of the client certificate, for the tls_client_auth method.", example = "CN=app.example.com")
    private String tlsClientAuthSubjectDn;

    @Schema(description = "Expected dNSName subject alternative name of the client certificate, for the tls_client_auth " +
            "method.")
    private String tlsClientAuthSanDns;

    @Schema(description = "Expected uniformResourceIdentifier subject alternative name of the client certificate, for the " +
            "tls_client_auth method.")
    private String tlsClientAuthSanUri;

    @Schema(description = "Expected iPAddress subject alternative name of the client certificate, for the tls_client_auth " +
            "method.")
    private String tlsClientAuthSanIp;

    @Schema(description = "Expected rfc822Name subject alternative name of the client certificate, for the tls_client_auth " +
            "method.")
    private String tlsClientAuthSanEmail;

    @Schema(description = "Whether access tokens are bound to the client certificate (RFC 8705).", defaultValue = "false")
    private boolean tlsClientCertificateBoundAccessTokens;

    @Schema(description = "Whether access tokens must be bound to a DPoP proof (RFC 9449).", defaultValue = "false")
    private boolean dpopBoundAccessTokens;

    @Schema(description = "Whether the authorization code flow requires PKCE. Computed (true) for browser and native " +
            "applications created without grantTypes, and for user_embedded agents.", defaultValue = "false")
    private boolean forcePKCE;

    @Schema(description = "Whether PKCE requires the S256 code challenge method. Computed (true) for browser applications " +
            "created without grantTypes, and for user_embedded agents.", defaultValue = "false")
    private boolean forceS256CodeChallengeMethod;

    @Schema(description = "Whether logout also calls the end_session_endpoint of the external OIDC provider the user " +
            "authenticated with.", defaultValue = "false")
    private boolean singleSignOut;

    @Schema(description = "Whether the id_token_hint parameter can be used to re-authenticate the user silently.", defaultValue = "false")
    private boolean silentReAuthentication;

    @Schema(description = "Whether the auth_time claim is required in the ID token.", defaultValue = "false")
    private boolean requireAuthTime;

    @Schema(description = "Default maximum authentication age, in seconds.", example = "3600")
    private Integer defaultMaxAge;

    @Schema(description = "Default requested Authentication Context Class Reference values.")
    private List<String> defaultACRvalues;

    @Schema(description = "JWS algorithm signing the ID tokens issued to the application.")
    private String idTokenSignedResponseAlg;

    @Schema(description = "JWE alg algorithm encrypting the ID tokens issued to the application.")
    private String idTokenEncryptedResponseAlg;

    @Schema(description = "JWE enc algorithm encrypting the ID tokens issued to the application.")
    private String idTokenEncryptedResponseEnc;

    @Schema(description = "JWS algorithm signing the UserInfo responses.")
    private String userinfoSignedResponseAlg;

    @Schema(description = "JWE alg algorithm encrypting the UserInfo responses.")
    private String userinfoEncryptedResponseAlg;

    @Schema(description = "JWE enc algorithm encrypting the UserInfo responses.")
    private String userinfoEncryptedResponseEnc;

    @Schema(description = "JWS algorithm the application signs its request objects with.")
    private String requestObjectSigningAlg;

    @Schema(description = "JWE alg algorithm the application may encrypt its request objects with.")
    private String requestObjectEncryptionAlg;

    @Schema(description = "JWE enc algorithm the application may encrypt its request objects with.")
    private String requestObjectEncryptionEnc;

    @Schema(description = "JWS algorithm signing the authorization responses (JARM).")
    private String authorizationSignedResponseAlg;

    @Schema(description = "JWE alg algorithm encrypting the authorization responses (JARM).")
    private String authorizationEncryptedResponseAlg;

    @Schema(description = "JWE enc algorithm encrypting the authorization responses (JARM).")
    private String authorizationEncryptedResponseEnc;

    @Schema(description = "Subject identifier type of the responses: public or pairwise.", example = "public")
    private String subjectType;

    @Schema(description = "URL, using the https scheme, used to compute pairwise subject identifiers.")
    private String sectorIdentifierUri;

    @Schema(description = "Email addresses of the people responsible for the application.", example = "[\"ops@example.com\"]")
    private List<String> contacts;

    @Schema(description = "URL of the application logo, displayed to users.")
    private String logoUri;

    @Schema(description = "URL of the application home page.")
    private String clientUri;

    @Schema(description = "URL of the application policy on the use of profile data.")
    private String policyUri;

    @Schema(description = "URL of the application terms of service.")
    private String tosUri;

    @Schema(description = "Cross App Access: resource servers this application may request an ID-JAG for. trustDomainId " +
            "and resourceServerId are references by id:<uuid> only (no Automation API endpoint).")
    private ApplicationCrossAppAccessSettings crossAppAccessSettings;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Name of the client presented to users. Always the application name. Read-only.")
    private String clientName;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "OAuth 2.0 client type, computed from the application type: confidential for web and service " +
            "applications, public for browser and native applications and user_embedded agents. Read-only.")
    private String clientType;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "OIDC application type, computed from the " +
            "application type: native for native applications, web for web, browser and service applications. " +
            "Read-only.")
    private String applicationType;
}
