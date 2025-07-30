/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.gateway.handler.common.service.mfa.VerifyAttemptService;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.am.service.exception.MFAValidationAttemptException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.MFAAuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.VerifyAttemptAuditBuilder;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.Cookie;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE;
import static io.gravitee.am.common.audit.EventType.MFA_ENROLLMENT;
import static io.gravitee.am.common.audit.EventType.MFA_MAX_ATTEMPT_REACHED;
import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_TYPE;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_FAILED;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.VERIFY_ATTEMPT_ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;

public class MFAChallengePostEndpoint extends MFAEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengePostEndpoint.class);

    private static final String REMEMBER_DEVICE_CONSENT = "rememberDeviceConsent";
    public static final String REMEMBER_DEVICE_CONSENT_ON = "on";
    public static final String PREVIOUS_TRANSACTION_ID_KEY = "prev-tid";

    private final FactorManager factorManager;
    private final UserService userService;
    private final ApplicationContext applicationContext;
    private final DeviceGatewayService deviceService;
    private final DomainDataPlane domainDataPlane;
    private final CredentialGatewayService credentialService;
    private final VerifyAttemptService verifyAttemptService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final JWTService jwtService;
    private final String rememberDeviceCookieName;

    public MFAChallengePostEndpoint(FactorManager factorManager,
                                    UserService userService,
                                    TemplateEngine engine,
                                    DeviceGatewayService deviceService,
                                    ApplicationContext applicationContext,
                                    DomainDataPlane domainDataPlane,
                                    CredentialGatewayService credentialService,
                                    VerifyAttemptService verifyAttemptService,
                                    EmailService emailService,
                                    AuditService auditService,
                                    DeviceIdentifierManager deviceIdentifierManager,
                                    JWTService jwtService,
                                    String rememberDeviceCookieName
    ) {
        super(engine);
        this.applicationContext = applicationContext;
        this.factorManager = factorManager;
        this.userService = userService;
        this.deviceService = deviceService;
        this.domainDataPlane = domainDataPlane;
        this.credentialService = credentialService;
        this.verifyAttemptService = verifyAttemptService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.jwtService = jwtService;
        this.rememberDeviceCookieName = rememberDeviceCookieName;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        verifyCode(routingContext);
    }

    private void verifyCode(RoutingContext routingContext) {
        final User endUser = user(routingContext);

        if (endUser == null) {
            logger.warn("User must be authenticated to submit MFA challenge.");
            routingContext.fail(401);
            return;
        }

        // check form inputs
        final MultiMap params = routingContext.request().formAttributes();
        final String code = params.get("code");
        final String factorId = params.get("factorId");
        if (code == null) {
            logger.warn("No code in form - did you forget to include code value ?");
            routingContext.fail(400);
            return;
        }
        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(400);
            return;
        }

        // create factor context
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final Factor factor = getFactor(routingContext, client, endUser);
        final FactorProvider factorProvider = factorManager.get(factorId);
        final FactorContext factorCtx = new FactorContext(applicationContext, new HashMap<>());
        final EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser, factorCtx, false);
        factorCtx.getData().putAll(getEvaluableAttributes(routingContext));
        factorCtx.registerData(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        factorCtx.registerData(FactorContext.KEY_CODE, code);
        factorCtx.registerData(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));

        if (factor.is(FIDO2)) {
            factorCtx.registerData(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, routingContext.session().get(PASSWORDLESS_CHALLENGE_KEY));
            factorCtx.registerData(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, routingContext.session().get(PASSWORDLESS_CHALLENGE_USERNAME_KEY));
            factorCtx.registerData(ConstantKeys.PASSWORDLESS_ORIGIN, domainDataPlane.getWebAuthnOrigin());
        }

        verifyAttemptService.checkVerifyAttempt(endUser, factorId, client, domainDataPlane.getDomain())
                .map(Optional::of)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .subscribe(verifyAttempt -> verify(factorProvider, factorCtx, verifyAttempt,
                                verifyHandler(routingContext, client, endUser, factor, code, factorId, factorProvider, enrolledFactor, factorCtx)),
                        error -> {
                            if (error instanceof MFAValidationAttemptException e) {
                                auditService.report(AuditBuilder.builder(VerifyAttemptAuditBuilder.class)
                                        .type(EventType.MFA_VERIFICATION_LIMIT_EXCEED)
                                        .verifyAttempt(e.getVerifyAttempt())
                                        .ipAddress(routingContext)
                                        .userAgent(routingContext)
                                        .client(client)
                                        .user(endUser));

                                if (verifyAttemptService.shouldSendEmail(client, domainDataPlane.getDomain())) {
                                    emailService.send(Template.VERIFY_ATTEMPT, endUser, client);
                                }
                                updateAuditLog(routingContext, MFA_MAX_ATTEMPT_REACHED, endUser, client, factor, factorCtx, error);
                                logger.warn("MFA verification limit reached for the user: {}", endUser.getUsername());
                                handleException(routingContext, VERIFY_ATTEMPT_ERROR_PARAM_KEY, "maximum_verify_limit");
                            } else {
                                logger.error("Could not check verify attempts", error);
                                routingContext.fail(401);
                            }
                        });
    }

    private Handler<AsyncResult<Void>> verifyHandler(RoutingContext routingContext,
                                                     Client client,
                                                     User endUser,
                                                     Factor factor,
                                                     String code,
                                                     String factorId,
                                                     FactorProvider factorProvider,
                                                     EnrolledFactor enrolledFactor,
                                                     FactorContext factorContext) {
        return h -> {
            final boolean enrolling = isEnrolling(enrolledFactor);
            if (h.failed()) {
                String failureReason = enrolling ? MFA_ENROLLMENT : MFA_CHALLENGE;
                updateAuditLog(routingContext, failureReason, endUser, client, factor, factorContext, h.cause());
                handleException(routingContext, ERROR_PARAM_KEY, MFA_CHALLENGE_FAILED);
                return;
            }

            if (factor.is(FIDO2)) {
                handleFido2Factor(routingContext, client, endUser, code, factor, factorContext, enrolledFactor, h);
                return;
            }
            // save enrolled factor if needed and redirect to the original url
            routingContext.session().put(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY, factorId);
            routingContext.session().remove(PREVIOUS_TRANSACTION_ID_KEY);
            if (enrolling || factorProvider.useVariableFactorSecurity(factorContext)) {
                enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                saveFactor(endUser, factorProvider.changeVariableFactorSecurity(enrolledFactor), fh -> {
                    if (fh.failed()) {
                        logger.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        updateAuditLog(routingContext, enrolling ? MFA_ENROLLMENT : MFA_CHALLENGE, endUser, client, factor, factorContext, fh.cause());
                        handleException(routingContext, ERROR_PARAM_KEY, MFA_CHALLENGE_FAILED);
                        return;
                    }

                    cleanSession(routingContext);
                    updateStrongAuthStatus(routingContext);
                    updateAuditLog(routingContext, enrolling ? MFA_ENROLLMENT : MFA_CHALLENGE, endUser, client, factor, factorContext, null);
                    saveDeviceAndContinue(routingContext, client, endUser);
                });
            } else {
                updateAuditLog(routingContext, MFA_CHALLENGE, endUser, client, factor, factorContext, null);
                updateStrongAuthStatus(routingContext);
                saveDeviceAndContinue(routingContext, client, endUser);
            }
            logger.debug("User {} strongly authenticated", endUser.getId());
        };
    }

    private void handleFido2Factor(RoutingContext routingContext, Client client, User endUser, String code,
                                   Factor factor, FactorContext factorContext, EnrolledFactor enrolledFactor, AsyncResult<Void> h) {
        final String userId = endUser.getId();
        final JsonObject webauthnResp = new JsonObject(code);
        final String credentialId = webauthnResp.getString("id");
        updateCredential(routingContext.request(), credentialId, userId, ch -> {
            final String auditLogType = isEnrolling(enrolledFactor) ? MFA_ENROLLMENT : MFA_CHALLENGE;
            if (ch.failed()) {
                final String username = routingContext.session().get(PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                logger.error("An error has occurred while updating credential for the user {}", username, h.cause());
                updateAuditLog(routingContext, auditLogType, endUser, client, factor, factorContext, h.cause());
                routingContext.fail(401);
                return;
            }


            updateStrongAuthStatus(routingContext);
            updateAuditLog(routingContext, auditLogType, endUser, client, factor, factorContext, null);
            logger.debug("User {} strongly authenticated", endUser.getId());
            // set the credentialId in session
            routingContext.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, credentialId);
            final Credential credential = ch.result();
            routingContext.session().put(WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY, credential.getId());

            if (userHasFido2Factor(endUser)) {
                cleanSession(routingContext);
                saveDeviceAndContinue(routingContext, client, endUser);
            } else {
                final String fidoFactorId = routingContext.session().get(ENROLLED_FACTOR_ID_KEY);
                userService.upsertFactor(endUser.getId(), createEnrolledFactor(fidoFactorId, credentialId), new DefaultUser(endUser))
                        .ignoreElement()
                        .subscribe(
                                () -> {
                                    cleanSession(routingContext);
                                    saveDeviceAndContinue(routingContext, client, endUser);
                                },
                                error -> {
                                    logger.error("Could not update user profile with FIDO2 factor detail", error);
                                    routingContext.fail(401);
                                }
                        );
            }
        });
    }

    private void verify(FactorProvider factorProvider, FactorContext factorContext, Optional<VerifyAttempt> verifyAttempt,
                        Handler<AsyncResult<Void>> handler) {
        factorProvider.verify(factorContext)
                .subscribe(
                        () -> {
                            if (verifyAttempt.isPresent()) {
                                verifyAttemptService.delete(verifyAttempt.get().getId()).subscribe(
                                        () -> handler.handle(Future.succeededFuture()),
                                        error -> logger.warn("Could not delete verify attempt", error)
                                );
                            } else {
                                handler.handle(Future.succeededFuture());
                            }
                        },
                        error -> {
                            logger.debug("Challenge failed for user {}", factorContext.getUser().getId());
                            final EnrolledFactor enrolledFactor = (EnrolledFactor) factorContext.getData().get(FactorContext.KEY_ENROLLED_FACTOR);
                            verifyAttemptService.incrementAttempt(factorContext.getUser().getId(), enrolledFactor.getFactorId(),
                                    factorContext.getClient(), domainDataPlane.getDomain(), verifyAttempt).subscribe(
                                    () -> handler.handle(Future.failedFuture(error)),
                                    verificationFailedError -> {
                                        logger.error("Could not updated verification failed status", verificationFailedError);
                                        handler.handle(Future.failedFuture(verificationFailedError));
                                    }
                            );
                        });
    }

    private void saveFactor(User user, Single<EnrolledFactor> enrolledFactor, Handler<AsyncResult<User>> handler) {
        enrolledFactor.flatMap(factor -> userService.upsertFactor(user.getId(), factor, new DefaultUser(user)))
                .subscribe(
                        user1 -> handler.handle(Future.succeededFuture(user1)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }


    private Factor getFactor(RoutingContext routingContext, Client client, User endUser) {
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

        return enrolledFactors
                .stream()
                .filter(e -> TRUE.equals(e.isPrimary()))
                .map(enrolledFactor -> factorManager.getFactor(enrolledFactor.getFactorId()))
                .findFirst()
                .orElse(factorManager.getFactor(enrolledFactors.get(0).getFactorId()));
    }

    private EnrolledFactor getEnrolledFactor(RoutingContext routingContext,
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

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private void handleException(RoutingContext context, String errorKey, String errorValue) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // redirect to mfa challenge page with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        Map<String, String> parameters = new LinkedHashMap<>(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put(errorKey, errorValue);
        if (context.session() != null) {
            context.session().put(ERROR_HASH, HashUtil.generateSHA256(errorValue));
        }
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters, true);
        doRedirect(resp, uri);
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private void saveDeviceAndContinue(RoutingContext routingContext, Client client, User user) {
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        boolean rememberDeviceConsent = REMEMBER_DEVICE_CONSENT_ON.equalsIgnoreCase(routingContext.request().getParam(REMEMBER_DEVICE_CONSENT));
        if (!rememberDeviceSettings.isActive() || !rememberDeviceConsent) {
            routingContext.next();
            return;
        }

        var deviceId = routingContext.session().<String>get(DEVICE_ID);
        var rememberDeviceId = rememberDeviceSettings.getDeviceIdentifierId();
        if (isNullOrEmpty(deviceId)) {
            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            routingContext.next();
        } else {
            this.deviceService.deviceExists(domainDataPlane.getDomain(), client.getClientId(), user.getFullId(), rememberDeviceId, deviceId).flatMapMaybe(isEmpty -> {
                if (Boolean.FALSE.equals(isEmpty)) {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
                    return Maybe.empty();
                }
                var deviceType = routingContext.session().<String>get(DEVICE_TYPE);
                return this.deviceService.create(
                                domainDataPlane.getDomain(),
                                client.getClientId(),
                                user.getFullId(),
                                rememberDeviceId,
                                isNullOrEmpty(deviceType) ? "Unknown" : deviceType,
                                rememberDeviceSettings.getExpirationTimeSeconds(), deviceId)
                        .concatMap(device -> {

                            if (deviceIdentifierManager.useCookieBasedDeviceIdentifier(client)) {

                                final var jwt = new JWT();

                                long now = System.currentTimeMillis() / 1000;
                                jwt.setIat(now);
                                jwt.setExp(now + rememberDeviceSettings.getExpirationTimeSeconds());
                                jwt.setJti(deviceId);

                                return jwtService.encode(jwt, client)
                                        .map(jwtValue -> {
                                            // create the cookie
                                            var cookie = Cookie.cookie(rememberDeviceCookieName, jwtValue)
                                                    .setHttpOnly(true)
                                                    .setMaxAge(rememberDeviceSettings.getExpirationTimeSeconds());
                                            routingContext.response().addCookie(cookie);
                                            // we do not want to continue with the JWT
                                            // return the device object to finalize the flow
                                            return device;
                                        });
                            }
                            return Single.just(device);
                        })
                        .doOnSuccess(device -> {
                            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
                            auditRememberDevice(user, client, null);
                        })
                        .doOnError(throwable -> {
                            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
                            auditRememberDevice(user, client, throwable);
                        })
                        .toMaybe();
            }).doFinally(() -> {
                        routingContext.session().remove(DEVICE_ID);
                        routingContext.session().remove(DEVICE_TYPE);
                    }
            ).subscribe(d -> routingContext.next());
        }
    }


    private void updateCredential(HttpServerRequest request, String credentialId, String userId, Handler<AsyncResult<Credential>> handler) {
        final Credential credential = new Credential();
        credential.setUserId(userId);
        credential.setUserAgent(RequestUtils.userAgent(request));
        credential.setIpAddress(RequestUtils.remoteAddress(request));

        credentialService.update(domainDataPlane.getDomain(), credentialId, credential)
                .subscribe(
                        updatedCredential -> handler.handle(Future.succeededFuture(updatedCredential)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void updateStrongAuthStatus(RoutingContext ctx) {
        ctx.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
        ctx.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
    }

    private void cleanSession(RoutingContext ctx) {
        ctx.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
        ctx.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);

        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY);
        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER);
        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER);
        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS);
    }

    private boolean userHasFido2Factor(User endUser) {
        if (endUser.getFactors() == null || endUser.getFactors().isEmpty()) {
            return false;
        }

        return endUser.getFactors()
                .stream()
                .map(EnrolledFactor::getSecurity)
                .filter(Objects::nonNull)
                .anyMatch(enrolledFactorSecurity -> WEBAUTHN_CREDENTIAL.equals(enrolledFactorSecurity.getType()));
    }

    private EnrolledFactor createEnrolledFactor(String factorId, String credentialId) {
        final EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setStatus(ACTIVATED);
        enrolledFactor.setCreatedAt(new Date());
        enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
        enrolledFactor.setSecurity(new EnrolledFactorSecurity(WEBAUTHN_CREDENTIAL, credentialId));

        return enrolledFactor;
    }

    private static boolean isEnrolling(EnrolledFactor enrolledFactor) {
        return enrolledFactor.getStatus() == PENDING_ACTIVATION;
    }

    private void updateAuditLog(RoutingContext routingContext, String type, User endUser, Client client, Factor factor, FactorContext factorContext, Throwable cause) {
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

    private void auditRememberDevice(User endUser, Client client, Throwable cause) {
        final MFAAuditBuilder auditBuilder = AuditBuilder.builder(MFAAuditBuilder.class)
                .user(endUser)
                .client(client)
                .type(EventType.MFA_REMEMBER_DEVICE)
                .application(client)
                .throwable(cause);

        auditService.report(auditBuilder);
    }
}
