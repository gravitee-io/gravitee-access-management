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
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.policy.enroll.mfa.configuration.EnrollMfaPolicyConfiguration;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrollMfaPolicy {

    static final String GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY = "GATEWAY_POLICY_ENROLL_MFA_ERROR";
    private static Logger LOGGER = LoggerFactory.getLogger(EnrollMfaPolicy.class);
    private final EnrollMfaPolicyConfiguration configuration;

    public EnrollMfaPolicy(EnrollMfaPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        LOGGER.debug("Start enroll MFA policy");
        final String factorId = configuration.getFactorId();
        final String value = configuration.getValue();

        if (ObjectUtils.isEmpty(factorId)) {
            LOGGER.warn("No factor ID configured for the enroll MFA policy");
            policyChain.doNext(request, response);
            return;
        }

        try {
            final UserService userService = context.getComponent(UserService.class);
            final User user = (User) context.getAttribute(ConstantKeys.USER_CONTEXT_KEY);
            final FactorManager factorManager = context.getComponent(FactorManager.class);
            final Factor factor = factorManager.getFactor(factorId);

            if (factor == null) {
                LOGGER.warn("No MFA factor with ID [{}] found", factorId);
                policyChain.doNext(request, response);
                return;
            }

            if (user == null) {
                LOGGER.warn("No user found in context");
                policyChain.doNext(request, response);
                return;
            }

            // no need to enroll the same factor
            if (!ObjectUtils.isEmpty(user.getFactors())
                    && user.getFactors().stream().anyMatch(enrolledFactor -> enrolledFactor.getFactorId().equals(factorId))) {
                LOGGER.debug("MFA factor with ID [{}] already enrolled for the current user", factorId);
                policyChain.doNext(request, response);
                return;
            }

            // value is mandatory for every factor except the HTTP one
            if (ObjectUtils.isEmpty(value) &&
                    !FactorType.HTTP.getType().equals(factor.getFactorType().getType())) {
                LOGGER.error("Value field is missing");
                policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, "Value field is missing"));
                return;
            }

            // enroll the MFA factor
            buildEnrolledFactor(factor, user, value, context)
                    .flatMap(enrolledFactor -> userService.addFactor(user.getId(), enrolledFactor, new DefaultUser(user)))
                    .subscribe(
                            __ -> {
                                LOGGER.debug("MFA factor with ID [{}] enrolled for user {}", factorId, user.getId());
                                policyChain.doNext(request, response);
                            },
                            error -> {
                                LOGGER.error("Unable to enroll MFA factor with ID [{}]", factorId, error.getMessage());
                                policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, error.getMessage()));
                            });


        } catch (Exception ex) {
            LOGGER.error("An error has occurred for [enroll-mfa] policy", ex);
            policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENROLL_MFA_ERROR_KEY, ex.getMessage()));
        }
    }

    private Single<EnrolledFactor> buildEnrolledFactor(Factor factor, User user, String value, ExecutionContext context) {
        return Single.defer(() -> {
            try {
                // compute value
                String enrollmentValue = (!ObjectUtils.isEmpty(value)) ? context.getTemplateEngine().getValue(value, String.class) : null;
                if (!ObjectUtils.isEmpty(value) && ObjectUtils.isEmpty(enrollmentValue)) {
                    LOGGER.warn("The expression language set up for Enroll MFA has returned nothing");
                }
                // create the enrolled factor
                EnrolledFactor enrolledFactor = new EnrolledFactor();
                enrolledFactor.setFactorId(factor.getId());
                enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
                enrolledFactor.setPrimary(configuration.isPrimary());
                switch (factor.getFactorType()) {
                    case OTP:
                        enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, enrollmentValue));
                        break;
                    case SMS:
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, enrollmentValue));
                        break;
                    case CALL:
                        enrolledFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.CALL, enrollmentValue));
                        break;
                    case EMAIL:
                        Map<String, Object> additionalData = Collections.singletonMap(FactorDataKeys.KEY_MOVING_FACTOR, generateInitialMovingFactor(user));
                        enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, SharedSecret.generate(), additionalData));
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
                LOGGER.error("An error has occurred when building the enrolled factor", ex);
                return Single.error(ex);
            }
        });
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
