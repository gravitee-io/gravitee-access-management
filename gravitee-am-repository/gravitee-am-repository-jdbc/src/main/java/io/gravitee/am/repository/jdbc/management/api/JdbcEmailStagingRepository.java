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

import io.gravitee.am.repository.common.EmailStagingRepository;
import io.gravitee.am.repository.jdbc.common.AbstractEmailStagingRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEmailStaging;
import org.springframework.stereotype.Repository;

@Repository("managementEmailStagingRepository")
public class JdbcEmailStagingRepository extends AbstractEmailStagingRepository<JdbcEmailStaging> implements EmailStagingRepository {

    public JdbcEmailStagingRepository() {
        super(JdbcEmailStaging.class, JdbcEmailStaging::new, "cp_email_staging");
    }
}
