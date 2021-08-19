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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.account.model.EnrollmentAccount;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountFactorsEndpointHandler {

    private AccountService accountService;
    private FactorManager factorManager;
    private ApplicationContext applicationContext;

    public AccountFactorsEndpointHandler(AccountService accountService,
                                         FactorManager factorManager,
                                         ApplicationContext applicationContext) {
        this.accountService = accountService;
        this.factorManager = factorManager;
        this.applicationContext = applicationContext;
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
                        factors -> AccountResponseHandler.handleDefaultResponse(routingContext, factors),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Get enrolled factors for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listEnrolledFactors(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final List<EnrolledFactor> enrolledFactors = user.getFactors() != null ? user.getFactors() : Collections.emptyList();
        AccountResponseHandler.handleDefaultResponse(routingContext, enrolledFactors);
    }

    /**
     * Enroll a factor for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void enrollFactor(RoutingContext routingContext) {
        try {
            if (routingContext.getBodyAsString() == null) {
                routingContext.fail(new InvalidRequestException("Unable to parse body message"));
                return;
            }

            final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            final io.gravitee.am.gateway.handler.account.model.Enrollment enrollment =
                    Json.decodeValue(routingContext.getBodyAsString(), io.gravitee.am.gateway.handler.account.model.Enrollment.class);

            // factorId is required
            if (StringUtils.isEmpty(enrollment.getFactorId())) {
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

                // check request body parameters
                if (FactorType.EMAIL.equals(factor.getFactorType())
                        && StringUtils.isEmpty(account.getEmail())) {
                    routingContext.fail(new InvalidRequestException("Field [email] is required"));
                    return;
                }

                if (FactorType.SMS.equals(factor.getFactorType())
                        && StringUtils.isEmpty(account.getPhoneNumber())) {
                    routingContext.fail(new InvalidRequestException("Field [phoneNumber] is required"));
                    return;
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
                            AccountResponseHandler.handleDefaultResponse(routingContext, existingEnrolledFactor);
                            return;
                        }
                    }
                }

                // enroll factor
                enrollFactor(factor, factorProvider, account, user, eh -> {
                    if (eh.failed()) {
                        routingContext.fail(eh.cause());
                        return;
                    }

                    final EnrolledFactor enrolledFactor = eh.result();
                    // send challenge
                    sendChallenge(factorProvider, enrolledFactor, user, routingContext, sh -> {
                        // save enrolled factor
                        accountService.upsertFactor(user.getId(), enrolledFactor, new DefaultUser(user))
                                .subscribe(
                                        __ -> AccountResponseHandler.handleDefaultResponse(routingContext, enrolledFactor),
                                        error -> routingContext.fail(error));
                    });
                });
            });


        } catch (DecodeException ex) {
            routingContext.fail(new InvalidRequestException("Unable to parse body message"));
        }
    }

    /**
     * Verify a factor for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void verifyFactor(RoutingContext routingContext) {
        try {
            if (routingContext.getBodyAsString() == null) {
                routingContext.fail(new InvalidRequestException("Unable to parse body message"));
                return;
            }

            final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            final String factorId = routingContext.request().getParam("factorId");
            final String code = routingContext.getBodyAsJson().getString("code");

            // code is required
            if (StringUtils.isEmpty(code)) {
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

                if (!optionalEnrolledFactor.isPresent()) {
                    routingContext.fail(new FactorNotFoundException(factorId));
                    return;
                }

                // if factor is already activated, continue
                final EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
                if (FactorStatus.ACTIVATED.equals(enrolledFactor.getStatus())) {
                    AccountResponseHandler.handleDefaultResponse(routingContext, enrolledFactor);
                    return;
                }

                // verify factor
                verifyFactor(code, enrolledFactor, factorProvider, vh -> {
                    if (vh.failed()) {
                        routingContext.fail(vh.cause());
                        return;
                    }

                    // verify successful, change the EnrolledFactor Status
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    accountService.upsertFactor(user.getId(), enrolledFactor, new DefaultUser(user))
                            .subscribe(
                                    __ -> AccountResponseHandler.handleDefaultResponse(routingContext, enrolledFactor),
                                    error -> routingContext.fail(error)
                            );
                });
            });
        } catch (DecodeException ex) {
            routingContext.fail(new InvalidRequestException("Unable to parse body message"));
        }
    }

    /**
     * Get enrolled factor detail for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledFactor(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam("factorId");

        if (user.getFactors() == null) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        final Optional<EnrolledFactor> optionalEnrolledFactor = getEnrolledFactor(factorId, user);

        if (!optionalEnrolledFactor.isPresent()) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }
        AccountResponseHandler.handleDefaultResponse(routingContext, optionalEnrolledFactor.get());
    }

    /**
     *
     * @param routingContext
     */
    public void getEnrolledFactorQrCode(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam("factorId");

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

        if (!optionalEnrolledFactor.isPresent()) {
            routingContext.fail(new FactorNotFoundException(factorId));
            return;
        }

        EnrolledFactor enrolledFactor = optionalEnrolledFactor.get();
        factorProvider.generateQrCode(user, enrolledFactor)
                .subscribe(
                        barCode -> AccountResponseHandler.handleDefaultResponse(routingContext, new JsonObject().put("qrCode", barCode)),
                        error -> routingContext.fail(error),
                        () -> routingContext.fail(404)
                );
    }

    public void removeFactor(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String factorId = routingContext.request().getParam("factorId");

        accountService.removeFactor(user.getId(), factorId, new DefaultUser(user))
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        error -> routingContext.fail(error)
                );
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
                              User endUser,
                              Handler<AsyncResult<EnrolledFactor>> handler) {
        factorProvider.enroll(endUser.getUsername())
                .map(enrollment -> buildEnrolledFactor(factor, enrollment, account, endUser))
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

        Map<String, Object> factorData = new HashMap<>();
        factorData.putAll(getEvaluableAttributes(routingContext));
        factorData.put(FactorContext.KEY_CLIENT, routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        factorData.put(FactorContext.KEY_USER, endUser);
        factorData.put(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        FactorContext factorContext = new FactorContext(applicationContext, factorData);

        factorProvider.sendChallenge(factorContext)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private EnrolledFactor buildEnrolledFactor(Factor factor,
                                               Enrollment enrollment,
                                               EnrollmentAccount account,
                                               User user) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factor.getId());
        enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
        switch (factor.getFactorType()) {
            case OTP:
                enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, enrollment.getKey()));
                break;
            case SMS:
                enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, account.getPhoneNumber()));
                break;
            case EMAIL:
                Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                        .put(FactorDataKeys.KEY_MOVING_FACTOR, generateInitialMovingFactor(user))
                        .build();
                // For email even if the endUser will contain all relevant information, we extract only the Expiration Date of the code.
                // this is done only to enforce the other parameter (shared secret and initialMovingFactor)
                getEnrolledFactor(factor.getId(), user).ifPresent(ef -> {
                    additionalData.put(FactorDataKeys.KEY_EXPIRE_AT, ef.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class));
                });
                enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, enrollment.getKey(), additionalData));
                enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, account.getEmail()));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + factor.getFactorType().getType());
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

    private int generateInitialMovingFactor(User endUser) {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(endUser.getUsername().getBytes(StandardCharsets.UTF_8));
            return secureRandom.nextInt(1000) + 1;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }
}
