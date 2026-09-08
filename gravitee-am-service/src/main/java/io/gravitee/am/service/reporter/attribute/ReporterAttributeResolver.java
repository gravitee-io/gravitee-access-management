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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.model.ReporterAttributeMapping;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEnrichmentContext;
import io.gravitee.el.TemplateEngine;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Evaluates the attribute mappings configured on a reporter against one audit event, producing the extra
 * fields that reporter exports.
 *
 * <p>Each mapping is evaluated in isolation, so one that fails to resolve does not affect the rest.
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class ReporterAttributeResolver {

    static final String CONTEXT_VARIABLE = "context";

    static final String USER_KEY = "user";
    static final String CLIENT_KEY = "client";
    static final String REQUEST_KEY = "request";
    static final String AUDIT_KEY = "audit";

    private final SensitiveAttributeDenylist denylist;

    public ReporterAttributeResolver(SensitiveAttributeDenylist denylist) {
        this.denylist = denylist;
    }

    /**
     * @return the resolved attributes keyed by the operator's chosen export name, in declaration order
     */
    public Map<String, Object> resolve(List<ReporterAttributeMapping> mappings, Audit audit) {
        if (mappings == null || mappings.isEmpty() || audit == null) {
            return Map.of();
        }

        TemplateEngine engine = engineFor(audit);

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (ReporterAttributeMapping mapping : mappings) {
            if (mapping == null || mapping.expression() == null || mapping.exportedName() == null) {
                continue;
            }
            evaluate(engine, mapping.expression(), audit)
                    .filter(value -> {
                        if (ExportableValue.isExportable(value)) {
                            return true;
                        }
                        log.debug("Reporter attribute mapping '{}' resolves to something other than a single value, skipping it for audit {}",
                                mapping.expression(), audit.getId());
                        return false;
                    })
                    .ifPresent(value -> resolved.put(mapping.exportedName(), value));
        }
        return resolved;
    }

    private Optional<Object> evaluate(TemplateEngine engine, String expression, Audit audit) {
        try {
            return Optional.ofNullable(engine.getValue(expression, Object.class));
        } catch (Exception ex) {
            log.debug("Unable to resolve reporter attribute mapping '{}' for audit {}", expression, audit.getId(), ex);
            return Optional.empty();
        }
    }

    private TemplateEngine engineFor(Audit audit) {
        TemplateEngine engine = TemplateEngine.templateEngine();
        engine.getTemplateContext().setVariable(CONTEXT_VARIABLE, new EvaluableAuditContext(attributesOf(audit)));
        return engine;
    }

    /**
     * The attribute names an operator can address in a mapping expression.
     */
    private Map<String, Object> attributesOf(Audit audit) {
        Map<String, Object> attributes = new HashMap<>();

        AuditEnrichmentContext context = audit.getEnrichmentContext();
        if (context != null) {
            attributes.put(USER_KEY, scrub(project(context::user, audit)));
            attributes.put(CLIENT_KEY, scrub(project(context::client, audit)));
        }

        AuditAccessPoint accessPoint = audit.getAccessPoint();
        if (accessPoint != null) {
            Map<String, Object> request = new HashMap<>();
            request.put("ip", accessPoint.getIpAddress());
            request.put("userAgent", accessPoint.getUserAgent());
            attributes.put(REQUEST_KEY, request);
        }

        Map<String, Object> auditAttributes = new HashMap<>();
        auditAttributes.put("id", audit.getId());
        auditAttributes.put("type", audit.getType());
        auditAttributes.put("transactionId", audit.getTransactionId());
        if (audit.getOutcome() != null) {
            auditAttributes.put("status", audit.getOutcome().getStatus());
        }
        attributes.put(AUDIT_KEY, auditAttributes);

        return attributes;
    }

    /**
     * @return the projection with its free-form maps filtered, or null if it could not be filtered
     */
    private Object scrub(Object projection) {
        try {
            if (projection instanceof UserProperties user) {
                // the nested maps here belong to the audited User
                // claims and additionalInformation are one instance in UserProperties
                Map<String, Object> claims = denylist.scrubbedCopyOf(user.getClaims());
                user.setClaims(claims);
                user.setAdditionalInformation(claims);
                Optional.ofNullable(user.getIdentities())
                        .ifPresent(identities -> identities.forEach(identity ->
                                identity.setAdditionalInformation(denylist.scrubbedCopyOf(identity.getAdditionalInformation()))));
            } else if (projection instanceof ClientProperties client) {
                client.setMetadata(denylist.scrubbedCopyOf(client.getMetadata()));
            }
            return projection;
        } catch (Exception ex) {
            log.warn("Unable to filter sensitive attributes out of {}, withholding it from attribute mappings",
                    projection.getClass().getSimpleName(), ex);
            return null;
        }
    }

    /**
     * Isolated so a projection that throws does not cost the mappings that never referenced it.
     */
    private static Object project(Supplier<?> projection, Audit audit) {
        try {
            return projection.get();
        } catch (Exception ex) {
            log.debug("Unable to project audit {} for attribute mapping", audit.getId(), ex);
            return null;
        }
    }
}
