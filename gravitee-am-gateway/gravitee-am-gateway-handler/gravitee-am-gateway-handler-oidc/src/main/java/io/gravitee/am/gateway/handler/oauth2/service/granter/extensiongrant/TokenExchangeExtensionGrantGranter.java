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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Specialized Extension Grant Granter for RFC 8693 Token Exchange.
 *
 * This granter extends the standard ExtensionGrantGranter to provide:
 * - Support for issued_token_type in the response (RFC 8693 Section 2.2.1)
 * - Token exchange specific audit events
 * - Actor claim (act) handling for delegation scenarios
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
@Slf4j
public class TokenExchangeExtensionGrantGranter extends ExtensionGrantGranter {

    private static final String TOKEN_EXCHANGE_METADATA = "token_exchange";
    private static final String REQUESTED_TOKEN_TYPE = "requested_token_type";
    private static final String DELEGATION_TYPE = "delegation_type";
    private static final String IMPERSONATION = "impersonation";
    private static final String ACTOR_SUBJECT = "actor_subject";

    private final AuditService auditService;

    public TokenExchangeExtensionGrantGranter(
            ExtensionGrantProvider extensionGrantProvider,
            ExtensionGrant extensionGrant,
            UserAuthenticationManager userAuthenticationManager,
            TokenService tokenService,
            TokenRequestResolver tokenRequestResolver,
            IdentityProviderManager identityProviderManager,
            UserGatewayService userService,
            RulesEngine rulesEngine,
            Domain domain,
            AuditService auditService) {
        super(extensionGrantProvider, extensionGrant, userAuthenticationManager, tokenService,
                tokenRequestResolver, identityProviderManager, userService, rulesEngine, domain);
        this.auditService = auditService;
    }

    @Override
    protected Single<Token> createAccessToken(OAuth2Request oAuth2Request, Client client, User endUser) {
        // Determine the issued token type based on requested_token_type
        String issuedTokenType = determineIssuedTokenType(oAuth2Request, endUser);

        // Set the issued_token_type in the context for TokenService to pick up
        if (oAuth2Request.getContext() == null) {
            oAuth2Request.setContext(new HashMap<>());
        }
        oAuth2Request.getContext().put(Token.ISSUED_TOKEN_TYPE, issuedTokenType);

        return super.createAccessToken(oAuth2Request, client, endUser)
                .doOnSuccess(token -> reportTokenExchangeAudit(oAuth2Request, client, endUser, token))
                .doOnError(error -> reportTokenExchangeFailure(oAuth2Request, client, endUser, error));
    }

    /**
     * Determine the issued token type based on the request and configuration.
     */
    private String determineIssuedTokenType(OAuth2Request oAuth2Request, User endUser) {
        // Check if requested_token_type is specified in user additional info
        if (endUser != null && endUser.getAdditionalInformation() != null) {
            Object requestedTokenType = endUser.getAdditionalInformation().get(REQUESTED_TOKEN_TYPE);
            if (requestedTokenType != null) {
                String requested = requestedTokenType.toString();
                // For now, we only issue access tokens, but return the requested type
                // if it's access_token or jwt
                if (TokenTypeURN.ACCESS_TOKEN.equals(requested) || TokenTypeURN.JWT.equals(requested)) {
                    return requested;
                }
            }
        }

        // Default to access_token as per RFC 8693
        return TokenTypeURN.ACCESS_TOKEN;
    }

    /**
     * Report a successful token exchange audit event.
     */
    private void reportTokenExchangeAudit(OAuth2Request oAuth2Request, Client client, User endUser, Token token) {
        if (auditService == null || endUser == null) {
            return;
        }

        Map<String, Object> additionalInfo = endUser.getAdditionalInformation();
        if (additionalInfo == null) {
            return;
        }

        // Determine the event type based on the delegation scenario
        String eventType = determineAuditEventType(additionalInfo);

        try {
            Map<String, Object> outcome = new HashMap<>();
            outcome.put("token_exchange", true);
            outcome.put(Token.ISSUED_TOKEN_TYPE, token.getIssuedTokenType());

            if (additionalInfo.get(DELEGATION_TYPE) != null) {
                outcome.put(DELEGATION_TYPE, additionalInfo.get(DELEGATION_TYPE));
            }
            if (additionalInfo.get(ACTOR_SUBJECT) != null) {
                outcome.put(ACTOR_SUBJECT, additionalInfo.get(ACTOR_SUBJECT));
            }

            auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                    .tokenActor(client)
                    .tokenTarget(endUser)
                    .type(eventType));

            log.debug("Token exchange audit reported: type={}, subject={}, actor={}",
                    eventType, endUser.getId(), additionalInfo.get(ACTOR_SUBJECT));
        } catch (Exception e) {
            log.warn("Failed to report token exchange audit event", e);
        }
    }

    /**
     * Report a failed token exchange audit event.
     */
    private void reportTokenExchangeFailure(OAuth2Request oAuth2Request, Client client, User endUser, Throwable error) {
        if (auditService == null) {
            return;
        }

        try {
            auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                    .tokenActor(client)
                    .tokenTarget(endUser)
                    .type(EventType.TOKEN_EXCHANGE_FAILED)
                    .throwable(error));

            log.debug("Token exchange failure audit reported: error={}", error.getMessage());
        } catch (Exception e) {
            log.warn("Failed to report token exchange failure audit event", e);
        }
    }

    /**
     * Determine the audit event type based on the delegation scenario.
     */
    private String determineAuditEventType(Map<String, Object> additionalInfo) {
        Object delegationType = additionalInfo.get(DELEGATION_TYPE);
        Object isImpersonation = additionalInfo.get(IMPERSONATION);

        if (Boolean.TRUE.equals(isImpersonation)) {
            return EventType.TOKEN_EXCHANGE_IMPERSONATION;
        }

        if (delegationType != null) {
            String type = delegationType.toString();
            if ("DELEGATION".equals(type)) {
                return EventType.TOKEN_EXCHANGE_DELEGATION;
            }
            if (type.contains("IMPERSONATION")) {
                return EventType.TOKEN_EXCHANGE_IMPERSONATION;
            }
        }

        return EventType.TOKEN_EXCHANGE;
    }

    /**
     * Check if this is a token exchange grant type.
     */
    public static boolean isTokenExchangeGrantType(String grantType) {
        return GrantType.TOKEN_EXCHANGE.equals(grantType);
    }
}
