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
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.stream.Stream;

import static io.gravitee.am.common.utils.ConstantKeys.CIBA_AUTH_REQUEST_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestParametersHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestParametersHandler.class);

    private final CibaAuthenticationRequestResolver cibaRequestResolver;
    private final int bindingMessageMaxLength;

    public AuthenticationRequestParametersHandler(Domain domain, JWSService jwsService, JWKService jwkService, UserService userService, ScopeManager scopeManager, SubjectManager subjectManager) {
        this.bindingMessageMaxLength = domain.getOidc().getCibaSettings().getBindingMessageLength();

        cibaRequestResolver = new CibaAuthenticationRequestResolver(domain, jwsService, jwkService, userService, subjectManager);
        cibaRequestResolver.setScopeManager(scopeManager);
    }

    @Override
    public void handle(RoutingContext context) {
        final CibaAuthenticationRequest request = createCibaRequest(context);

        final OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
        final Client client = context.get(CLIENT_CONTEXT_KEY);

        try {
            validateScopes(request);
            validateAcrValue(openIDProviderMetadata, request);
            validateHints(request);
            validateBindingMessage(request);
            validateUserCode(client, request);

            cibaRequestResolver
                    .resolve(request, client)
                    .subscribe(requestValidated -> {
                        context.put(CIBA_AUTH_REQUEST_KEY, requestValidated);
                        context.next();
                    }, context::fail);

        } catch (Exception e) {
            LOGGER.debug("CIBA Authentication Request parameter validation fails due to : {}", e.getMessage());
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
}
