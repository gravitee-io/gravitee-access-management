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

import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.MFAAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.common.util.Maps;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE_SENT;
import static io.gravitee.am.common.audit.EventType.MFA_RATE_LIMIT_REACHED;
import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_SENT_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RATE_LIMIT_ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.VERIFY_ATTEMPT_ERROR_PARAM_KEY;
import static io.gravitee.am.factor.api.FactorContext.KEY_USER;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.lang.Boolean.TRUE;


abstract class MFAChallengeEndpoint extends MFAEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeEndpoint.class);

    public static final String PREVIOUS_TRANSACTION_ID_KEY = "prev-tid";

    protected final FactorManager factorManager;
    protected final ApplicationContext applicationContext;
    protected final DomainDataPlane domainDataPlane;
    protected final RateLimiterService rateLimiterService;
    protected final AuditService auditService;

    public MFAChallengeEndpoint(FactorManager factorManager,
                                TemplateEngine engine,
                                ApplicationContext applicationContext,
                                DomainDataPlane domainDataPlane,
                                RateLimiterService rateLimiterService,
                                AuditService auditService) {
        super(engine);
        this.factorManager = factorManager;
        this.applicationContext = applicationContext;
        this.domainDataPlane = domainDataPlane;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
    }

    protected String challengePagePath(RoutingContext routingContext) {
        return routingContext.get(CONTEXT_PATH) + "/mfa/challenge";
    }

    protected void sendMfaChallenge(FactorProvider factorProvider,
                                    RoutingContext routingContext,
                                    Factor factor,
                                    User endUser,
                                    boolean forceResend,
                                    boolean jsonResponse,
                                    Handler<AsyncResult<EnrolledFactor>> handler) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final FactorContext factorContext = new FactorContext(applicationContext, new HashMap<>());

        factorContext.getData().putAll(getEvaluableAttributes(routingContext));
        factorContext.registerData(FactorContext.KEY_CLIENT, client);
        factorContext.registerData(KEY_USER, endUser);
        factorContext.registerData(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));

        // do not send challenge in case of error param to avoid useless code generation
        // to provide EnrolledFactor we call the getEnrolledFactor but the overrideMovingFactor is set to false
        // so during the enrolment phase, in case of wrong code provided by the user, the code is not reset
        // otherwise the persisted value will not be the same as the one computed based on the session information
        if (!factorProvider.needChallengeSending()
                || routingContext.get(ConstantKeys.ERROR_PARAM_KEY) != null
                || routingContext.get(RATE_LIMIT_ERROR_PARAM_KEY) != null
                || routingContext.get(VERIFY_ATTEMPT_ERROR_PARAM_KEY) != null) {
            final EnrolledFactor currentEnrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, false);
            handler.handle(Future.succeededFuture(currentEnrolledFactor));
            return;
        }

        if (!forceResend && !shouldSendChallenge(routingContext, factor)) {
            final EnrolledFactor currentEnrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, false);
            handler.handle(Future.succeededFuture(currentEnrolledFactor));
            return;
        }

        final String rateLimitRedirectPath = challengePagePath(routingContext);
        if (rateLimiterService.isRateLimitEnabled()) {
            rateLimiterService.tryConsume(endUser.getId(), factor.getId(), client.getId(), client.getDomain())
                    .subscribe(allowRequest -> {
                                final EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, allowRequest);
                                factorContext.registerData(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
                                if (allowRequest) {
                                    invokeSendChallenge(routingContext, factorProvider, factorContext, endUser, client, factor, handler);
                                } else {
                                    updateAuditLog(routingContext, MFA_RATE_LIMIT_REACHED, endUser, client, factor, factorContext, new Throwable("MFA rate limit reached"));
                                    if (jsonResponse) {
                                        respondJsonChallengeError(routingContext, RATE_LIMIT_ERROR_PARAM_KEY, "mfa_request_limit_exceed",
                                                HttpStatusCode.TOO_MANY_REQUESTS_429);
                                    } else {
                                        handleException(routingContext, RATE_LIMIT_ERROR_PARAM_KEY, "mfa_request_limit_exceed", rateLimitRedirectPath);
                                    }
                                }
                            },
                            error -> handler.handle(Future.failedFuture(error))
                    );
        } else {
            final EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorContext, true);
            factorContext.registerData(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
            invokeSendChallenge(routingContext, factorProvider, factorContext, endUser, client, factor, handler);
        }
    }

    protected boolean shouldSendChallenge(RoutingContext routingContext, Factor factor) {
        final String sentFactorId = routingContext.session().get(MFA_CHALLENGE_SENT_FACTOR_ID_KEY);
        return !factor.getId().equals(sentFactorId);
    }

    protected void markChallengeSent(RoutingContext routingContext, Factor factor) {
        routingContext.session().put(MFA_CHALLENGE_SENT_FACTOR_ID_KEY, factor.getId());
    }

    private void invokeSendChallenge(RoutingContext routingContext,
                                     FactorProvider factorProvider,
                                     FactorContext factorContext,
                                     User endUser,
                                     Client client,
                                     Factor factor,
                                     Handler<AsyncResult<EnrolledFactor>> handler) {
        factorProvider.sendChallenge(factorContext)
                .doOnComplete(() -> logger.debug("Challenge sent to user {}", factorContext.getUser().getId()))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {
                            markChallengeSent(routingContext, factor);
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, endUser, client, factor, factorContext, null);
                            handler.handle(Future.succeededFuture((EnrolledFactor) factorContext.getData().get(FactorContext.KEY_ENROLLED_FACTOR)));
                        },
                        error -> {
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, endUser, client, factor, factorContext, error);
                            handler.handle(Future.failedFuture(error));
                        }
                );
    }

    protected Factor getFactor(RoutingContext routingContext, Client client, User endUser) {
        // factor can be either in session (if user come from mfa/enroll or mfa/challenge/alternatives page)
        // or from the user enrolled factor list
        final String savedFactorId =
                routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null ?
                        routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) :
                        routingContext.session().get(ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY);

        if (savedFactorId != null) {
            return factorManager.getFactor(savedFactorId);
        }

        if (endUser.getFactors() == null) {
            throw FactorNotFoundException.withMessage("No factor found for the end user");
        }

        // get the primary enrolled factor
        // if there is no primary, select the first created
        final var factors = hasApplicationFactorSettings(client) ? client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .collect(Collectors.toSet()) : Set.of();

        final var enrolledFactors = endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> factors.contains(enrolledFactor.getFactorId()))
                .sorted(Comparator.comparing(EnrolledFactor::getCreatedAt))
                .toList();

        if (enrolledFactors.isEmpty()) {
            throw FactorNotFoundException.withMessage("No factor found for the end user");
        }

        Optional<EnrolledFactor> firstPrimary = enrolledFactors
                .stream()
                .filter(e -> TRUE.equals(e.isPrimary()))
                .findFirst();

        Optional<EnrolledFactor> firstActivated = enrolledFactors
                .stream()
                .filter(e -> ACTIVATED.equals(e.getStatus()))
                .findFirst();

        return firstPrimary.or(() -> firstActivated)
                .map(enrolledFactor -> factorManager.getFactor(enrolledFactor.getFactorId()))
                .orElseGet(() -> factorManager.getFactor(enrolledFactors.get(0).getFactorId()));
    }

    protected EnrolledFactor getEnrolledFactor(RoutingContext routingContext,
                                             FactorProvider factorProvider,
                                             Factor factor,
                                             User endUser,
                                             FactorContext factorContext,
                                             boolean overrideMovingFactor) {
        // enrolled factor can be either in session (if user come from mfa/enroll page)
        // or from the user enrolled factor list
        final String savedFactorId = routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
        if (factor.getId().equals(savedFactorId)) {
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(factor.getId());
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            switch (factor.getFactorType()) {
                case OTP:
                    enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY)));
                    break;
                case SMS:
                    enrolledFactor.setChannel(new EnrolledFactorChannel(Type.SMS,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER)));
                    break;
                case CALL:
                    enrolledFactor.setChannel(new EnrolledFactorChannel(Type.CALL,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER)));
                    break;
                case EMAIL:
                    enrolledFactor.setChannel(new EnrolledFactorChannel(Type.EMAIL,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS)));
                    break;
                case RECOVERY_CODE:
                    if (endUser.getFactors() != null) {
                        Optional<EnrolledFactorSecurity> factorSecurity = endUser.getFactors()
                                .stream()
                                .filter(ftr -> ftr.getSecurity().getType().equals(RECOVERY_CODE))
                                .map(EnrolledFactor::getSecurity)
                                .findFirst();

                        factorSecurity.ifPresent(enrolledFactor::setSecurity);
                    }
                    break;
                default:
            }

            // if the factor provider uses a moving factor security mechanism,
            // we ensure that every data has been shared with the user enrolled factor
            if (factorProvider.useVariableFactorSecurity(factorContext)) {
                String tid = routingContext.session().get(PREVIOUS_TRANSACTION_ID_KEY);
                if (overrideMovingFactor) {
                    tid = routingContext.get(TRANSACTION_ID_KEY);
                    routingContext.session().put(PREVIOUS_TRANSACTION_ID_KEY, tid);
                }
                enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET,
                        routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY)));
                Map<String, Object> additionalData = new Maps.MapBuilder<String, Object>(new HashMap<>())
                        .put(FactorDataKeys.KEY_MOVING_FACTOR, MovingFactorUtils.generateInitialMovingFactor(tid))
                        .build();
                getEnrolledFactor(factor, endUser).ifPresent(ef -> {
                    additionalData.put(FactorDataKeys.KEY_EXPIRE_AT, ef.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class));
                });
                enrolledFactor.getSecurity().getAdditionalData().putAll(additionalData);
            }

            // if there is an extension phone number, add it to the enrolled factor
            final String extensionPhoneNumber = routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER);
            if (extensionPhoneNumber != null && enrolledFactor.getChannel() != null) {
                var additionalData = new HashMap<String, Object>();
                additionalData.put(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER, extensionPhoneNumber);
                enrolledFactor.getChannel().setAdditionalData(additionalData);
            }

            enrolledFactor.setCreatedAt(new Date());
            enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
            return enrolledFactor;
        }

        return getEnrolledFactor(factor, endUser)
                .orElseThrow(() -> FactorNotFoundException.withMessage("No enrolled factor found for the end user"));
    }

    private Optional<EnrolledFactor> getEnrolledFactor(Factor factor, User endUser) {
        if (endUser.getFactors() == null) {
            return Optional.empty();
        }

        return endUser.getFactors()
                .stream()
                .filter(f -> factor.getId().equals(f.getFactorId()))
                .findFirst();
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_CHALLENGE.template();
    }

    protected void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    protected void respondJsonChallengeError(RoutingContext routingContext, String errorKey, String errorValue, int statusCode) {
        respondJson(routingContext, statusCode, new JsonObject().put(errorKey, errorValue), errorValue);
    }

    protected void respondJson(RoutingContext routingContext, int statusCode, JsonObject body, String sessionErrorHashSource) {
        if (sessionErrorHashSource != null && routingContext.session() != null) {
            routingContext.session().put(ERROR_HASH, HashUtil.generateSHA256(sessionErrorHashSource));
        }
        routingContext.response()
                .setStatusCode(statusCode)
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(body.encode());
    }

    protected void handleException(RoutingContext context, String errorKey, String errorValue) {
        handleException(context, errorKey, errorValue, context.request().path());
    }

    protected void handleException(RoutingContext context, String errorKey, String errorValue, String redirectPath) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // redirect to mfa challenge page with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        Map<String, String> parameters = new LinkedHashMap<>(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put(errorKey, errorValue);
        if (context.session() != null) {
            context.session().put(ERROR_HASH, HashUtil.generateSHA256(errorValue));
        }
        String uri = UriBuilderRequest.resolveProxyRequest(req, redirectPath, parameters, true);
        doRedirect(resp, uri);
    }

    protected void updateAuditLog(RoutingContext routingContext, String type, User endUser, Client client, Factor factor, FactorContext factorContext, Throwable cause) {
        final EnrolledFactor enrolledFactor = factorContext.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        final EnrolledFactorChannel channel = enrolledFactor.getChannel();

        final MFAAuditBuilder builder = AuditBuilder.builder(MFAAuditBuilder.class)
                .user(endUser)
                .factor(factor)
                .type(type)
                .channel(channel)
                .client(client)
                .reference(Reference.domain(domainDataPlane.getDomain().getId()))
                .ipAddress(routingContext)
                .userAgent(routingContext)
                .throwable(cause, channel);

        auditService.report(builder);
    }
}
