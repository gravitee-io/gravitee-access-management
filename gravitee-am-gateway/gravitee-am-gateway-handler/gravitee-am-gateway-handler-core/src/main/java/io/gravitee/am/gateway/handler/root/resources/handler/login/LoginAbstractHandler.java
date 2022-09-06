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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import com.google.common.base.Strings;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class LoginAbstractHandler implements Handler<RoutingContext> {

    private static Logger logger = LoggerFactory.getLogger(LoginAbstractHandler.class);

    protected boolean evaluateIdPSelectionRule(ApplicationIdentityProvider appIdp,
                                 IdentityProvider identityProvider,
                                 io.gravitee.el.TemplateEngine templateEngine) {
        var rule = appIdp.getSelectionRule();
        // We keep the same behaviour as before, if there is no rule, no automatic redirect
        if (Strings.isNullOrEmpty(rule) || rule.isBlank()) {
            return false;
        }
        if (templateEngine == null) {
            return false;
        }
        try {
            if (identityProvider != null) {
                IdentityProvider safeIdP = new IdentityProvider(identityProvider);
                safeIdP.setConfiguration(null);
                templateEngine.getTemplateContext().setVariable(ConstantKeys.IDENTITY_PROVIDER_CONTEXT_KEY, safeIdP);
            }
            return templateEngine.getValue(rule.trim(), Boolean.class);
        } catch (Exception e) {
            logger.warn("Cannot evaluate the expression [{}] as boolean", rule);
            logger.debug("Idp selection rule evaluation has raised the following exception", e);
            return false;
        }
    }

    protected void doRedirect(RoutingContext routingContext, String url) {
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}
