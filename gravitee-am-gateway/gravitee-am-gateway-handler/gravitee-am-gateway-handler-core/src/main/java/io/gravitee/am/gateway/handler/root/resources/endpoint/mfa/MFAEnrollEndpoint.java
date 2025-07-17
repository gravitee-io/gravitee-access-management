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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.EnrollmentChannelValidationException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAEnrollEndpoint.class);

    private final FactorManager factorManager;
    private final Domain domain;
    private final ApplicationContext applicationContext;

    private final RuleEngine ruleEngine;

    public MFAEnrollEndpoint(FactorManager factorManager,
                             TemplateEngine engine,
                             Domain domain,
                             ApplicationContext applicationContext,
                             RuleEngine ruleEngine) {
        super(engine);
        this.factorManager = factorManager;
        this.domain = domain;
        this.applicationContext = applicationContext;
        this.ruleEngine = ruleEngine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        renderPage(routingContext);
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to enroll MFA challenge.");
                routingContext.fail(401, new InvalidRequestException("Authentication required to enroll a factor"));
                return;
            }
            var context = new MfaFilterContext(routingContext, routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY), factorManager, ruleEngine);
            if (context.userHasMatchingActivatedFactors()) {
                logger.warn("User already has a factor.");
                redirectToAuthorize(routingContext);
                return;
            }

            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final Map<io.gravitee.am.model.Factor, FactorProvider> factors = getFactors(client);

            // Create post action url.
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);

            // load factor providers
            final FactorContext factorContext = new FactorContext(applicationContext, new HashMap<>());
            factorContext.registerData(FactorContext.KEY_USER, endUser);
            load(factors, factorContext, h -> {
                if (h.failed()) {
                    logger.error("An error occurs while loading factor providers", h.cause());
                    routingContext.fail(503);
                    return;
                }

                // put factors in context
                routingContext.put("factors", factorsToRender(h.result(), routingContext.session(), client, routingContext));

                addPhoneNumberToRoutingContext(routingContext, endUser);
                addEmailAddressToRoutingContext(routingContext, endUser);

                routingContext.put(ConstantKeys.MFA_FORCE_ENROLLMENT, !MfaUtils.isCanSkip(routingContext, client));
                routingContext.put(ConstantKeys.ACTION_KEY, action);
                // render the mfa enroll page
                this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render MFA enroll page");
            });
        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA enroll page", ex);
            routingContext.fail(503);
        }
    }

    private void addPhoneNumberToRoutingContext(RoutingContext routingContext, User endUser) {
        if (hasText(endUser.getPhoneNumber())){
            routingContext.put("phoneNumber", endUser.getPhoneNumber());
        } else if (endUser.getPhoneNumbers() != null && !endUser.getPhoneNumbers().isEmpty()) {
            routingContext.put("phoneNumber", endUser.getPhoneNumbers().stream()
                    .filter(attribute -> Boolean.TRUE.equals(attribute.isPrimary()))
                    .findFirst()
                    .orElse(endUser.getPhoneNumbers().get(0)).getValue());
        }
    }

    private void addEmailAddressToRoutingContext(RoutingContext routingContext, User endUser) {
        if (endUser.getEmail() != null && !endUser.getEmail().isEmpty()) {
            routingContext.put("emailAddress", endUser.getEmail());
        }
    }

    /**
     * Filter out recovery code factor from the given list of factors
     *
     * @param factors list of Factor object
     * @param session current session
     * @return list of Factor object
     */
    private List<Factor> factorsToRender(List<Factor> factors, Session session, Client client, RoutingContext context) {
        // if an alternative factor ID has been set, only display this one
        final String alternativeFactorId = session.get(ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY);
        var mfaContext = new MfaFilterContext(context, client, factorManager, ruleEngine);
        if (alternativeFactorId != null && !alternativeFactorId.isEmpty()) {
            // check if this alternative factor still exists
            Optional<Factor> optionalFactor = factors.stream()
                    .filter(factor -> alternativeFactorId.equals(factor.getId()))
                    .findFirst();
            if (optionalFactor.isPresent()) {
                return List.of(optionalFactor.get());
            }
        }
        // else return all factors except the RECOVERY CODE one
        List<Factor> evaluatedFactors = factors.stream()
                .filter(factor -> !factor.factorType.equals(FactorType.RECOVERY_CODE.getType()))
                .filter(factor -> mfaContext.evaluateFactorRuleByFactorId(factor.getId()))
                .toList();

        if (evaluatedFactors.isEmpty()) {
            return factors.stream()
                    .filter(factor -> mfaContext.isDefaultFactor(factor.getId()))
                    .collect(Collectors.toList());
        } else {
            return evaluatedFactors;
        }
    }

    private void redirectToAuthorize(RoutingContext routingContext) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String returnURL = getReturnUrl(routingContext, queryParams);
        doRedirect(routingContext.response(), returnURL);
    }

    private void load(Map<io.gravitee.am.model.Factor, FactorProvider> providers,
                      FactorContext factorContext,
                      Handler<AsyncResult<List<Factor>>> handler) {
        Observable.fromIterable(providers.entrySet())
                .flatMapSingle(entry -> entry.getValue().enroll(factorContext)
                        .map(enrollment -> new Factor(entry.getKey(), enrollment))
                )
                .toList()
                .subscribe(
                        factors -> handler.handle(Future.succeededFuture(factors)),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private Map<io.gravitee.am.model.Factor, FactorProvider> getFactors(Client client) {
        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(f -> factorManager.get(f) != null)
                .collect(Collectors.toMap(factorManager::getFactor, factorManager::get));
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_ENROLL.template();
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    @Getter
    @Setter
    private static class Factor {
        private String id;
        private String name;
        private String factorType;
        private Enrollment enrollment;

        public Factor(io.gravitee.am.model.Factor factor, Enrollment enrollment) {
            this.id = factor.getId();
            this.name = factor.getName();
            this.factorType = factor.getFactorType().getType();
            this.enrollment = enrollment;
        }
    }
}
