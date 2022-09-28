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
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorChannel.Type;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.FactorNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.util.Maps;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static io.gravitee.am.common.factor.FactorSecurityType.WEBAUTHN_CREDENTIAL;
import static io.gravitee.am.common.factor.FactorType.FIDO2;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_TYPE;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY;
import static io.gravitee.am.factor.api.FactorContext.KEY_USER;
import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;
import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(MFAChallengeEndpoint.class);
    private static final String MFA_ALTERNATIVES_ACTION_KEY = "mfaAlternativesAction";
    private static final String MFA_ALTERNATIVES_ENABLE_KEY = "mfaAlternativesEnabled";
    private static final String REMEMBER_DEVICE_CONSENT = "rememberDeviceConsent";
    public static final String REMEMBER_DEVICE_CONSENT_ON = "on";

    private final FactorManager factorManager;
    private final UserService userService;
    private final ApplicationContext applicationContext;
    private final DeviceService deviceService;
    private final Domain domain;
    private final CredentialService credentialService;
    private final FactorService factorService;

    public MFAChallengeEndpoint(FactorManager factorManager, UserService userService, TemplateEngine engine, DeviceService deviceService,
                                ApplicationContext applicationContext, Domain domain, CredentialService credentialService, FactorService factorService) {
        super(engine);
        this.applicationContext = applicationContext;
        this.factorManager = factorManager;
        this.userService = userService;
        this.deviceService = deviceService;
        this.domain = domain;
        this.credentialService = credentialService;
        this.factorService = factorService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                renderMFAPage(routingContext);
                break;
            case "POST":
                verifyCode(routingContext);
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void renderMFAPage(RoutingContext routingContext) {
        try {
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            if (routingContext.user() == null) {
                logger.warn("User must be authenticated to request MFA challenge.");
                routingContext.fail(401);
                return;
            }
            final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Factor factor = getFactor(routingContext, client, endUser);
            final String error = routingContext.request().getParam(ConstantKeys.ERROR_PARAM_KEY);

            // prepare context
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
            final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true);
            routingContext.put(ConstantKeys.FACTOR_KEY, factor);

            if (factor.is(FIDO2)) {
                final String webAuthnLoginPath = UriBuilderRequest.resolveProxyRequest(routingContext.request(),
                        routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true);

                routingContext.put("webAuthnLoginPath", webAuthnLoginPath);
                routingContext.put("userName", endUser.getUsername());
            }

            routingContext.put(ConstantKeys.ACTION_KEY, action);
            routingContext.put(ConstantKeys.ERROR_PARAM_KEY, error);
            //Include deviceId, so we can show/hide the "save my device" checkbox
            var deviceId = routingContext.session().get(ConstantKeys.DEVICE_ID);
            if (deviceId != null) {
                routingContext.put(ConstantKeys.DEVICE_ID, deviceId);
            }
            if (enableAlternateMFAOptions(client, endUser)) {
                routingContext.put(MFA_ALTERNATIVES_ENABLE_KEY, true);
                routingContext.put(MFA_ALTERNATIVES_ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/mfa/challenge/alternatives", queryParams, true));
            }

            final FactorProvider factorProvider = factorManager.get(factor.getId());
            // send challenge
            sendChallenge(factorProvider, routingContext, factor, endUser, resChallenge -> {
                if (resChallenge.failed()) {
                    logger.error("An error has occurred when sending MFA challenge", resChallenge.cause());
                    routingContext.fail(resChallenge.cause());
                    return;
                }
                // render the mfa challenge page
                this.renderPage(routingContext, generateData(routingContext, domain, client), client, logger, "Unable to render MFA challenge page");
            });
        } catch (Exception ex) {
            logger.error("An error has occurred when rendering MFA challenge page", ex);
            routingContext.fail(503);
        }
    }

    private void verifyCode(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (routingContext.user() == null) {
            logger.warn("User must be authenticated to submit MFA challenge.");
            routingContext.fail(401);
            return;
        }

        final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        final Factor factor = getFactor(routingContext, client, endUser);
        MultiMap params = routingContext.request().formAttributes();
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
        FactorProvider factorProvider = factorManager.get(factorId);
        EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser);
        Map<String, Object> factorData = new HashMap<>();
        factorData.putAll(getEvaluableAttributes(routingContext));
        factorData.put(FactorContext.KEY_ENROLLED_FACTOR, enrolledFactor);
        factorData.put(FactorContext.KEY_CODE, code);
        factorData.put(FactorContext.KEY_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));

        if(factor.is(FIDO2)){
            factorData.put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, routingContext.session().get(PASSWORDLESS_CHALLENGE_KEY));
            factorData.put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, routingContext.session().get(PASSWORDLESS_CHALLENGE_USERNAME_KEY));
            factorData.put(ConstantKeys.PASSWORDLESS_ORIGIN, domain.getWebAuthnSettings().getOrigin());
        }

        final FactorContext factorCtx = new FactorContext(applicationContext, factorData);

        verify(factorProvider, factorCtx, h -> {
            if (h.failed()) {
                handleException(routingContext);
                return;
            }

            if (factor.is(FIDO2)) {
                final String userId = endUser.getId();
                final JsonObject webauthnResp = new JsonObject(code);
                final String credentialId = webauthnResp.getString("id");
                updateCredential(routingContext.request(), credentialId, userId, ch -> {

                    if (ch.failed()) {
                        final String username = routingContext.session().get(PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                        logger.error("An error has occurred while updating credential for the user {}", username, h.cause());
                        routingContext.fail(401);
                        return;
                    }

                    updateStrongAuthStatus(routingContext);

                    if (userHasFido2Factor(endUser)) {
                        cleanSession(routingContext);
                        redirectToAuthorize(routingContext, client);
                    } else {
                        final String fidoFactorId = routingContext.session().get(ENROLLED_FACTOR_ID_KEY);
                        factorService.enrollFactor(endUser, createEnrolledFactor(fidoFactorId, credentialId))
                                .ignoreElement()
                                .subscribe(
                                () -> {
                                    cleanSession(routingContext);
                                    redirectToAuthorize(routingContext, client);
                                },
                                error -> {
                                    logger.error("Could not update user profile with FIDO2 factor detail", error);
                                    routingContext.fail(401);
                                }
                        );
                    }
                });
                return;
            }
            // save enrolled factor if needed and redirect to the original url
            routingContext.session().put(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY, factorId);
            if (routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null || factorProvider.useVariableFactorSecurity()) {
                enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                saveFactor(endUser, factorProvider.changeVariableFactorSecurity(enrolledFactor), fh -> {
                    if (fh.failed()) {
                        logger.error("An error occurs while saving enrolled factor for the current user", fh.cause());
                        handleException(routingContext);
                        return;
                    }

                    cleanSession(routingContext);
                    updateStrongAuthStatus(routingContext);
                    redirectToAuthorize(routingContext, client);
                });
            } else {
                updateStrongAuthStatus(routingContext);
                redirectToAuthorize(routingContext, client);
            }
        });
    }

    private void redirectToAuthorize(RoutingContext routingContext, Client client) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        final String returnURL = getReturnUrl(routingContext, queryParams);
        //Register device if the device is active
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        boolean rememberDeviceConsent = REMEMBER_DEVICE_CONSENT_ON.equalsIgnoreCase(routingContext.request().getParam(REMEMBER_DEVICE_CONSENT));
        if (rememberDeviceSettings.isActive() && rememberDeviceConsent) {
            var user = routingContext.user().principal();
            saveDeviceAndRedirect(routingContext, client, user.getString("id"), rememberDeviceSettings, returnURL);
        } else {
            doRedirect(routingContext.request().response(), returnURL);
        }
    }

    private void verify(FactorProvider factorProvider, FactorContext factorContext, Handler<AsyncResult<Void>> handler) {
        factorProvider.verify(factorContext)
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private void saveFactor(User user, Single<EnrolledFactor> enrolledFactor, Handler<AsyncResult<User>> handler) {
        enrolledFactor.flatMap(factor -> userService.addFactor(user.getId(), factor, new DefaultUser(user)))
                .subscribe(
                        user1 -> handler.handle(Future.succeededFuture(user1)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }

    private void sendChallenge(FactorProvider factorProvider, RoutingContext routingContext, Factor factor, User endUser, Handler<AsyncResult<Void>> handler) {
        if (!factorProvider.needChallengeSending() || routingContext.get(ConstantKeys.ERROR_PARAM_KEY) != null) {
            // do not send challenge in case of error param to avoid useless code generation
            handler.handle(Future.succeededFuture());
            return;
        }

        EnrolledFactor enrolledFactor = getEnrolledFactor(routingContext, factorProvider, factor, endUser);
        Map<String, Object> factorData = new HashMap<>();
        factorData.putAll(getEvaluableAttributes(routingContext));
        factorData.put(FactorContext.KEY_CLIENT, routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        factorData.put(KEY_USER, endUser);
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
            throw new FactorNotFoundException("No factor found for the end user");
        }

        // get the primary enrolled factor
        // if there is no primary, select the first created
        List<EnrolledFactor> enrolledFactors = endUser.getFactors()
                .stream()
                .filter(enrolledFactor -> client.getFactors().contains(enrolledFactor.getFactorId()))
                .sorted(Comparator.comparing(EnrolledFactor::getCreatedAt))
                .collect(Collectors.toList());

        if (enrolledFactors.isEmpty()) {
            throw new FactorNotFoundException("No factor found for the end user");
        }

        return enrolledFactors
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.isPrimary()))
                .map(enrolledFactor -> factorManager.getFactor(enrolledFactor.getFactorId()))
                .findFirst()
                .orElse(factorManager.getFactor(enrolledFactors.get(0).getFactorId()));
    }

    private EnrolledFactor getEnrolledFactor(RoutingContext routingContext, FactorProvider factorProvider, Factor factor, User endUser) {
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
                    enrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET,
                            routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY)));
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
            }

            // if the factor provider uses a moving factor security mechanism,
            // we ensure that every data has been shared with the user enrolled factor
            if (factorProvider.useVariableFactorSecurity()) {
                Map<String, Object> additionalData = new Maps.MapBuilder(new HashMap())
                        .put(FactorDataKeys.KEY_MOVING_FACTOR, generateInitialMovingFactor(endUser))
                        .build();
                getEnrolledFactor(factor, endUser).ifPresent(ef -> {
                    additionalData.put(FactorDataKeys.KEY_EXPIRE_AT, ef.getSecurity().getData(FactorDataKeys.KEY_EXPIRE_AT, Long.class));
                });
                enrolledFactor.getSecurity().getAdditionalData().putAll(additionalData);
            }

            enrolledFactor.setCreatedAt(new Date());
            enrolledFactor.setUpdatedAt(enrolledFactor.getCreatedAt());
            return enrolledFactor;
        }

        return getEnrolledFactor(factor, endUser)
                .orElseThrow(() -> new FactorNotFoundException("No enrolled factor found for the end user"));
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

    private int generateInitialMovingFactor(User endUser) {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(endUser.getUsername().getBytes(StandardCharsets.UTF_8));
            return secureRandom.nextInt(1000) + 1;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }

    @Override
    public String getTemplateSuffix() {
        return Template.MFA_CHALLENGE.template();
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private void handleException(RoutingContext context) {
        final HttpServerRequest req = context.request();
        final HttpServerResponse resp = context.response();

        // redirect to mfa challenge page with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(req.uri());
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.putAll(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put("error", "mfa_challenge_failed");
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.path(), parameters, true);
        doRedirect(resp, uri);
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private void saveDeviceAndRedirect(RoutingContext routingContext, Client client, String userId, RememberDeviceSettings settings, String redirectUrl) {
        var domain = client.getDomain();
        var deviceId = routingContext.session().<String>get(DEVICE_ID);
        var rememberDeviceId = settings.getDeviceIdentifierId();
        if (isNullOrEmpty(deviceId)) {
            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            doRedirect(routingContext.response(), redirectUrl);
        } else {
            this.deviceService.deviceExists(domain, client.getClientId(), userId, rememberDeviceId, deviceId).flatMapMaybe(isEmpty -> {
                if (!isEmpty) {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
                    return Maybe.empty();
                }
                var deviceType = routingContext.session().<String>get(DEVICE_TYPE);
                return this.deviceService.create(
                                domain,
                                client.getClientId(),
                                userId,
                                rememberDeviceId,
                                isNullOrEmpty(deviceType) ? "Unknown" : deviceType,
                                settings.getExpirationTimeSeconds(), deviceId)
                        .doOnSuccess(device -> routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true))
                        .doOnError(device -> routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false))
                        .toMaybe();
            }).doFinally(() -> {
                        routingContext.session().remove(DEVICE_ID);
                        routingContext.session().remove(DEVICE_TYPE);
                        doRedirect(routingContext.response(), redirectUrl);
                    }
            ).subscribe();
        }
    }

    private void updateCredential(HttpServerRequest request, String credentialId, String userId, Handler<AsyncResult<Void>> handler) {
        final Credential credential = new Credential();
        credential.setUserId(userId);
        credential.setUserAgent(RequestUtils.userAgent(request));
        credential.setIpAddress(RequestUtils.remoteAddress(request));

        credentialService.update(ReferenceType.DOMAIN, domain.getId(), credentialId, credential)
                .subscribe(
                        () -> handler.handle(Future.succeededFuture()),
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
        ctx.session().remove(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS);
    }

    private boolean userHasFido2Factor(io.gravitee.am.model.User endUser) {
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

    private boolean enableAlternateMFAOptions(Client client, io.gravitee.am.model.User endUser ){
        if(endUser.getFactors() == null || endUser.getFactors().size() <= 1) {
            return false;
        }
        else if(client.getFactors() == null || client.getFactors().size() <= 1){
            return false;
        }
        else {
            final Set<String> clientFactorIds = client.getFactors();
            final List<EnrolledFactor> activeEnrolledFactors = endUser.getFactors()
                    .stream()
                    .filter(enrolledFactor -> factorManager.get(enrolledFactor.getFactorId()) != null)
                    .filter(enrolledFactor -> clientFactorIds.contains(enrolledFactor.getFactorId()))
                    .collect(Collectors.toList());

            return activeEnrolledFactors.size() > 1;
        }
    }
}
