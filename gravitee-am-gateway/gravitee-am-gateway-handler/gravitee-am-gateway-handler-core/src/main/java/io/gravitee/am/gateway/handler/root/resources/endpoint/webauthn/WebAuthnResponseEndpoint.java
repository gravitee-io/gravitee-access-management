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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * The callback route to verify attestations and assertions. Usually this route is <pre>/webauthn/response</pre>
 *
 *  // TODO : This endpoint exists only because of https://github.com/gravitee-io/issues/issues/7158
 *  // should be removed in a future version of AM
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnResponseEndpoint extends AbstractEndpoint implements Handler<RoutingContext>  {

    @Override
    public void handle(RoutingContext ctx) {
        // at this stage the user has been authenticated
        // redirect the user to the original request
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(ctx.request());
        final String redirectUri = getReturnUrl(ctx, queryParams);

        ctx.response().putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUri)
                .setStatusCode(302)
                .end();
    }
}
