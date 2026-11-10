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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import io.gravitee.am.authdevice.notifier.api.model.NotifierCapability;
import io.gravitee.am.common.exception.oauth2.InvalidBindingMessageException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.MissingUserCodeException;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequestResolver;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailsValidator;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

import java.text.ParseException;
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

    private final CibaAuthenticationRequestResolver cibaRequestResolver;
    private final int bindingMessageMaxLength;
    private final boolean fapiEnabled;
    private final Domain domain;
    private final AuthenticationDeviceNotifierManager deviceNotifierManager;
    private final AuthorizationDetailsValidator authorizationDetailsValidator;

    public AuthenticationRequestParametersHandler(Domain domain, CibaAuthenticationRequestResolver cibaRequestResolver, AuthenticationDeviceNotifierManager deviceNotifierManager, AuthorizationDetailsValidator authorizationDetailsValidator) {
        this.bindingMessageMaxLength = domain.getOidc().getCibaSettings().getBindingMessageLength();
        this.fapiEnabled = domain.usePlainFapiProfile() || domain.useFapiBrazilProfile();
        this.domain = domain;
        this.deviceNotifierManager = deviceNotifierManager;
        this.authorizationDetailsValidator = authorizationDetailsValidator;
        this.cibaRequestResolver = cibaRequestResolver;
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
            applyAuthorizationDetails(context, client, request);

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

    private void applyAuthorizationDetails(RoutingContext context, Client client, CibaAuthenticationRequest request) {
        if (!authorizationDetailsEnabled()) {
            return;
        }
        request.setAuthorizationDetails(validateAuthorizationDetails(context, client));
    }

    private AuthorizationDetails validateAuthorizationDetails(RoutingContext context, Client client) {
        final JWT requestObject = context.get(REQUEST_OBJECT_KEY);
        if (requestObject != null) {
            final Object claim;
            try {
                claim = requestObject.getJWTClaimsSet().getClaim(AUTHORIZATION_DETAILS);
            } catch (ParseException e) {
                throw new InvalidRequestException("Invalid request object");
            }
            if (claim != null) {
                return authorizationDetailsValidator.validate(toJson(claim), client);
            }
        }
        final String parameter = getOAuthParameter(context, AUTHORIZATION_DETAILS);
        return parameter == null ? null : authorizationDetailsValidator.validate(parameter, client);
    }

    private static String toJson(Object claim) {
        try {
            return OBJECT_MAPPER.writeValueAsString(claim);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Invalid request object");
        }
    }
}
