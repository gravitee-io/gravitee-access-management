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
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
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
import io.gravitee.common.util.Maps;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static java.lang.Boolean.TRUE;


abstract class MFAChallengeEndpoint extends MFAEndpoint {
    public static final String PREVIOUS_TRANSACTION_ID_KEY = "prev-tid";

    protected final FactorManager factorManager;
    protected final DomainDataPlane domainDataPlane;
    protected final AuditService auditService;

    public MFAChallengeEndpoint(FactorManager factorManager,
                                TemplateEngine engine,
                                DomainDataPlane domainDataPlane,
                                AuditService auditService) {
        super(engine);
        this.factorManager = factorManager;
        this.domainDataPlane = domainDataPlane;
        this.auditService = auditService;
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

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    protected void handleException(RoutingContext context, String errorKey, String errorValue) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // redirect to mfa challenge page with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        Map<String, String> parameters = new LinkedHashMap<>(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put(errorKey, errorValue);
        if(context.session() != null){
            context.session().put(ERROR_HASH, HashUtil.generateSHA256(errorValue));
        }
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters, true);
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
