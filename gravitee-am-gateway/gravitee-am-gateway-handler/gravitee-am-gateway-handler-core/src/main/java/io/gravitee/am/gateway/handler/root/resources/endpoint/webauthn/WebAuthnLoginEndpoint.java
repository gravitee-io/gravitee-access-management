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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserActivityService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnLoginEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnLoginEndpoint.class);
    private final Domain domain;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final UserActivityService userActivityService;

    public WebAuthnLoginEndpoint(ThymeleafTemplateEngine engine,
                                 Domain domain,
                                 DeviceIdentifierManager deviceIdentifierManager,
                                 UserActivityService userActivityService) {
        super(engine);
        this.domain = domain;
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.userActivityService = userActivityService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        renderPage(routingContext);
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            // prepare the context
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));

            final String loginActionKey = routingContext.get(CONTEXT_PATH) + "/login";
            routingContext.put(ConstantKeys.LOGIN_ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), loginActionKey, queryParams, true));
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, Collections.singletonMap(Parameters.CLIENT_ID, client.getClientId()));
            addUserActivityTemplateVariables(routingContext, userActivityService);
            addUserActivityConsentTemplateVariables(routingContext);

            // render the webauthn login page
            final Map<String, Object> data = generateData(routingContext, domain, client);
            data.putAll(deviceIdentifierManager.getTemplateVariables(client));
            renderPage(routingContext, data, client, logger, "Unable to render WebAuthn login page");
        } catch (Exception ex) {
            logger.error("An error occurs while rendering WebAuthn login page", ex);
            routingContext.fail(503);
        }
    }

    @Override
    public String getTemplateSuffix() {
        return Template.WEBAUTHN_LOGIN.template();
    }
}
