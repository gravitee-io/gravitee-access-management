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
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static java.util.Optional.ofNullable;
import java.util.Set;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAEnrollEndpoint.class);

    private final FactorManager factorManager;
    private final UserService userService;
    private final Domain domain;
    private final ApplicationContext applicationContext;

    public MFAEnrollEndpoint(FactorManager factorManager,
                             TemplateEngine engine,
                             UserService userService,
                             Domain domain,
                             ApplicationContext applicationContext) {
        super(engine);
        this.factorManager = factorManager;
        this.userService = userService;
        this.domain = domain;
        this.applicationContext = applicationContext;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderPage(routingContext);
                break;
            case "POST":
                saveEnrollment(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderPage(RoutingContext routingContext) {
        try {
            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to enroll MFA challenge.");
                routingContext.fail(401);
                return;
            }
            var context = new MfaFilterContext(routingContext, routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY), factorManager);
            if (context.userHasMatchingFactors()) {
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
                routingContext.put("factors", factorsToRender(h.result(), routingContext.session()));

                if (endUser.getPhoneNumbers() != null && !endUser.getPhoneNumbers().isEmpty()) {
                    routingContext.put("phoneNumber", endUser.getPhoneNumbers().stream()
                            .filter(attribute -> Boolean.TRUE.equals(attribute.isPrimary()))
                            .findFirst()
                            .orElse(endUser.getPhoneNumbers().get(0)).getValue());
                }
                if (endUser.getEmail() != null && !endUser.getEmail().isEmpty()) {
                    routingContext.put("emailAddress", endUser.getEmail());
                }

                routingContext.put(ConstantKeys.MFA_FORCE_ENROLLMENT, isForceMfaActive(client));
                routingContext.put(ConstantKeys.ACTION_KEY, action);
                // render the mfa enroll page
                this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render MFA enroll page");
            });
        } catch (Exception ex) {
            logger.error("An error occurs while rendering MFA enroll page", ex);
            routingContext.fail(503);
        }
    }

    /**
     * Filter out recovery code factor from the given list of factors
     *
     * @param factors list of Factor object
     * @param session current session
     * @return list of Factor object
     */
    private List<Factor> factorsToRender(List<Factor> factors, Session session) {
        // if an alternative factor ID has been set, only display this one
        final String alternativeFactorId = session.get(ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY);
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
        return factors.stream()
                .filter(factor -> !factor.factorType.equals(FactorType.RECOVERY_CODE.getType()))
                .collect(Collectors.toList());
    }

    private boolean isForceMfaActive(Client client) {
        var enrollmentSettings = ofNullable(client.getMfaSettings()).orElse(new MFASettings()).getEnrollment();
        return ofNullable(enrollmentSettings).map(EnrollmentSettings::getForceEnrollment).orElse(false);
    }

    private void saveEnrollment(RoutingContext routingContext) {
        MultiMap params = routingContext.request().formAttributes();
        final boolean acceptEnrollment = Boolean.parseBoolean(params.get(ConstantKeys.USER_MFA_ENROLLMENT));
        final String factorId = params.get(ConstantKeys.MFA_ENROLLMENT_FACTOR_ID);
        final String sharedSecret = params.get(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET);
        final String phoneNumber = params.get(ConstantKeys.MFA_ENROLLMENT_PHONE);
        final String extensionPhoneNumber = params.get(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER);
        final String emailAddress = params.get(ConstantKeys.MFA_ENROLLMENT_EMAIL);
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // if user has skipped the enrollment process, continue
        if (!acceptEnrollment) {
            final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // set the last skipped time
            // and update the session
            userService.setMfaEnrollmentSkippedTime(client, endUser)
                    .subscribe(() -> {
                        // as the user has skipped the MFA enroll page
                        // that means the user has also skipped the MFA challenge page
                        routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true);
                        routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
                        redirectToAuthorize(routingContext);
                    });
            return;
        }

        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(400);
            return;
        }

        final Map<io.gravitee.am.model.Factor, FactorProvider> factors = getFactors(client);
        Optional<Map.Entry<io.gravitee.am.model.Factor, FactorProvider>> optFactor = factors.entrySet().stream().filter(factor -> factorId.equals(factor.getKey().getId())).findFirst();
        if (optFactor.isEmpty()) {
            logger.warn("Factor not found - did you send a valid factor id ?");
            routingContext.fail(400);
            return;
        }

        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to enroll MFA challenge.");
            routingContext.fail(401);
            return;
        }

        final var endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        if (userAlreadyHasFactor(endUser, client, routingContext.session())){
            logger.warn("User already has active factor, enrollment of factor '{}' rejected", factorId);
            routingContext.fail(new InvalidRequestException("factor already enrolled"));
            return;
        }

        // manage enrolled factors
        FactorProvider provider = optFactor.get().getValue();
        if (provider.checkSecurityFactor(getSecurityFactor(params, optFactor.get().getKey()))) {
            // save enrolled factor for the current user and continue
            routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, factorId);
            logger.debug("Factor {} selected for user {}", factorId, endUser.getId());
            if (sharedSecret != null) {
                routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY, sharedSecret);
            }
            if (phoneNumber != null) {
                routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER, phoneNumber);
            }
            if (extensionPhoneNumber != null) {
                routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER, extensionPhoneNumber);
            }
            if (emailAddress != null) {
                routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, emailAddress);
            }
            // update the session
            routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true);
            // redirect to the original request
            redirectToAuthorize(routingContext);
        } else {
            // parameters are invalid
            routingContext.fail(400);
        }
    }

    private boolean userAlreadyHasFactor(User endUser, Client client, Session session) {
        // check if MFA force enroll is enabled
        if (Boolean.TRUE.equals(session.get(ConstantKeys.MFA_FORCE_ENROLLMENT))) {
            return false;
        }

        // verify that the user has no enrolled factor
        final var recoveryCodeFactors = getRecoveryFactorIds(client);
        return isNotEmpty(endUser.getFactors()) &&
                endUser.getFactors().stream()
                        .filter(enrolledFactor -> !recoveryCodeFactors.contains(enrolledFactor.getFactorId()))
                        .anyMatch(enrolledFactor -> enrolledFactor.getStatus() == FactorStatus.ACTIVATED);
    }

    private void redirectToAuthorize(RoutingContext routingContext) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String returnURL = getReturnUrl(routingContext, queryParams);
        doRedirect(routingContext.response(), returnURL);
    }

    private EnrolledFactor getSecurityFactor(MultiMap params, io.gravitee.am.model.Factor factor) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        switch (factor.getFactorType()) {
            case SMS:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.SMS, params.get("phone")));
                break;
            case CALL:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.CALL, params.get("phone")));
                break;
            case EMAIL:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.EMAIL, params.get("email")));
                break;
        }
        // set shared secret
        final String sharedSecret = params.get("sharedSecret");
        if (sharedSecret != null && !sharedSecret.isEmpty()) {
            enrolledFactor.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, sharedSecret));
        }
        return enrolledFactor;
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
        return client.getFactors()
                .stream()
                .filter(f -> factorManager.get(f) != null)
                .collect(Collectors.toMap(factorManager::getFactor, factorManager::get));
    }

    private Set<String> getRecoveryFactorIds(Client client) {
        return client.getFactors()
                .stream()
                .filter(f -> factorManager.getFactor(f) != null && FactorType.RECOVERY_CODE.equals(factorManager.getFactor(f).getFactorType()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_ENROLL.template();
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFactorType() {
            return factorType;
        }

        public void setFactorType(String factorType) {
            this.factorType = factorType;
        }

        public Enrollment getEnrollment() {
            return enrollment;
        }

        public void setEnrollment(Enrollment enrollment) {
            this.enrollment = enrollment;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
