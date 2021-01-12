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
package io.gravitee.am.management.services.purge;

import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.am.repository.management.api.LoginAttemptRepository;
import io.gravitee.am.repository.management.api.PermissionTicketRepository;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Singleton
public class PurgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurgeManager.class);

    @Lazy
    @Autowired
    protected LoginAttemptRepository loginAttemptRepository;
    @Lazy
    @Autowired
    protected PermissionTicketRepository permissionTicketRepository;
    @Lazy
    @Autowired
    protected AccessTokenRepository accessTokenRepository;
    @Lazy
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Lazy
    @Autowired
    protected RequestObjectRepository requestObjectRepository;
    @Lazy
    @Autowired
    protected ScopeApprovalRepository scopeApprovalRepository;
    @Lazy
    @Autowired
    protected AuthorizationCodeRepository authorizationCodeRepository;
    @Lazy
    @Autowired
    protected AuthenticationFlowContextRepository authenticationFlowContextRepository;

    protected List<TableName> tables = asList(TableName.values());

    public void purge(List<TableName> exclude) {
        List<TableName> tableToProcess = tables.stream()
                .filter(t -> !ofNullable(exclude).orElse(emptyList()).contains(t))
                .collect(Collectors.toList());

        for (TableName toProcess : tableToProcess) {
            LOGGER.debug("Purging expired data for table '{}'", toProcess);
            switch (toProcess) {
                case access_tokens:
                    accessTokenRepository.purgeExpiredData().subscribe();
                    break;
                case authorization_codes:
                    authorizationCodeRepository.purgeExpiredData().subscribe();
                    break;
                case refresh_tokens:
                    refreshTokenRepository.purgeExpiredData().subscribe();
                    break;
                case request_objects:
                    requestObjectRepository.purgeExpiredData().subscribe();
                    break;
                case scope_approvals:
                    scopeApprovalRepository.purgeExpiredData().subscribe();
                    break;
                case login_attempts:
                    loginAttemptRepository.purgeExpiredData().subscribe();
                    break;
                case uma_permission_ticket:
                    permissionTicketRepository.purgeExpiredData().subscribe();
                    break;
                case auth_flow_ctx:
                    authenticationFlowContextRepository.purgeExpiredData().subscribe();
                    break;
            }
        }
    }
}
