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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.gateway.WebAuthnBrowserClientErrorAuditBuilder;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Accepts same-origin client-reported failures from {@code navigator.credentials.get} / {@code create} for logging.
 * Must be installed after {@link io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnTelemetrySameOriginHandler}.
 * Correlation identifier resolution (first non-blank): configurable transaction HTTP header (default {@code X-Gravitee-Transaction-Id},
 * property {@code handlers.request.transaction.header}), then routing context {@link ConstantKeys#TRANSACTION_ID_KEY}.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebauthnErrorEndpoint implements Handler<RoutingContext> {

    /** Default request header carrying the gateway transaction id; must match reactor {@code TransactionHandler} configuration. */
    public static final String DEFAULT_TRANSACTION_ID_HTTP_HEADER = "X-Gravitee-Transaction-Id";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebauthnErrorEndpoint.class);

    private static final int MAX_ERROR_NAME = 128;
    private static final int MAX_ERROR_MESSAGE = 512;
    private static final int MAX_RP_ID = 256;
    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_CORRELATION_ID = 128;
    private static final int MAX_USERNAME = 256;
    private static final String PHASE = "phase";
    private static final String OPERATION = "operation";
    private static final String ERROR_NAME = "errorName";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String RP_ID = "rpId";
    private static final String CLIENT_TIMESTAMP = "clientTimestamp";
    private static final String USERNAME = "username";
    private static final String LOGIN_PHASE = "login";
    private static final String REGISTER_PHASE = "register";
    private static final String MFA_PHASE = "mfa";
    private static final String GET_OPERATION = "get";
    private static final String CREATE_OPERATION = "create";

    private final Domain domain;
    private final AuditService auditService;
    private final String transactionIdHttpHeader;

    public WebauthnErrorEndpoint(Domain domain, AuditService auditService, String transactionIdHttpHeader) {
        this.domain = domain;
        this.auditService = auditService;
        this.transactionIdHttpHeader =
                transactionIdHttpHeader != null && !transactionIdHttpHeader.isBlank()
                        ? transactionIdHttpHeader.trim()
                        : DEFAULT_TRANSACTION_ID_HTTP_HEADER;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (ctx.request().method() != HttpMethod.POST) {
            ctx.fail(405);
            return;
        }
        final JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            LOGGER.debug("WebAuthn client error: invalid JSON body");
            ctx.response().setStatusCode(400).end();
            return;
        }
        if (body == null) {
            ctx.response().setStatusCode(400).end();
            return;
        }

        String phase = trimToNull(body.getString(PHASE));
        String operation = trimToNull(body.getString(OPERATION));
        String errorName = trimToMax(body.getString(ERROR_NAME), MAX_ERROR_NAME);
        if (phase == null || operation == null || errorName == null || errorName.isEmpty()) {
            ctx.response().setStatusCode(400).end();
            return;
        }
        if (!isAllowedPhase(phase) || !isAllowedOperation(operation)) {
            ctx.response().setStatusCode(400).end();
            return;
        }

        String errorMessage = trimToMax(body.getString(ERROR_MESSAGE), MAX_ERROR_MESSAGE);
        String rpId = trimToMax(body.getString(RP_ID), MAX_RP_ID);
        Long clientTimestamp = body.getLong(CLIENT_TIMESTAMP);
        String username = trimToMax(body.getString(USERNAME), MAX_USERNAME);

        WebAuthnClientErrorCategory category = WebAuthnClientErrorCategory.fromTechnicalErrorName(errorName);

        Client client = ctx.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        String clientId = client != null ? client.getClientId() : null;
        String domainId = domain != null ? domain.getId() : null;

        String correlationId = resolveCorrelationId(ctx);

        String remoteAddressForLog = "";
        if (ConsentUtils.canSaveIp(ctx)) {
            String ra = RequestUtils.remoteAddress(ctx.request());
            remoteAddressForLog = ra != null ? ra : "";
        }
        String userAgentForLog = "";
        if (ConsentUtils.canSaveUserAgent(ctx)) {
            String ua = trimToMax(ctx.request().getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT);
            userAgentForLog = ua != null ? ua : "";
        }

        LOGGER.debug(
                "WebAuthn client error: category={} phase={} operation={} technicalError={} technicalMessage={} "
                        + "domainId={} clientId={} correlationId={} username={} rpId={} clientTimestamp={} "
                        + "serverTimestamp={} remoteAddress={} userAgent={}",
                category.name(),
                phase,
                operation,
                errorName,
                errorMessage != null ? errorMessage : "",
                domainId != null ? domainId : "",
                clientId != null ? clientId : "",
                correlationId != null ? correlationId : "",
                username != null ? username : "",
                rpId != null ? rpId : "",
                clientTimestamp != null ? clientTimestamp : "",
                Instant.now().toString(),
                remoteAddressForLog,
                userAgentForLog);

        reportAudit(ctx, client, phase, operation, category, errorName, errorMessage, rpId, clientTimestamp, correlationId, username);

        ctx.response().setStatusCode(204).end();
    }

    private void reportAudit(
            RoutingContext ctx,
            Client client,
            String phase,
            String operation,
            WebAuthnClientErrorCategory category,
            String errorName,
            String errorMessage,
            String rpId,
            Long clientTimestamp,
            String correlationId,
            String username) {
        try {
            auditService.report(
                    AuditBuilder.builder(WebAuthnBrowserClientErrorAuditBuilder.class)
                            .domain(domain)
                            .oauthClient(client)
                            .endUserUsername(username, domain)
                            .network(ctx)
                            .details(
                                    phase,
                                    operation,
                                    category.name(),
                                    errorName,
                                    errorMessage,
                                    rpId,
                                    clientTimestamp,
                                    correlationId));
        } catch (Exception e) {
            LOGGER.debug("WebAuthn client error: audit report failed: {}", e.toString());
        }
    }

    private static boolean isAllowedPhase(String phase) {
        return LOGIN_PHASE.equals(phase) || REGISTER_PHASE.equals(phase) || MFA_PHASE.equals(phase);
    }

    private static boolean isAllowedOperation(String operation) {
        return GET_OPERATION.equals(operation) || CREATE_OPERATION.equals(operation);
    }

    /**
     * Uses the configured transaction HTTP header (same as {@code TransactionHandler}), then {@link ConstantKeys#TRANSACTION_ID_KEY}
     * on the routing context.
     */
    private String resolveCorrelationId(RoutingContext ctx) {
        String fromHeader = trimToMax(ctx.request().getHeader(transactionIdHttpHeader), MAX_CORRELATION_ID);
        if (fromHeader != null && !fromHeader.isEmpty()) {
            return fromHeader;
        }
        Object tid = ctx.get(ConstantKeys.TRANSACTION_ID_KEY);
        if (tid != null) {
            String fromContext = trimToMax(tid.toString(), MAX_CORRELATION_ID);
            if (fromContext != null && !fromContext.isEmpty()) {
                return fromContext;
            }
        }
        return null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimToMax(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
