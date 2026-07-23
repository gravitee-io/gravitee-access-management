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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.service.mfa.VerifyAttemptService;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.MFAValidationAttemptException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.MFAAuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.VerifyAttemptAuditBuilder;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE;
import static io.gravitee.am.common.audit.EventType.MFA_ENROLLMENT;
import static io.gravitee.am.common.audit.EventType.MFA_MAX_ATTEMPT_REACHED;
import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_TYPE;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_FAILED;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.VERIFY_ATTEMPT_ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.util.Optional.ofNullable;
import lombok.CustomLog;

@CustomLog
public class MFAChallengePostEndpoint extends MFAChallengeEndpoint {

    private static final String REMEMBER_DEVICE_CONSENT = "rememberDeviceConsent";
    public static final String REMEMBER_DEVICE_CONSENT_ON = "on";

    private final UserService userService;
    private final DeviceGatewayService deviceService;
    private final CredentialGatewayService credentialService;
    private final VerifyAttemptService verifyAttemptService;
    private final EmailService emailService;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final JWTService jwtService;
    private final String rememberDeviceCookieName;

    public MFAChallengePostEndpoint(FactorManager factorManager,
                                    UserService userService,
                                    TemplateEngine engine,
                                    DeviceGatewayService deviceService,
                                    ApplicationContext applicationContext,
                                    DomainDataPlane domainDataPlane,
                                    RateLimiterService rateLimiterService,
                                    CredentialGatewayService credentialService,
                                    VerifyAttemptService verifyAttemptService,
                                    EmailService emailService,
                                    AuditService auditService,
                                    DeviceIdentifierManager deviceIdentifierManager,
                                    JWTService jwtService,
                                    String rememberDeviceCookieName
    ) {
        super(factorManager, engine, applicationContext, domainDataPlane, rateLimiterService, auditService);
        this.userService = userService;
        this.deviceService = deviceService;
        this.credentialService = credentialService;
        this.verifyAttemptService = verifyAttemptService;
        this.emailService = emailService;
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
            log.warn("User must be authenticated to submit MFA challenge.");
            routingContext.fail(401);
            return;
        }

        // check form inputs
        final MultiMap params = routingContext.request().formAttributes();
        final String code = params.get("code");
        final String factorId = params.get("factorId");
        if (code == null) {
            log.warn("No code in form - did you forget to include code value ?");
            routingContext.fail(400);
            return;
        }
        if (factorId == null) {
            log.warn("No factor id in form - did you forget to include factor id value ?");
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
                                log.warn("MFA verification limit reached for the user: {}", endUser.getUsername());
                                handleException(routingContext, VERIFY_ATTEMPT_ERROR_PARAM_KEY, "maximum_verify_limit");
                            } else {
                                log.error("Could not check verify attempts", error);
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
                        log.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        updateAuditLog(routingContext, enrolling ? MFA_ENROLLMENT : MFA_CHALLENGE, endUser, client, factor, factorContext, fh.cause());
                        handleException(routingContext, ERROR_PARAM_KEY, MFA_CHALLENGE_FAILED);
                        return;
                    }

                    routingContext.put(ConstantKeys.USER_CONTEXT_KEY, fh.result());
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
            log.debug("User {} strongly authenticated", endUser.getId());
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
                log.error("An error has occurred while updating credential for the user {}", username, h.cause());
                updateAuditLog(routingContext, auditLogType, endUser, client, factor, factorContext, h.cause());
                routingContext.fail(401);
                return;
            }


            updateStrongAuthStatus(routingContext);
            updateAuditLog(routingContext, auditLogType, endUser, client, factor, factorContext, null);
            log.debug("User {} strongly authenticated", endUser.getId());
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
                                    log.error("Could not update user profile with FIDO2 factor detail", error);
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
                                        error -> log.warn("Could not delete verify attempt", error)
                                );
                            } else {
                                handler.handle(Future.succeededFuture());
                            }
                        },
                        error -> {
                            log.debug("Challenge failed for user {}", factorContext.getUser().getId());
                            final EnrolledFactor enrolledFactor = (EnrolledFactor) factorContext.getData().get(FactorContext.KEY_ENROLLED_FACTOR);
                            verifyAttemptService.incrementAttempt(factorContext.getUser().getId(), enrolledFactor.getFactorId(),
                                    factorContext.getClient(), domainDataPlane.getDomain(), verifyAttempt).subscribe(
                                    () -> handler.handle(Future.failedFuture(error)),
                                    verificationFailedError -> {
                                        log.error("Could not updated verification failed status", verificationFailedError);
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
        ctx.session().remove(ConstantKeys.MFA_CHALLENGE_SENT_FACTOR_ID_KEY);
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
