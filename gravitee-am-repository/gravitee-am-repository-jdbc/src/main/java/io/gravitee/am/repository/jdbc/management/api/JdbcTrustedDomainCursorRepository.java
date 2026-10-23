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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.TrustedDomainCursorRequest;
import io.gravitee.am.model.oidc.TrustedDomainUsage;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcCursorRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain;
import io.gravitee.am.repository.management.api.TrustedDomainCursorRepository;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class JdbcTrustedDomainCursorRepository
        extends AbstractJdbcCursorRepository<JdbcTrustedDomain, TrustedDomain, TrustedDomainCursorRequest>
        implements TrustedDomainCursorRepository {

    private static final String SORT_FIELD_NAME = "name";

    @Autowired
    private JdbcTrustedDomainRepository trustedDomainRepository;

    @Override
    public Single<CursorPage<TrustedDomain, TrustedDomainCursorRequest>> findByReferenceCursor(ReferenceType referenceType, String referenceId, TrustedDomainCursorRequest cursor, int limit) {
        return findCursorPage(buildQuery(referenceType, referenceId, cursor.getUsage()), cursor, limit);
    }

    private CursorQuerySpec buildQuery(ReferenceType referenceType, String referenceId, TrustedDomainUsage usage) {
        String filter = "td.reference_type = :refType AND td.reference_id = :refId" + usageCriteria(usage);
        boolean bindEnabled = usage == TrustedDomainUsage.TOKEN_EXCHANGE || usage == TrustedDomainUsage.CROSS_APP_ACCESS;
        return new CursorQuerySpec(
                "SELECT td.* FROM trusted_domains td WHERE " + filter,
                "SELECT COUNT(*) FROM trusted_domains td WHERE " + filter,
                spec -> {
                    spec = spec.bind("refType", referenceType.name()).bind("refId", referenceId);
                    return bindEnabled ? spec.bind("enabled", true) : spec;
                });
    }

    private static String usageCriteria(TrustedDomainUsage usage) {
        if (usage == null) {
            return "";
        }
        return switch (usage) {
            case SPIFFE -> " AND EXISTS (SELECT 1 FROM trusted_domains_spiffe u"
                    + " WHERE u.trusted_domain_id = td.id AND u.spiffe_trust_domain IS NOT NULL)";
            case TOKEN_EXCHANGE -> " AND td.domain_identifier IS NOT NULL AND EXISTS (SELECT 1 FROM trusted_domains_token_exchange u"
                    + " WHERE u.trusted_domain_id = td.id AND u.enabled = :enabled)";
            case CROSS_APP_ACCESS -> " AND EXISTS (SELECT 1 FROM trusted_domains_xaa u"
                    + " WHERE u.trusted_domain_id = td.id AND u.enabled = :enabled)";
        };
    }

    @Override
    protected TrustedDomain toEntity(JdbcTrustedDomain row) {
        return trustedDomainRepository.toEntity(row);
    }

    @Override
    protected Single<TrustedDomain> completeEntity(TrustedDomain entity) {
        return trustedDomainRepository.complete(entity);
    }

    @Override
    protected Class<JdbcTrustedDomain> rowType() {
        return JdbcTrustedDomain.class;
    }

    @Override
    protected String idColumn() {
        return "td.id";
    }

    @Override
    protected String sortColumn(TrustedDomainCursorRequest cursor) {
        String sortField = cursor.getSortField() != null ? cursor.getSortField() : SORT_FIELD_NAME;
        if (!SORT_FIELD_NAME.equals(sortField)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortField);
        }
        return "LOWER(td.name)";
    }

    @Override
    protected Object sortFieldValue(TrustedDomainCursorRequest cursor) {
        return cursor.getLastSortValue().toLowerCase(Locale.ROOT);
    }

    @Override
    protected TrustedDomainCursorRequest nextCursor(TrustedDomainCursorRequest currentCursor, TrustedDomain element) {
        return new TrustedDomainCursorRequest(
                element.getName(),
                element.getId(),
                currentCursor.getSortDirection(),
                currentCursor.getSortField(),
                currentCursor.getPage() + 1,
                currentCursor.getUsage());
    }
}
