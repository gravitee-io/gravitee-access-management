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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.AuthorizationRequestFactory;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public abstract class AbstractAuthorizationEndpointHandler {

    protected final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();

    protected AuthorizationRequest resolveInitialAuthorizeRequest(RoutingContext routingContext) {
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);
        // we have the authorization request in session if we come from the approval user page
        if (authorizationRequest != null) {
            // remove OAuth2Constants.AUTHORIZATION_REQUEST session value
            // should not be used after this step
            routingContext.session().remove(OAuth2Constants.AUTHORIZATION_REQUEST);
            return authorizationRequest;
        }

        // the initial request failed for some reasons, we have the required request parameters to re-create the authorize request
        return authorizationRequestFactory.create(routingContext.request());
    }
}
