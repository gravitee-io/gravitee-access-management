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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import io.gravitee.am.authdevice.notifier.api.model.NotifierCapability;
import io.gravitee.am.common.exception.oauth2.InvalidBindingMessageException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.MissingUserCodeException;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequestResolver;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.gravitee.am.common.ciba.Parameters.AUTHORIZATION_DETAILS;
import static io.gravitee.am.common.utils.ConstantKeys.CIBA_AUTH_REQUEST_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_OBJECT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class AuthenticationRequestParametersHandler implements Handler<RoutingContext> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> AUTHORIZATION_DETAILS_TYPE = new TypeReference<>() { };

    private final CibaAuthenticationRequestResolver cibaRequestResolver;
    private final int bindingMessageMaxLength;
    private final boolean fapiEnabled;
    private final Domain domain;
    private final AuthenticationDeviceNotifierManager deviceNotifierManager;

    public AuthenticationRequestParametersHandler(Domain domain, JWSService jwsService, JWKService jwkService, UserGatewayService userService, ScopeManager scopeManager, SubjectManager subjectManager, ProtectedResourceManager protectedResourceManager, AuthenticationDeviceNotifierManager deviceNotifierManager) {
        this.bindingMessageMaxLength = domain.getOidc().getCibaSettings().getBindingMessageLength();
        this.fapiEnabled = domain.usePlainFapiProfile() || domain.useFapiBrazilProfile();
        this.domain = domain;
        this.deviceNotifierManager = deviceNotifierManager;

        cibaRequestResolver = new CibaAuthenticationRequestResolver(domain, jwsService, jwkService, userService, subjectManager, deviceNotifierManager);
        cibaRequestResolver.setManagers(scopeManager, protectedResourceManager);
    }

    boolean authorizationDetailsEnabled() {
        return hasCapability(NotifierCapability.AUTHORIZATION_DETAILS);
    }

    private boolean hasCapability(NotifierCapability cap) {
        var notifiers = domain.getOidc().getCibaSettings().getDeviceNotifiers();
        if (notifiers == null || notifiers.isEmpty()) return false;
        var p = deviceNotifierManager.getAuthDeviceNotifierProvider(notifiers.get(0).getId());
        return p != null && p.capabilities().contains(cap);
    }

    @Override
    public void handle(RoutingContext context) {
        final CibaAuthenticationRequest request = createCibaRequest(context);

        final OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
        final Client client = context.get(CLIENT_CONTEXT_KEY);

        try {
            if (fapiEnabled && context.get(REQUEST_OBJECT_KEY) == null) {
                throw new InvalidRequestException("Signed authentication request is required for FAPI");
            }
            validateScopes(request);
            validateAcrValue(openIDProviderMetadata, request);
            validateHints(request);
            validateBindingMessage(request);
            validateUserCode(client, request);
            applyAuthorizationDetails(context, request);

            cibaRequestResolver
                    .resolve(request, client)
                    .subscribe(requestValidated -> {
                        context.put(CIBA_AUTH_REQUEST_KEY, requestValidated);
                        if (request.getUser() != null) {
                            context.put(USER_CONTEXT_KEY, request.getUser());
                        }
                        context.next();
                    }, context::fail);

        } catch (Exception e) {
            log.debug("CIBA Authentication Request parameter validation fails due to : {}", e.getMessage());
            context.fail(e);
        }
    }

    protected CibaAuthenticationRequest createCibaRequest(RoutingContext context) {
        return CibaAuthenticationRequest.createFrom(context);
    }

    private void validateScopes(CibaAuthenticationRequest request) {
        if (request.getScopes() == null || !request.getScopes().contains(Scope.OPENID.getKey())) {
            throw new InvalidScopeException("scope is missing or doesn't contain 'openid'");
        }
    }

    private void validateAcrValue(OpenIDProviderMetadata openIDProviderMetadata, CibaAuthenticationRequest request) {
        if (request.getAcrValues() != null && request.getAcrValues().stream().noneMatch(openIDProviderMetadata.getAcrValuesSupported()::contains)) {
            throw new InvalidRequestException("Unsupported acr values");
        }
    }

    private void validateHints(CibaAuthenticationRequest request) {
        final long hints = Stream.of(request.getLoginHint(), request.getLoginHintToken(), request.getIdTokenHint())
                .filter(StringUtils::hasText)
                .count();
        if (hints != 1) {
            throw new InvalidRequestException("Only one of login_hint_token, id_token_hint, login_hint is accepted");
        }
    }

    private void validateUserCode(Client client, CibaAuthenticationRequest request) {
        if (!StringUtils.hasText(request.getUserCode()) && client.getBackchannelUserCodeParameter()) {
            throw new MissingUserCodeException("user_code is missing");
        }
    }

    private void validateBindingMessage(CibaAuthenticationRequest request) {
        if (StringUtils.hasText(request.getBindingMessage()) && request.getBindingMessage().length() > this.bindingMessageMaxLength) {
            throw new InvalidBindingMessageException("binding_message is too long");
        }
    }

    private void applyAuthorizationDetails(RoutingContext context, CibaAuthenticationRequest request) {
        if (!authorizationDetailsEnabled()) {
            return; // capability absent => stock: never read or parse the parameter
        }
        final List<Map<String, Object>> details = resolveAuthorizationDetails(context);
        if (details == null) {
            return;
        }
        validateAuthorizationDetailTypes(details);
        request.setAuthorizationDetails(details);
    }

    /**
     * authorization_details is a structured RFC 9396 parameter. Under FAPI/CIBA a signed request
     * object is mandatory, and Nimbus hands the claim back as a java.util.List — which the stock
     * ParamUtils.getOAuthParameter renders through List.toString() ("[{type=x}]"), i.e. not JSON,
     * guaranteeing a 400 on exactly that profile. So read the structured claim straight off the
     * request object here and only fall back to the raw query-string parameter (which getOAuthParameter
     * returns verbatim) when no request object carries it. PAR does not apply to the backchannel endpoint.
     */
    private List<Map<String, Object>> resolveAuthorizationDetails(RoutingContext context) {
        final JWT requestObject = context.get(REQUEST_OBJECT_KEY);
        if (requestObject != null) {
            try {
                final Object claim = requestObject.getJWTClaimsSet().getClaim(AUTHORIZATION_DETAILS);
                if (claim != null) {
                    return OBJECT_MAPPER.convertValue(claim, AUTHORIZATION_DETAILS_TYPE);
                }
            } catch (ParseException | IllegalArgumentException e) {
                throw new InvalidRequestException("Invalid authorization_details");
            }
        }
        final String raw = getOAuthParameter(context, AUTHORIZATION_DETAILS);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, AUTHORIZATION_DETAILS_TYPE);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Invalid authorization_details");
        }
    }

    /** RFC 9396 §2: each authorization_details element MUST be a JSON object carrying a string "type". */
    private void validateAuthorizationDetailTypes(List<Map<String, Object>> details) {
        if (details == null) {
            return;
        }
        for (Map<String, Object> element : details) {
            Object type = element == null ? null : element.get("type");
            if (!(type instanceof String s) || s.isBlank()) {
                throw new InvalidRequestException("Each authorization_details entry must have a non-empty string \"type\" (RFC 9396 §2)");
            }
        }
    }
}
