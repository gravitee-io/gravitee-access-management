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
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.ApplicationFactorSettings;
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
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

public class MFAEnrollPostEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAEnrollPostEndpoint.class);

    private final FactorManager factorManager;
    private final UserService userService;

    public MFAEnrollPostEndpoint(FactorManager factorManager,
                                 UserService userService) {
        this.factorManager = factorManager;
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        saveEnrollmentToSession(routingContext);
    }

    private void saveEnrollmentToSession(RoutingContext routingContext) {
        MultiMap params = routingContext.request().formAttributes();
        final boolean acceptEnrollment = ofNullable(params.get(ConstantKeys.USER_MFA_ENROLLMENT)).map(Boolean::parseBoolean).orElse(true);
        final String factorId = params.get(ConstantKeys.MFA_ENROLLMENT_FACTOR_ID);
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        if (!acceptEnrollment && !MfaUtils.isCanSkip(routingContext, client)) {
            // user request a skipEnrollment but it is required,
            // redirect back to the authorize endpoint to force
            // the enrollment
            routingContext.next();
            return;
        }
        if(isSkipped(routingContext, acceptEnrollment, client)){
            routingContext.next();
            return;
        }

        getValidFactor(routingContext, factorId, client)
                .ifPresent(optFactor -> manageEnrolledFactors(routingContext, optFactor, params));
    }

    private boolean isSkipped(RoutingContext routingContext, boolean acceptEnrollment, Client client) {
        // if user has skipped the enrollment process, continue
        if (!acceptEnrollment && MfaUtils.isCanSkip(routingContext, client)) {
            final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            // set the last skipped time
            // and update the session
            userService.setMfaEnrollmentSkippedTime(client, endUser)
                    .subscribe(() -> {
                        // as the user has skipped the MFA enroll page
                        // that means the user has also skipped the MFA challenge page
                        routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true);
                        routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
                    });
            return true;
        }
        return false;
    }


    private Optional<Map.Entry<io.gravitee.am.model.Factor, FactorProvider>> getValidFactor(RoutingContext routingContext, String factorId, Client client) {
        if (factorId == null) {
            logger.warn("No factor id in form - did you forget to include factor id value ?");
            routingContext.fail(400);
            return Optional.empty();
        }

        final Map<io.gravitee.am.model.Factor, FactorProvider> factors = getFactors(client);
        Optional<Map.Entry<io.gravitee.am.model.Factor, FactorProvider>> optFactor = factors.entrySet().stream().filter(factor -> factorId.equals(factor.getKey().getId())).findFirst();
        if (optFactor.isEmpty()) {
            logger.warn("Factor not found - did you send a valid factor id ?");
            routingContext.fail(400);
            return Optional.empty();
        }

        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to enroll MFA challenge.");
            routingContext.fail(401);
            return Optional.empty();
        }

        final var endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        if (userAlreadyHasFactor(endUser, client, routingContext.session())) {
            logger.warn("User already has active factor, enrollment of factor '{}' rejected", factorId);
            routingContext.fail(new InvalidRequestException("factor already enrolled"));
            return Optional.empty();
        }
        return optFactor;
    }

    private void manageEnrolledFactors(RoutingContext routingContext, Map.Entry<io.gravitee.am.model.Factor, FactorProvider> optFactor, MultiMap params) {
        final String sharedSecret = params.get(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET);
        final String phoneNumber = params.get(ConstantKeys.MFA_ENROLLMENT_PHONE);
        final String extensionPhoneNumber = params.get(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER);
        final String emailAddress = params.get(ConstantKeys.MFA_ENROLLMENT_EMAIL);
        FactorProvider provider = optFactor.getValue();
        EnrolledFactor enrolledFactor = getSecurityFactor(params, optFactor.getKey());
        if (provider.checkSecurityFactor(enrolledFactor)) {
            // save enrolled factor for the current user and continue
            routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, optFactor.getKey().getId());
            logger.debug("Factor {} selected", optFactor.getKey().getId());

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
            routingContext.next();
        } else {
            routingContext.fail(new EnrollmentChannelValidationException("Invalid parameters"));
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
            default:
        }
        // set shared secret
        final String sharedSecret = params.get("sharedSecret");
        if (sharedSecret != null && !sharedSecret.isEmpty()) {
            enrolledFactor.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.SHARED_SECRET, sharedSecret));
        }
        return enrolledFactor;
    }



    private Map<io.gravitee.am.model.Factor, FactorProvider> getFactors(Client client) {
        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(f -> factorManager.get(f) != null)
                .collect(Collectors.toMap(factorManager::getFactor, factorManager::get));
    }

    private Set<String> getRecoveryFactorIds(Client client) {
        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(f -> factorManager.getFactor(f) != null && FactorType.RECOVERY_CODE.equals(factorManager.getFactor(f).getFactorType()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_ENROLL.template();
    }

}
