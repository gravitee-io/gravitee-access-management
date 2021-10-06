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

import io.gravitee.am.common.exception.oauth2.InvalidBindingMessageException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.MissingUserCodeException;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequestResolver;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

import java.util.stream.Stream;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestParametersHandler implements Handler<RoutingContext> {

    private Domain domain;
    private JWSService jwsService;
    private JWKService jwkService;
    private UserService userService;

    private final int bindingMessageMaxLength;

    public AuthenticationRequestParametersHandler(Domain domain, JWSService jwsService, JWKService jwkService, UserService userService) {
        this.domain = domain;
        this.jwsService = jwsService;
        this.jwkService = jwkService;
        this.userService = userService;

        this.bindingMessageMaxLength = domain.getOidc().getCibaSettings().getBindingMessageLength();
    }

    @Override
    public void handle(RoutingContext context) {
        final CibaAuthenticationRequest request = createCibaRequest(context);

        final OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
        final Client client = context.get(CLIENT_CONTEXT_KEY);

        validateScopes(request);

        validateAcrValue(openIDProviderMetadata, request);

        validateHints(request);

        validateBindingMessage(request);

        validateUserCode(client, request);

        new CibaAuthenticationRequestResolver(domain, jwsService, jwkService, userService)
                .resolve(request, client)
                .subscribe(requestValidated -> {
                    context.put(CIBA_AUTH_REQUEST_KEY, requestValidated);
                    context.next();
                }, context::fail);
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
        if (request.getAcrValues() != null && !request.getAcrValues().stream().anyMatch(openIDProviderMetadata.getAcrValuesSupported()::contains)) {
            throw new InvalidRequestException("Unsupported acr values");
        }
    }

    private void validateHints(CibaAuthenticationRequest request) {
        final long hints = Stream.of(request.getLoginHint(), request.getLoginHintToken(), request.getIdTokenHint())
                .filter(value -> !StringUtils.isEmpty(value))
                .count();
        if (hints != 1) {
            throw new InvalidRequestException("Only one of login_hint_token, id_token_hint, login_hint is accepted");
        }
    }

    private void validateUserCode(Client client, CibaAuthenticationRequest request) {
        if (StringUtils.isEmpty(request.getUserCode()) && client.getBackchannelUserCodeParameter()) {
            throw new MissingUserCodeException("user_code is missing");
        }
    }

    private void validateBindingMessage(CibaAuthenticationRequest request) {
        if (!StringUtils.isEmpty(request.getBindingMessage()) && request.getBindingMessage().length() > this.bindingMessageMaxLength) {
            throw new InvalidBindingMessageException("binding_message is too long");
        }
    }
}
