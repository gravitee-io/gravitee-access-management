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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.net.URISyntaxException;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractAuthorizationRequestParametersHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAuthorizationRequestParametersHandler.class);

    private final String loginPageUrl;

    public AbstractAuthorizationRequestParametersHandler(Domain domain) {
        this.loginPageUrl = '/' + domain.getPath() + "/login";
    }

    protected void parseMaxAgeParameter(RoutingContext context) {
        // if user is already authenticated and if the last login date is greater than the max age parameter,
        // the OP MUST attempt to actively re-authenticate the End-User.
        User authenticatedUser = context.user();
        if (
            authenticatedUser == null ||
            !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)
        ) {
            // user not authenticated, continue
            return;
        }

        String maxAge = context.request().getParam(Parameters.MAX_AGE);
        if (maxAge == null || !maxAge.matches("-?\\d+")) {
            // none or invalid max age, continue
            return;
        }

        io.gravitee.am.model.User endUser =
            ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();
        Date loggedAt = endUser.getLoggedAt();
        if (loggedAt == null) {
            // user has no last login date, continue
            return;
        }

        // check the elapsed user session duration
        long elapsedLoginTime = (System.currentTimeMillis() - loggedAt.getTime()) / 1000L;
        Long maxAgeValue = Long.valueOf(maxAge);
        if (maxAgeValue < elapsedLoginTime) {
            // check if the user doesn't come from the login page
            if (!returnFromLoginPage(context)) {
                // should we logout the user or just force it to go to the login page ?
                context.clearUser();

                // check prompt parameter in case the user set 'none' option
                parsePromptParameter(context);
            }
        }
    }

    protected boolean returnFromLoginPage(RoutingContext context) {
        String referer = context.request().headers().get(HttpHeaders.REFERER);
        try {
            return referer != null && UriBuilder.fromURIString(referer).build().getPath().contains(loginPageUrl);
        } catch (URISyntaxException e) {
            logger.debug("Unable to calculate referer url : {}", referer, e);
            return false;
        }
    }

    abstract void parsePromptParameter(RoutingContext context);
}
