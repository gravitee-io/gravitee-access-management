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
package io.gravitee.am.gateway.handler.root.resources.endpoint;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.gateway.handler.common.utils.RedirectUrlResolver;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.NotImplementedException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.USER_ACTIVITY_ENABLED;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ACTIVITY_RETENTION_TIME;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static java.lang.Boolean.TRUE;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractEndpoint {

    private final TemplateEngine templateEngine;
    private final RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();

    protected AbstractEndpoint() {
        templateEngine = null;
    }

    protected AbstractEndpoint(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String getTemplateSuffix() {
        throw new NotImplementedException("No need to render a template");
    }

    protected final String getTemplateFileName(Client client) {
        return getTemplateSuffix() +
                Optional.ofNullable(client).map(c -> FormManager.TEMPLATE_NAME_SEPARATOR + c.getId()).orElse("");
    }

    protected final void copyValue(HttpServerRequest request, RoutingContext routingContext, String paramKey) {
        routingContext.put(paramKey, request.getParam(paramKey));
    }

    protected void renderPage(RoutingContext routingContext, Map<String, Object> data, Client client, Logger logger, String errorMessage) {
        this.renderPage(data, client)
                .subscribe(
                        buffer -> {
                            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                            routingContext.response().end(buffer);
                        },
                        throwable -> {
                            logger.error(errorMessage, throwable);
                            routingContext.fail(throwable.getCause());
                        }
                );
    }

    protected Single<Buffer> renderPage(Map<String, Object> data, Client client) {
        return templateEngine.render(data, getTemplateFileName(client));
    }

    protected String getReturnUrl(RoutingContext context, MultiMap queryParams) {
        return redirectUrlResolver.resolveRedirectUrl(context, queryParams);
    }

    protected void addUserActivityTemplateVariables(RoutingContext routingContext, UserActivityGatewayService userActivityService) {
        routingContext.put(USER_ACTIVITY_ENABLED, userActivityService.canSaveUserActivity());
        if (userActivityService.canSaveUserActivity()) {
            final long time = Math.abs(userActivityService.getRetentionTime());
            final String retentionUnit = userActivityService.getRetentionUnit().name().toLowerCase(Locale.ROOT);
            final String retentionTime = time + " " + (time > 1 ? retentionUnit : retentionUnit.substring(0, retentionUnit.length() - 1));
            routingContext.put(USER_ACTIVITY_RETENTION_TIME, retentionTime);
        }
    }

    protected void addUserActivityConsentTemplateVariables(RoutingContext routingContext) {
        final Session session = routingContext.session();
        if (session != null) {
            routingContext.put(USER_CONSENT_IP_LOCATION, TRUE.equals(session.get(USER_CONSENT_IP_LOCATION)));
            routingContext.put(USER_CONSENT_USER_AGENT, TRUE.equals(session.get(USER_CONSENT_USER_AGENT)));
        }
    }
}
