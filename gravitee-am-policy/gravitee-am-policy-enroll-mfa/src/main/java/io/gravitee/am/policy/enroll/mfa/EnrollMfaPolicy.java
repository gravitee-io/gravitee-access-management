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
package io.gravitee.am.policy.enroll.mfa;

import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.policy.enroll.mfa.configuration.EnrollMfaPolicyConfiguration;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class EnrollMfaPolicy {

    static final String GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY = "GATEWAY_POLICY_ENROLL_MFA_ERROR";
    private static Set<FactorType> UPDATABLE_FACTORS = Set.of(FactorType.SMS, FactorType.EMAIL, FactorType.CALL);
    private final EnrollMfaPolicyConfiguration configuration;

    public EnrollMfaPolicy(EnrollMfaPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        log.debug("Start enroll MFA policy");
        final String factorId = configuration.getFactorId();
        final String value = configuration.getValue();

        if (ObjectUtils.isEmpty(factorId)) {
            log.warn("No factor ID configured for the enroll MFA policy");
            policyChain.doNext(request, response);
            return;
        }

        try {
            final UserGatewayService userService = context.getComponent(UserGatewayService.class);
            final User user = (User) context.getAttribute(ConstantKeys.USER_CONTEXT_KEY);
            final Client client = (Client) context.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY);
            final FactorManager factorManager = context.getComponent(FactorManager.class);
            final Optional<Factor> optFactor = factorManager.getClientFactor(client, factorId);

            if (optFactor.isEmpty()) {
                log.warn("No active MFA factor with ID [{}] found", factorId);
                policyChain.doNext(request, response);
                return;
            }

            if (user == null) {
                log.warn("No user found in context");
                policyChain.doNext(request, response);
                return;
            }

            final Factor factor = optFactor.get();
            if (!ObjectUtils.isEmpty(user.getFactors())) {
                boolean factorAlreadyExist = user.getFactors().stream().anyMatch(enrolledFactor -> enrolledFactor.getFactorId().equals(factorId));
                if (!configuration.isRefresh() && factorAlreadyExist) {
                    // no need to enroll the same factor
                    log.debug("MFA factor with ID [{}] already enrolled for the current user", factorId);
                    policyChain.doNext(request, response);
                    return;
                } else if (configuration.isRefresh() && factorAlreadyExist) {
                    // factor already exist but may need to be updated
                    log.debug("MFA factor with ID [{}] already enrolled for the current user, update the factor information", factorId);
                    var existingFactorOpt = user.getFactors().stream().filter(enrolledFactor -> enrolledFactor.getFactorId().equals(factorId)).findFirst();
                    refreshEnrolledFactor(existingFactorOpt.get(), factor, value, context)
                            .flatMapSingle(enrolledFactor -> userService.updateFactor(user.getId(), enrolledFactor, new DefaultUser(user)).map(Optional::ofNullable))
                            .switchIfEmpty(Single.just(Optional.empty()))
                            .subscribe(
                                    updatedUserOpt -> {
                                        log.debug("MFA factor with ID [{}] enrolled for user {}", factorId, user.getId());
                                        // update inner context user profile with the new list of factors.
                                        // this is important to avoid losing the factors information on
                                        // user updates in the further policies execution (https://github.com/gravitee-io/issues/issues/9161)
                                        updatedUserOpt.ifPresent(updatedUser -> user.setFactors(updatedUser.getFactors()));
                                        policyChain.doNext(request, response);
                                    },
                                    error -> {
                                        log.error("Unable to enroll MFA factor with ID [{}]", factorId, error.getMessage());
                                        policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, error.getMessage()));
                                    });
                    return;
                }
            }

            // value is mandatory for every factor except the HTTP and OTP factors
            final FactorProvider factorProvider = factorManager.get(factorId);

            if (ObjectUtils.isEmpty(value) &&
                    !(FactorType.HTTP.getType().equals(factor.getFactorType().getType()) || FactorType.OTP.getType().equals(factor.getFactorType().getType()))
            ) {
                log.error("Value field is missing");
                policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, "Value field is missing"));
                return;
            }

            // enroll the MFA factor
            buildEnrolledFactor(factor, factorProvider, user, value, context)
                    .flatMap(enrolledFactor -> {
                        // clone the enrolledFactor to preserve security attribute removed by addFactor method
                        var cloneEnrolledFactor = new EnrolledFactor(enrolledFactor);
                        return userService.addFactor(user.getId(), enrolledFactor, new DefaultUser(user)).map(updatedUser -> {
                            // update inner context user profile with the new factor.
                            // this is important to avoid losing the factors information on
                            // user updates in the further policies execution (https://github.com/gravitee-io/issues/issues/9161)
                            // we can't use the factors coming from the updatedUser as addFactor remove security entry from each factor
                            // as we do not add a factor in this policy if it already exists in the user profile , we can safely add it to the list
                            var userFactors = user.getFactors();
                            if (userFactors == null) {
                                userFactors = new ArrayList<>();
                                user.setFactors(userFactors);
                            }
                            userFactors.add(cloneEnrolledFactor);

                            // we return the updatedUser here to keep previous behaviour if any
                            return updatedUser;
                        });
                    })
                    .subscribe(
                            __ -> {
                                log.debug("MFA factor with ID [{}] enrolled for user {}", factorId, user.getId());
                                policyChain.doNext(request, response);
                            },
                            error -> {
                                log.error("Unable to enroll MFA factor with ID [{}]", factorId, error.getMessage());
                                policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, error.getMessage()));
                            });


        } catch (Exception ex) {
            log.error("An error has occurred for [enroll-mfa] policy", ex);
            policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, ex.getMessage()));
        }
    }

    private Single<EnrolledFactor> buildEnrolledFactor(Factor factor, FactorProvider factorProvider, User user, String value, ExecutionContext context) {
        return Single.defer(() -> {
            try {
                // compute value
                String enrollmentValue = (!ObjectUtils.isEmpty(value)) ? context.getTemplateEngine().getValue(value, String.class) : null;
                if (!ObjectUtils.isEmpty(value) && ObjectUtils.isEmpty(enrollmentValue)) {
                    log.warn("The expression language set up for Enroll MFA has returned nothing");
                }
                // create the enrolled factor
                EnrolledFactor enrolledFactor = new EnrolledFactor();
                enrolledFactor.setFactorId(factor.getId());
                enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                enrolledFactor.setPrimary(configuration.isPrimary());
                switch (factor.getFactorType()) {
                    case OTP:
                        final String otpEnrollmentValue = enrollmentValue != null ? enrollmentValue : SharedSecret.generate();
                        Map<String, Object> otpAdditionalData = Collections.emptyMap();
                        if (factorProvider.useVariableFactorSecurity()) {
                            otpAdditionalData = Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, MovingFactorUtils.generateInitialMovingFactor(user.getId()));
                        }
                        enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, otpEnrollmentValue, otpAdditionalData));
                        break;
                    case SMS:
                        initiateVariableFactorSecurity(factorProvider, user)
                                .ifPresent(enrolledFactor::setSecurity);
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, enrollmentValue));
                        break;
                    case CALL:
                        initiateVariableFactorSecurity(factorProvider, user)
                                .ifPresent(enrolledFactor::setSecurity);
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.CALL, enrollmentValue));
                        break;
                    case EMAIL:
                        initiateVariableFactorSecurity(factorProvider, user)
                                .ifPresent(enrolledFactor::setSecurity);
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, enrollmentValue));
                        break;
                    case HTTP:
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.HTTP, enrollmentValue));
                        break;
                    default:
                        return Single.error(new IllegalStateException("Unexpected value: " + factor.getFactorType().getType()));
                }
                enrolledFactor.setCreatedAt(new Date());
                enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
                return Single.just(enrolledFactor);
            } catch (Exception ex) {
                log.error("An error has occurred when building the enrolled factor", ex);
                return Single.error(ex);
            }
        });
    }

    private static Optional<EnrolledFactorSecurity> initiateVariableFactorSecurity(FactorProvider factorProvider, User user) {
        if (factorProvider.useVariableFactorSecurity()) {
            Map<String, Object> additionalData = Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, MovingFactorUtils.generateInitialMovingFactor(user.getId()));
            return Optional.of(new EnrolledFactorSecurity(SHARED_SECRET, SharedSecret.generate(), additionalData));
        }
        return Optional.empty();
    }

    private Maybe<EnrolledFactor> refreshEnrolledFactor(EnrolledFactor enrolledFactor, Factor factor, String value, ExecutionContext context) {
        return Maybe.defer(() -> {
            try {
                // compute value
                String enrollmentValue = (!ObjectUtils.isEmpty(value)) ? context.getTemplateEngine().getValue(value, String.class) : null;
                if (!ObjectUtils.isEmpty(value) && ObjectUtils.isEmpty(enrollmentValue)) {
                    log.warn("The expression language set up for Enroll MFA has returned nothing");
                }
                // create the enrolled factor
                if (UPDATABLE_FACTORS.contains(factor.getFactorType()) && !ObjectUtils.isEmpty(enrollmentValue) && !enrolledFactor.getChannel().getTarget().equals(enrollmentValue)) {
                    enrolledFactor.getChannel().setTarget(enrollmentValue);
                    enrolledFactor.setUpdatedAt(new Date());
                    return Maybe.just(enrolledFactor);
                } else {
                    log.debug("Only SMS/CALL/EMAIL factor can be refreshed by EnrollMfa Policy");
                }
                return Maybe.empty();
            } catch (Exception ex) {
                log.error("An error has occurred when building the enrolled factor", ex);
                return Maybe.error(ex);
            }
        });
    }
}
