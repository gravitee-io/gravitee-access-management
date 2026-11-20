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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.service.ratelimit.DeviceFlowRateLimiterService;
import io.gravitee.am.gateway.handler.common.utils.DeviceFlowContext;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.DeviceFlowAuditBuilder;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.CustomLog;

import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Validates the code typed by the end user and either hands the request over to the shared
 * authorization chain, which then runs login, MFA, risk policies and consent unchanged, or, when
 * the user rejected the code, brings the request to its terminal denied state. A code which is
 * unknown, which belongs to another application or which has already been settled all read the
 * same, so none of them tells the user whether that code ever existed.
 *
 * Every submission is attributable to an authenticated user, so an account guessing its way through
 * the outstanding codes is refused once its budget runs out, before the code is ever looked up. Only
 * a code no application of the domain is waiting on spends that budget: pairing a device, refusing
 * to, or coming back to a code that has already been settled never costs an account an attempt.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceVerificationSubmitEndpoint implements Handler<RoutingContext> {

    static final String ERROR_MISSING = "missing";
    static final String ERROR_INVALID = "invalid";
    static final String ERROR_TOO_MANY_ATTEMPTS = "too_many_attempts";
    static final String ACTION_PARAM = "action";
    static final String ACTION_REJECT = "reject";
    private static final String AUTHORIZE_PATH = "/oauth/authorize";

    private final DeviceAuthorizationRequestService requestService;
    private final DeviceFlowPageRenderer renderer;
    private final DeviceFlowRateLimiterService rateLimiterService;
    private final AuditService auditService;

    public DeviceVerificationSubmitEndpoint(DeviceAuthorizationRequestService requestService,
                                            DeviceFlowPageRenderer renderer,
                                            DeviceFlowRateLimiterService rateLimiterService,
                                            AuditService auditService) {
        this.requestService = requestService;
        this.renderer = renderer;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
    }

    @Override
    public void handle(RoutingContext context) {
        final boolean rejected = ACTION_REJECT.equalsIgnoreCase(context.request().getFormAttribute(ACTION_PARAM));
        final String userCode = context.request().getFormAttribute(Parameters.USER_CODE);
        if (userCode == null || userCode.isBlank()) {
            if (rejected) {
                renderer.renderCompletion(context, DeviceFlowOutcome.INVALID);
            } else {
                backToCodeEntryPage(context, ERROR_MISSING);
            }
            return;
        }

        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (!rateLimiterService.isRateLimitEnabled()) {
            lookUpCode(context, client, userCode, rejected);
            return;
        }

        final String endUserId = endUserId(context);
        if (endUserId == null) {
            log.error("Device flow code entry reached without an authenticated end user");
            context.fail(new AccessDeniedException());
            return;
        }

        rateLimiterService.hasAttemptsLeft(endUserId, client.getDomain())
                .subscribe(
                        allowed -> {
                            if (allowed) {
                                lookUpCode(context, client, userCode, rejected);
                            } else {
                                reportVerificationFailed(context, "Device flow code entry refused: too many attempts");
                                backToCodeEntryPage(context, ERROR_TOO_MANY_ATTEMPTS);
                            }
                        },
                        error -> {
                            log.error("Unable to rate limit the device flow code entry", error);
                            context.fail(error);
                        });
    }

    private void lookUpCode(RoutingContext context, Client client, String userCode, boolean rejected) {
        requestService.findByUserCode(userCode)
                .subscribe(
                        request -> onCodeFound(context, client, request, rejected),
                        error -> {
                            log.error("Unable to read the device authorization request", error);
                            context.fail(error);
                        },
                        () -> onUnusableCode(context, rejected));
    }

    private String endUserId(RoutingContext context) {
        final io.gravitee.am.model.User endUser = RoutingContextHelper.endUser(context);
        return endUser == null ? null : endUser.getId();
    }

    private void onCodeFound(RoutingContext context, Client client, DeviceAuthorizationRequest request, boolean rejected) {
        if (!client.getClientId().equals(request.getClientId())) {
            onUnusableCode(context, rejected);
            return;
        }
        if (rejected) {
            deny(context, client, request);
            return;
        }
        if (requestService.isExpired(request)) {
            renderer.renderCompletion(context, DeviceFlowOutcome.EXPIRED);
            return;
        }
        if (!requestService.isPending(request)) {
            backToCodeEntryPage(context, ERROR_INVALID);
            return;
        }
        handOverToAuthorizationChain(context, request);
    }

    private void deny(RoutingContext context, Client client, DeviceAuthorizationRequest request) {
        requestService.deny(request.getId(), client.getClientId())
                .subscribe(
                        denied -> {
                            reportDenied(context, client);
                            renderer.renderCompletion(context, DeviceFlowOutcome.DENIED);
                        },
                        error -> DeviceFlowOutcome.of(error).ifPresentOrElse(
                                outcome -> renderer.renderCompletion(context, outcome),
                                () -> {
                                    log.error("Unable to reject the device authorization request", error);
                                    context.fail(error);
                                }));
    }

    private void onUnusableCode(RoutingContext context, boolean rejected) {
        reportVerificationFailed(context, "Device flow user code verification failed");
        onWrongCode(context, () -> reportUnusableCode(context, rejected));
    }

    private void reportVerificationFailed(RoutingContext context, String reason) {
        auditService.report(AuditBuilder.builder(DeviceFlowAuditBuilder.class)
                .type(EventType.DEVICE_FLOW_VERIFICATION_FAILED)
                .throwable(new Throwable(reason))
                .user(RoutingContextHelper.endUser(context))
                .application(context.get(ConstantKeys.CLIENT_CONTEXT_KEY))
                .ipAddress(context)
                .userAgent(context));
    }

    private void reportDenied(RoutingContext context, Client client) {
        auditService.report(AuditBuilder.builder(DeviceFlowAuditBuilder.class)
                .type(EventType.DEVICE_FLOW_DENIED)
                .user(RoutingContextHelper.endUser(context))
                .application(client)
                .ipAddress(context)
                .userAgent(context));
    }

    private void reportUnusableCode(RoutingContext context, boolean rejected) {
        if (rejected) {
            renderer.renderCompletion(context, DeviceFlowOutcome.INVALID);
        } else {
            backToCodeEntryPage(context, ERROR_INVALID);
        }
    }

    private void onWrongCode(RoutingContext context, Runnable then) {
        final String endUserId = endUserId(context);
        if (!rateLimiterService.isRateLimitEnabled() || endUserId == null) {
            then.run();
            return;
        }
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        rateLimiterService.recordWrongAttempt(endUserId, client.getDomain())
                .subscribe(
                        then::run,
                        error -> {
                            log.error("Unable to record a wrong device flow code entry", error);
                            then.run();
                        });
    }

    private void backToCodeEntryPage(RoutingContext context, String error) {
        context.session().put(ConstantKeys.DEVICE_FLOW_ERROR_KEY, error);
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        queryParams.add(Parameters.CLIENT_ID, ((Client) context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).getClientId());
        redirect(context, UriBuilderRequest.resolveProxyRequest(context.request(),
                context.get(CONTEXT_PATH) + DeviceVerificationUriResolver.VERIFICATION_PATH, queryParams, true));
    }

    private void handOverToAuthorizationChain(RoutingContext context, DeviceAuthorizationRequest request) {
        DeviceFlowContext.markDeviceFlow(context, request.getId(), request.getClientId());
        context.session().remove(ConstantKeys.RETURN_URL_KEY);

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        queryParams.add(Parameters.CLIENT_ID, request.getClientId());
        Optional.ofNullable(request.getScopes())
                .filter(scopes -> !scopes.isEmpty())
                .ifPresent(scopes -> queryParams.add(Parameters.SCOPE, String.join(" ", scopes)));

        redirect(context, UriBuilderRequest.resolveProxyRequest(context.request(),
                context.get(CONTEXT_PATH) + AUTHORIZE_PATH, queryParams, true));
    }

    private void redirect(RoutingContext context, String location) {
        context.response()
                .putHeader(HttpHeaders.LOCATION, location)
                .setStatusCode(302)
                .end();
    }
}
