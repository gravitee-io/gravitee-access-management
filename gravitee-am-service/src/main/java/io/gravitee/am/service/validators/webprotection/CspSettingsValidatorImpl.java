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
package io.gravitee.am.service.validators.webprotection;

import io.gravitee.am.model.webprotection.CspDirective;
import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.model.webprotection.WebProtectionResolution;
import io.gravitee.am.model.webprotection.WebProtectionSettingsResolver;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service-layer validator for {@link CspSettings}.
 * <p>
 * Only the shape of the directive list is validated: names must be syntactically valid CSP tokens and
 * must not repeat, values must be present where the directive requires one, and report-only mode must
 * name a report target. Source-list grammar ({@code 'self'}, hosts, nonces, hashes) is deliberately
 * not interpreted.
 * <p>
 * Directive names are compared case-insensitively per the CSP specification, but the operator's input
 * is never rewritten — the gateway parser canonicalizes when it builds the policy map.
 *
 * @author GraviteeSource Team
 */
@Component
public class CspSettingsValidatorImpl implements CspSettingsValidator {

    @Override
    public Completable validate(CspSettings settings) {
        if (WebProtectionSettingsResolver.resolve(settings) != WebProtectionResolution.ENABLED) {
            return Completable.complete();
        }

        final List<String> entries = settings.getDirectives();
        if (entries == null || entries.isEmpty()) {
            return error("At least one CSP directive is required when CSP is enabled");
        }

        final Set<String> seenNames = new HashSet<>();
        boolean hasReportTarget = false;

        for (String entry : entries) {
            final Completable failure = validateEntry(entry, seenNames);
            if (failure != null) {
                return failure;
            }
            final CspDirective directive = CspDirective.parse(entry);
            hasReportTarget |= directive.isReportTarget() && directive.hasValue();
        }

        if (settings.isReportOnly() && !hasReportTarget) {
            return error("Report-only mode requires a 'report-uri' or 'report-to' directive with a value, " +
                    "otherwise the login page cannot be served");
        }

        return Completable.complete();
    }

    /**
     * @return a failed {@link Completable} describing the problem, or {@code null} when the entry is valid
     */
    private Completable validateEntry(String entry, Set<String> seenNames) {
        final CspDirective directive = CspDirective.parse(entry);
        if (directive == null) {
            return error("CSP directives must not be blank");
        }

        if (CspDirective.stripTrailingSemicolon(entry).contains(";")) {
            return error("CSP directive '%s' must not contain ';', configure one directive per entry"
                    .formatted(directive.name()));
        }

        final String canonicalName = directive.canonicalName();
        if (!CspDirective.isValidName(canonicalName)) {
            return error("'%s' is not a valid CSP directive name, names may contain only letters, digits and hyphens"
                    .formatted(directive.name()));
        }

        if (CspDirective.hasIllegalHeaderCharacter(directive.value())) {
            return error("CSP directive '%s' must not contain control characters".formatted(directive.name()));
        }

        if (CspDirective.requiresValue(canonicalName) && !directive.hasValue()) {
            return error("CSP directive '%s' requires a value".formatted(directive.name()));
        }

        if (!seenNames.add(canonicalName)) {
            return error("CSP directive '%s' is configured more than once".formatted(directive.name()));
        }

        return null;
    }

    private Completable error(String message) {
        return Completable.error(new InvalidDomainException(message));
    }
}
