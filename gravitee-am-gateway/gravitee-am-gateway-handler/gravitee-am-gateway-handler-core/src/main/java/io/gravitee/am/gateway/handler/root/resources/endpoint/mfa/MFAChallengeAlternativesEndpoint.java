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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeAlternativesEndpoint extends AbstractEndpoint implements Handler<RoutingContext>  {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeAlternativesEndpoint.class);
    private final FactorManager factorManager;

    public MFAChallengeAlternativesEndpoint(TemplateEngine templateEngine,
                                            FactorManager factorManager) {
        super(templateEngine);
        this.factorManager = factorManager;
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_CHALLENGE_ALTERNATIVES.template();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                get(routingContext);
                break;
            case "POST":
                post(routingContext);
                break;
            default:
                routingContext.fail(new HttpException(405, "Unsupported method"));
        }
    }

    private void get(RoutingContext routingContext) {
        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to access MFA challenge alternatives page.");
            routingContext.fail(new HttpException(401, "User must be authenticated to access MFA challenge alternatives page."));
            return;
        }

        final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        if (endUser.getFactors() == null || endUser.getFactors().size() <= 1) {
            logger.warn("User must have at least two enrolled factors to access MFA challenge alternatives page.");
            routingContext.fail(new HttpException(400, "User must have at least two enrolled factors to access MFA challenge alternatives page."));
            return;
        }

        // prepare context
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final List<Factor> factors = getEnabledFactors(client, endUser);
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
        routingContext.put(ConstantKeys.FACTORS_KEY, factors);
        routingContext.put(ConstantKeys.ACTION_KEY, action);

        // render the mfa challenge alternatives page
        this.renderPage(routingContext, routingContext.data(), client, logger, "Unable to render MFA challenge alternatives page");
    }

    private void post(RoutingContext routingContext) {
        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to submit MFA challenge alternate choice.");
            routingContext.fail(new HttpException(401, "User must be authenticated to submit MFA challenge alternate choice."));
            return;
        }

        final MultiMap params = routingContext.request().formAttributes();
        final String factorId = params.get("factorId");

        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(new HttpException(400, "No factor id in form - did you forget to include factor id value ?"));
            return;
        }

        // store alternative factor
        routingContext.session().put(ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY, factorId);

        // redirect to MFA challenge step
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String returnURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/mfa/challenge", queryParams, true);
        doRedirect(routingContext.response(), returnURL);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private List<Factor> getEnabledFactors(Client client, io.gravitee.am.model.User endUser ){
        final Set<String> clientFactorIds = client.getFactors();
        return endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> factorManager.get(enrolledFactor.getFactorId()) != null)
                .filter(enrolledFactor ->  clientFactorIds.contains(enrolledFactor.getFactorId()))
                .map(enrolledFactor -> new Factor(factorManager.getFactor(enrolledFactor.getFactorId()), enrolledFactor))
                .collect(Collectors.toList());
    }

    private static class Factor {
        private String id;
        private String name;
        private String factorType;
        private String target;

        public Factor(io.gravitee.am.model.Factor factor, EnrolledFactor enrolledFactor) {
            this.id = factor.getId();
            this.name = factor.getName();
            this.factorType = factor.getFactorType().getType();
            this.target = enrolledFactor.getChannel() != null ? enrolledFactor.getChannel().getTarget() : null;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFactorType() {
            return factorType;
        }

        public void setFactorType(String factorType) {
            this.factorType = factorType;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}
