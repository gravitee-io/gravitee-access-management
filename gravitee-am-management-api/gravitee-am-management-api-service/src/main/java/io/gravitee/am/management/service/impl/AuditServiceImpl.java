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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementAuditService")
public class AuditServiceImpl implements AuditService {

    public static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    @Autowired
    private AuditReporterManager auditReporterManager;

    @Override
    public Single<Page<Audit>> search(String domain, AuditReportableCriteria criteria, int page, int size) {
        try {
            return getReporter(domain).search(criteria, page, size);
        } catch (Exception ex) {
            logger.error("An error occurs during audits search for domain: {}", domain, ex);
            return Single.error(ex);
        }
    }

    @Override
    public Maybe<Audit> findById(String domain, String auditId) {
        try {
            return getReporter(domain).findById(auditId);
        } catch (Exception ex) {
            logger.error("An error occurs while trying to find audit by id: {} and for the domain: {}", auditId, domain, ex);
            return Maybe.error(ex);
        }
    }

    private Reporter getReporter(String domain) {
        return auditReporterManager.getReporter(domain);
    }
}
