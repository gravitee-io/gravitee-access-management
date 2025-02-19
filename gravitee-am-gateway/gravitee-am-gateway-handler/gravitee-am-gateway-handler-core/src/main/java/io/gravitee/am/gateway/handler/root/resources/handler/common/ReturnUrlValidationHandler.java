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
package io.gravitee.am.gateway.handler.root.resources.handler.common;

import io.gravitee.am.common.exception.oauth2.ReturnUrlMismatchException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.redirectMatches;

@RequiredArgsConstructor
public class ReturnUrlValidationHandler implements Handler<RoutingContext> {
    private final Domain domain;
    private final String gatewayUrl;


    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        validateReturnUrl(context, client)
                .subscribe(x -> context.next(), context::fail);
    }


    private Single<Boolean> validateReturnUrl(RoutingContext context, Client client) {
        String returnUrl = getOAuthParameter(context, ConstantKeys.RETURN_URL_KEY);
        if(returnUrl == null) {
            return Single.just(true);
        } else {
            List<String> redirectUris = new ArrayList<>();
            redirectUris.add(gatewayUrl);
            if(client.getRedirectUris() != null){
                redirectUris.addAll(client.getRedirectUris());
            }
            return checkReturnUrl(returnUrl, redirectUris);
        }
    }

    private Single<Boolean> checkReturnUrl(String requestedReturnUrl, List<String> registeredClientRedirectUris) {
        if (registeredClientRedirectUris
                .stream()
                .noneMatch(registeredClientUri -> redirectMatches(requestedReturnUrl, registeredClientUri, this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile()))) {
            return Single.error(new ReturnUrlMismatchException(String.format("The return_url [ %s ] MUST match the registered callback URL for this application", requestedReturnUrl)));
        }
        return Single.just(true);
    }
}
