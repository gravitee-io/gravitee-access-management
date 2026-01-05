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
package io.gravitee.am.gateway.handler.account.resources;

import io.gravitee.am.common.exception.mfa.InvalidFactorAttributeException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.api.RecoveryFactor;
import io.gravitee.am.gateway.handler.account.model.EnrollmentAccount;
import io.gravitee.am.gateway.handler.account.model.UpdateEnrolledFactor;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.am.service.exception.RateLimitException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.MFAAuditBuilder;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE;
import static io.gravitee.am.common.audit.EventType.MFA_CHALLENGE_SENT;
import static io.gravitee.am.common.audit.EventType.MFA_ENROLLMENT;
import static io.gravitee.am.common.audit.EventType.MFA_RATE_LIMIT_REACHED;
import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.factor.api.FactorContext.KEY_USER;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountFactorsEndpointHandler {

    private final AccountService accountService;
    private final FactorManager factorManager;
    private final ApplicationContext applicationContext;
    private final RateLimiterService rateLimiterService;
    private final AuditService auditService;

    private static final String FACTOR_ID = "factorId";

    public AccountFactorsEndpointHandler(AccountService accountService,
                                         FactorManager factorManager,
                                         ApplicationContext applicationContext,
                                         RateLimiterService rateLimiterService, AuditService auditService) {
        this.accountService = accountService;
        this.factorManager = factorManager;
        this.applicationContext = applicationContext;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
    }

    /**
     * List security domain factors
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listAvailableFactors(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        accountService.getFactors(user.getReferenceId())
                .subscribe(
                        factors -> AccountResponseHandler.handleDefaultResponse(routingContext, filteredFactorCatalog(factors)),
                        routingContext::fail
                );
    }

    /**
     * Get enrolled factors for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listEnrolledFactors(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final List<EnrolledFactor> enrolledFactors = user.getFactors() != null ? filteredEnrolledFactors(user) : Collections.emptyList();
        enrolledFactors.forEach(this::sanitizeEnrolledFactor);
        AccountResponseHandler.handleDefaultResponse(routingContext, enrolledFactors);
    }

    /**
     * Enroll a factor for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void enrollFactor(RoutingContext routingContext) {
        try {
            if (!validBody(routingContext)) {
                return;
            }
            final io.gravitee.am.gateway.handler.account.model.Enrollment enrollment = routingContext.body().asPojo(io.gravitee.am.gateway.handler.account.model.Enrollment.class);
            if (enrollment == null) {
                routingContext.fail(new InvalidRequestException("Unable to parse body message"));
                return;
            }

            final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);

            // factorId is required
            if (!StringUtils.hasText(enrollment.getFactorId())) {
                routingContext.fail(new InvalidRequestException("Field [factorId] is required"));
                return;
            }

            final String factorId = enrollment.getFactorId();
            final EnrollmentAccount account = enrollment.getAccount();

            // find factor
            findFactor(factorId, h -> {
                if (h.failed()) {
                    routingContext.fail(h.cause());
                    return;
                }

                final Factor factor = h.result();
                final FactorProvider factorProvider = factorManager.get(factorId);

                if (factorProvider == null) {
                    routingContext.fail(new FactorNotFoundException(factorId));
                    return;
                }

                if (isRecoveryCodeFactor(factor)) {
                    routingContext.fail(new InvalidRequestException("Recovery code does not support enrollment feature. Instead, use '/api/recovery_code' endpoint to generate recovery code."));
                    return;
                }

                // check request body parameters
                switch (factor.getFactorType()) {
                    case CALL:
                    case SMS:
                        if (isNull(account) || !StringUtils.hasText(account.getPhoneNumber())) {
                            routingContext.fail(new InvalidRequestException("Field [phoneNumber] is required"));
                            return;
                        }
                        break;
                    case EMAIL:
                        if (isNull(account) || !StringUtils.hasText(account.getEmail())) {
                            routingContext.fail(new InvalidRequestException("Field [email] is required"));
                            return;
                        }
                        break;
                    default:
                        //Do nothing
                        break;
                }


                // check if the current factor is already enrolled
                if (user.getFactors() != null) {
                    Optional<EnrolledFactor> optionalEnrolledFactor = user.getFactors()
                            .stream()
                            .filter(enrolledFactor -> factorId.equals(enrolledFactor.getFactorId()))
                            .findFirst();

                    if (optionalEnrolledFactor.isPresent()) {
                        EnrolledFactor existingEnrolledFactor = optionalEnrolledFactor.get();
                        if (FactorStatus.ACTIVATED.equals(existingEnrolledFactor.getStatus())) {
                            AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(existingEnrolledFactor));
                            return;
                        }
                    }
                }

                // enroll factor
                final FactorContext factorContext = new FactorContext(applicationContext, new HashMap<>());
                factorContext.registerData(KEY_USER, user);
                enrollFactor(factor, factorProvider, account, factorContext, eh -> {
                    if (eh.failed()) {
                        if (eh.cause() instanceof InvalidFactorAttributeException) {
                            routingContext.fail(400, eh.cause());
                        } else {
                            routingContext.fail(eh.cause());
                        }
                        return;
                    }

                    final EnrolledFactor enrolledFactor = eh.result();
                    // send challenge
                    sendChallenge(factorProvider, enrolledFactor, user, routingContext, sh -> {
                        if(sh.failed()){
                            routingContext.fail(sh.cause());
                            return;
                        }
                        // save enrolled factor
                        accountService.upsertFactor(user.getId(), enrolledFactor, new DefaultUser(user))
                                .subscribe(
                                        __ -> AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(enrolledFactor)),
                                        routingContext::fail);
                    });
                });
            });

        } catch (DecodeException ex) {
            routingContext.fail(invalidBodyException());
        }
    }

    private Completable generateRecoveryCode(RoutingContext routingContext, Factor factor, RecoveryFactor factorProvider) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final Map<String, Object> factorData = Map.of(
                FactorContext.KEY_RECOVERY_FACTOR, factor,
                KEY_USER, user);
        final FactorContext recoveryFactorCtx = new FactorContext(applicationContext, factorData);

        return factorProvider.generateRecoveryCode(recoveryFactorCtx).ignoreElement();
    }

    private boolean isRecoveryCodeFactor(Factor factor) {
        return FactorType.RECOVERY_CODE.equals(factor.getFactorType());
    }

    /**
     * Verify a factor for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void verifyFactor(RoutingContext routingContext) {
        try {
            if (!validBody(routingContext)) {
                return;
            }
            final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final String factorId = routingContext.request().getParam(FACTOR_ID);
            final String code = routingContext.body().asJsonObject().getString("code");

            // code is required
            if (!StringUtils.hasText(code)) {
                routingContext.fail(new InvalidRequestException("Field [code] is required"));
                return;
            }

            // find factor
            findFactor(factorId, h -> {
                if (h.failed()) {
                    routingContext.fail(h.cause());
                    return;
                }

                final FactorProvider factorProvider = factorManager.get(factorId);

                if (factorProvider == null) {
                    routingContext.fail(new FactorNotFoundException(factorId));
                    return;
                }

                // get enrolled factor for the current user
                Optional<EnrolledFactor> optionalEnrolledFactor = user.getFactors()
                        .stream()
                        .filter(enrolledFactor -> factorId.equals(enrolledFactor.getFactorId()))
                        .findFirst();

                if (optionalEnrolledFactor.isEmpty()) {
                    routingContext.fail(new FactorNotFoundException(factorId));
                    return;
                }

                // verify factor
                final EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
                final String auditLogType = enrolledFactor.getStatus() == PENDING_ACTIVATION ? MFA_ENROLLMENT : MFA_CHALLENGE;
                verifyFactor(code, enrolledFactor, factorProvider, vh -> {
                    if (vh.failed()) {
                        updateAuditLog(routingContext, auditLogType, user, client, h.result(), enrolledFactor, vh.cause());
                        routingContext.fail(vh.cause());
                        return;
                    }

                    // verify successful, change the EnrolledFactor status and increment moving factor
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    updateAuditLog(routingContext, auditLogType, user, client, h.result(), enrolledFactor, null);

                    factorProvider.changeVariableFactorSecurity(enrolledFactor)
                            .flatMap(eF -> accountService.upsertFactor(user.getId(), eF, new DefaultUser(user)).map(__ -> eF))
                            .subscribe(
                                    eF -> AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(eF)),
                                    routingContext::fail
                            );
                });
            });
        } catch (DecodeException ex) {
            routingContext.fail(invalidBodyException());
        }
    }

    /**
     * Get enrolled factor detail for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledFactor(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam(FACTOR_ID);

        if (user.getFactors() == null) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        final Optional<EnrolledFactor> optionalEnrolledFactor = getEnrolledFactor(factorId, user);

        if (optionalEnrolledFactor.isEmpty()) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        // Remove recovery code from enrolled factor
        EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
        if (RECOVERY_CODE.equals(enrolledFactor.getSecurity().getType())) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }
        AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(enrolledFactor));
    }

    /**
     * Get QR code for the selected enrolled factor (TOTP only)
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledFactorQrCode(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam(FACTOR_ID);

        final FactorProvider factorProvider = factorManager.get(factorId);

        if (factorProvider == null) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        if (user.getFactors() == null) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        Optional<EnrolledFactor> optionalEnrolledFactor = getEnrolledFactor(factorId, user);

        if (optionalEnrolledFactor.isEmpty()) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
        factorProvider.generateQrCode(user, enrolledFactor)
                .subscribe(
                        barCode -> AccountResponseHandler.handleDefaultResponse(routingContext, new JsonObject().put("qrCode", barCode)),
                        routingContext::fail,
                        () -> routingContext.fail(404)
                );
    }

    /**
     * Get shared secret code for the selected enrolled factor (TOTP only)
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledFactorSharedSecretCode(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam(FACTOR_ID);

        if (user.getFactors() == null) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        Optional<EnrolledFactor> optionalEnrolledFactor = getEnrolledFactor(factorId, user);

        if (optionalEnrolledFactor.isEmpty()) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
        if (SHARED_SECRET.equals(enrolledFactor.getSecurity().getType())) {
            AccountResponseHandler.handleDefaultResponse(routingContext, new JsonObject().put("sharedSecret", enrolledFactor.getSecurity().getValue()));
        } else {
            routingContext.fail(new FactorNotFoundException(factorId));
        }
    }

    public void updateEnrolledFactor(RoutingContext routingContext) {
        try {
            if (!validBody(routingContext)) {
                return;
            }
            final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            final String factorId = routingContext.request().getParam(FACTOR_ID);
            final UpdateEnrolledFactor updateEnrolledFactor = Json.decodeValue(routingContext.body().asString(), UpdateEnrolledFactor.class);

            // find factor
            findFactor(factorId, h -> {
                if (h.failed()) {
                    routingContext.fail(h.cause());
                    return;
                }

                // get enrolled factor for the current user
                Optional<EnrolledFactor> optionalEnrolledFactor = user.getFactors()
                        .stream()
                        .filter(enrolledFactor -> factorId.equals(enrolledFactor.getFactorId()))
                        .findFirst();

                if (optionalEnrolledFactor.isEmpty()) {
                    routingContext.fail(new FactorNotFoundException(factorId));
                    return;
                }

                // update the factor
                final EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
                enrolledFactor.setPrimary(updateEnrolledFactor.isPrimary());
                accountService.upsertFactor(user.getId(), enrolledFactor, new DefaultUser(user))
                        .subscribe(
                                __ -> AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(enrolledFactor)),
                                routingContext::fail
                        );
            });
        } catch (DecodeException ex) {
            routingContext.fail(invalidBodyException());
        }
    }

    public void removeFactor(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam(FACTOR_ID);

        accountService.removeFactor(user, factorId, new DefaultUser(user))
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        routingContext::fail
                );
    }

    /**
     * List recovery codes for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listRecoveryCodes(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);

        if (user.getFactors() == null) {
            AccountResponseHandler.handleDefaultResponse(routingContext, Collections.emptyList());
        } else {
            AccountResponseHandler.handleDefaultResponse(routingContext, getUserRecoveryCodes(user));
        }
    }

    /**
     * Enroll user to recovery code factor and generate recovery code
     * in the process
     *
     * @param routingContext the routingContext holding the current user
     */
    public void enrollRecoveryCode(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final Factor recoveryCodeFactor = getClientRecoveryCodeFactor(client);

        if (recoveryCodeFactor == null) {
            routingContext.fail(new InvalidRequestException(client.getClientName() + " does not support recovery code. Please ask your administrator for further information."));
            return;
        }

        final RecoveryFactor recoveryCodeFactorProvider = (RecoveryFactor) factorManager.get(recoveryCodeFactor.getId());

        generateRecoveryCode(routingContext, recoveryCodeFactor, recoveryCodeFactorProvider).subscribe(
                () -> {
                    final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
                    //Need updated user data after recovery code generation, hence the accountService call
                    accountService.getByUserId(user.getId()).subscribe(
                            usr -> AccountResponseHandler.handleDefaultResponse(routingContext, getUserRecoveryCodes(usr)),
                            routingContext::fail
                    );
                },
                routingContext::fail
        );
    }

    /**
     * Delete user recovery codes
     *
     * @param routingContext the routingContext holding the current user
     */
    public void deleteRecoveryCode(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);

        if (user.getFactors() == null) {
            AccountResponseHandler.handleNoBodyResponse(routingContext);
            return;
        }

        if (user.getFactors().isEmpty()) {
            AccountResponseHandler.handleNoBodyResponse(routingContext);
            return;
        }

        final List<String> recoveryCodes = user.getFactors()
                .stream()
                .filter(ef -> ef.getSecurity() != null && RECOVERY_CODE.equals(ef.getSecurity().getType()))
                .map(EnrolledFactor::getFactorId)
                .collect(Collectors.toList());

        if (recoveryCodes.isEmpty()) {
            AccountResponseHandler.handleNoBodyResponse(routingContext);
            return;
        }

        Observable.fromIterable(recoveryCodes)
                .flatMapCompletable(recoveryCode -> accountService.removeFactor(user, recoveryCode, new DefaultUser(user)))
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        routingContext::fail
                );
    }

    /**
     * Issue a challenge for the selected factor (SMS, Email mainly)
     *
     * @param routingContext the routingContext holding the current user
     */
    public void sendChallenge(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam(FACTOR_ID);

        // find factor
        findFactor(factorId, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }

            final FactorProvider factorProvider = factorManager.get(factorId);
            if (factorProvider == null) {
                routingContext.fail(new FactorNotFoundException(factorId));
                return;
            }

            if (!factorProvider.needChallengeSending()) {
                routingContext.fail(new InvalidRequestException("Invalid factor"));
                return;
            }

            // get enrolled factor for the current user
            Optional<EnrolledFactor> optionalEnrolledFactor = user.getFactors()
                    .stream()
                    .filter(enrolledFactor -> factorId.equals(enrolledFactor.getFactorId()))
                    .findFirst();

            if (optionalEnrolledFactor.isEmpty()) {
                routingContext.fail(new FactorNotFoundException(factorId));
                return;
            }

            final EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
            sendChallenge(factorProvider, enrolledFactor, user, routingContext, sh -> {
                if (sh.failed()) {
                    routingContext.fail(sh.cause());
                    return;
                }
                // challenge has been sent, respond with OK status
                AccountResponseHandler.handleDefaultResponse(routingContext, sanitizeEnrolledFactor(enrolledFactor));
            });
        });
    }

    private List<String> getUserRecoveryCodes(User user) {
        final Optional<Object> securityCodes = user.getFactors()
                .stream()
                .filter(ef -> ef.getSecurity() != null && RECOVERY_CODE.equals(ef.getSecurity().getType()))
                .map(EnrolledFactor::getSecurity)
                .map(security -> security.getAdditionalData().get(RECOVERY_CODE))
                .findFirst();

        return securityCodes.map(codes -> (List<String>) codes).orElse(Collections.emptyList());
    }

    private Factor getClientRecoveryCodeFactor(Client client) {
        return Optional.ofNullable(client.getFactorSettings())
                .map(FactorSettings::getApplicationFactors)
                .flatMap(appFactorSettings -> appFactorSettings.stream()
                        .map(settings -> factorManager.getFactor(settings.getId()))
                        .filter(this::isRecoveryCodeFactor)
                        .findFirst())
                .orElse(null);
    }

    private void findFactor(String factorId, Handler<AsyncResult<Factor>> handler) {
        accountService.getFactor(factorId)
                .subscribe(
                        factor -> handler.handle(Future.succeededFuture(factor)),
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.failedFuture(new FactorNotFoundException(factorId))));
    }

    private void enrollFactor(Factor factor,
                              FactorProvider factorProvider,
                              EnrollmentAccount account,
                              FactorContext factorContext,
                              Handler<AsyncResult<EnrolledFactor>> handler) {
        factorProvider.enroll(factorContext)
                .map(enrollment -> {
                    final EnrolledFactor enrolledFactor = buildEnrolledFactor(factor, factorProvider, enrollment, account, factorContext);
                    if (factorProvider.checkSecurityFactor(enrolledFactor)) {
                        return enrolledFactor;
                    }
                    throw new InvalidFactorAttributeException("Invalid account information");
                })
                .subscribe(
                        enrolledFactor -> handler.handle(Future.succeededFuture(enrolledFactor)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void verifyFactor(String code,
                              EnrolledFactor enrolledFactor,
                              FactorProvider factorProvider,
                              Handler<AsyncResult<Void>> handler) {
        Map<String, Object> factorData = new HashMap<>();
        factorData.put(FactorContext.KEY_CODE, code);
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        factorProvider.verify(new FactorContext(applicationContext, factorData))
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void sendChallenge(FactorProvider factorProvider,
                               EnrolledFactor enrolledFactor,
                               User endUser,
                               RoutingContext routingContext,
                               Handler<AsyncResult<Void>> handler) {
        if (!factorProvider.needChallengeSending()) {
            handler.handle(Future.succeededFuture());
            return;
        }

        Map<String, Object> factorData = new HashMap<>(getEvaluableAttributes(routingContext));
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        factorData.put(FactorContext.KEY_CLIENT, client);
        factorData.put(FactorContext.KEY_USER, endUser);
        factorData.put(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        FactorContext factorContext = new FactorContext(applicationContext, factorData);
        final Factor factor = factorManager.getFactor(enrolledFactor.getFactorId());

        if (rateLimiterService.isRateLimitEnabled()) {
            rateLimiterService.tryConsume(endUser.getId(), factor.getId(), client.getId(), client.getDomain())
                    .subscribe(allowRequest -> {
                                if (allowRequest) {
                                    sendChallenge(routingContext, factorProvider, factorContext, endUser, client, enrolledFactor, factor, handler);
                                } else {
                                    RateLimitException exception = new RateLimitException("MFA rate limit reached");
                                    updateAuditLog(routingContext, MFA_RATE_LIMIT_REACHED, endUser, client, factor, enrolledFactor, exception);
                                    handler.handle(Future.failedFuture(exception));
                                }
                            },
                            error -> handler.handle(Future.failedFuture(error))
                    );
        } else {
            sendChallenge(routingContext, factorProvider, factorContext, endUser, client, enrolledFactor, factor, handler);
        }
    }

    private void sendChallenge(RoutingContext routingContext, FactorProvider factorProvider, FactorContext factorContext, User user, Client client,
                               EnrolledFactor enrolledFactor, Factor factor, Handler<AsyncResult<Void>> handler) {
        factorProvider.sendChallenge(factorContext)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> {
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, user, client, factor, enrolledFactor, null);
                            handler.handle(Future.succeededFuture());
                        },
                        error ->
                        {
                            updateAuditLog(routingContext, MFA_CHALLENGE_SENT, user, client, factor, enrolledFactor, error);
                            handler.handle(Future.failedFuture(error));
                        }
                );
    }

    private EnrolledFactor buildEnrolledFactor(Factor factor,
                                               FactorProvider factorProvider,
                                               Enrollment enrollment,
                                               EnrollmentAccount account,
                                               FactorContext factorContext) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factor.getId());
        enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
        switch (factor.getFactorType()) {
            case OTP:
                enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, enrollment.getKey()));
                break;
            case SMS:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.SMS, account.getPhoneNumber()));
                break;
            case CALL:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.CALL, account.getPhoneNumber()));
                break;
            case EMAIL:
                enrolledFactor.setChannel(new EnrolledFactorChannel(Type.EMAIL, account.getEmail()));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + factor.getFactorType().getType());
        }

        // add security information (shared secret and moving factor) for factor managed by AM to generate OTP
        if (factorProvider.useVariableFactorSecurity(factorContext)) {
            final User user = factorContext.getUser();
            Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                    .put(FactorDataKeys.KEY_MOVING_FACTOR, MovingFactorUtils.generateInitialMovingFactor(user.getId()))
                    .build();
            getEnrolledFactor(factor.getId(), user).ifPresent(ef -> {
                additionalData.put(FactorDataKeys.KEY_EXPIRE_AT, ef.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class));
            });
            enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, enrollment.getKey(), additionalData));
        }

        if (account != null && account.getExtensionPhoneNumber() != null && enrolledFactor.getChannel() != null) {
            var additionalData = ofNullable(enrolledFactor.getChannel().getAdditionalData()).map(HashMap::new).orElse(new HashMap<>());
            additionalData.put(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER, account.getExtensionPhoneNumber());
            enrolledFactor.getChannel().setAdditionalData(additionalData);
        }

        enrolledFactor.setCreatedAt(new Date());
        enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
        return enrolledFactor;
    }

    private Optional<EnrolledFactor> getEnrolledFactor(String factorId, User user) {
        if (user.getFactors() == null) {
            return Optional.empty();
        }

        return user.getFactors()
                .stream()
                .filter(factor -> Objects.equals(factorId, factor.getFactorId()))
                .findFirst();
    }

    /**
     * This method filter out recovery code factor
     *
     * @param user current user in the context
     * @return list of EnrolledFactor without recovery codes
     */
    private List<EnrolledFactor> filteredEnrolledFactors(User user) {
        return user.getFactors()
                .stream()
                .filter(ef -> ef.getSecurity() == null || !RECOVERY_CODE.equals(ef.getSecurity().getType()))
                .collect(Collectors.toList());
    }

    /**
     * This method filter out recovery code factor
     *
     * @param factors list of Factor objects
     * @return list of Factor without recovery codes
     */
    private List<Factor> filteredFactorCatalog(List<Factor> factors) {
        return factors
                .stream()
                .filter(factor -> !FactorType.RECOVERY_CODE.equals(factor.getFactorType()))
                .collect(Collectors.toList());
    }

    private void updateAuditLog(RoutingContext routingContext, String type, User endUser, Client client, Factor factor, EnrolledFactor enrolledFactor, Throwable cause) {
        final EnrolledFactorChannel channel = enrolledFactor.getChannel();

        final MFAAuditBuilder builder = AuditBuilder.builder(MFAAuditBuilder.class)
                .user(endUser)
                .factor(factor)
                .type(type)
                .client(client)
                .domainFrom(client)
                .channel(channel)
                .ipAddress(routingContext)
                .userAgent(routingContext)
                .throwable(cause, channel);

        auditService.report(builder);
    }

    private boolean validBody(RoutingContext routingContext) {
        if (routingContext.body().asString() == null) {
            routingContext.fail(new InvalidRequestException("Unable to parse body message"));
            return false;
        }
        return true;
    }

    private InvalidRequestException invalidBodyException() {
        throw new InvalidRequestException("Unable to parse body message");
    }

    private EnrolledFactor sanitizeEnrolledFactor(EnrolledFactor enrolledFactor) {
        enrolledFactor.setSecurity(null);
        return enrolledFactor;
    }
}
