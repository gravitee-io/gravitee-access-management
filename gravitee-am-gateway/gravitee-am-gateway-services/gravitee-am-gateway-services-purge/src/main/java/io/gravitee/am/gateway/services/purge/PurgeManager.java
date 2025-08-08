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
package io.gravitee.am.gateway.services.purge;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.plugins.dataplane.core.SingleDataPlaneProvider;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PurgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurgeManager.class);

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
    protected AuthorizationCodeRepository authorizationCodeRepository;
    @Lazy
    @Autowired
    protected AuthenticationFlowContextRepository authenticationFlowContextRepository;
    @Lazy
    @Autowired
    protected PushedAuthorizationRequestRepository pushedAuthRequestRepository;
    @Lazy
    @Autowired
    protected CibaAuthRequestRepository cibaAuthRequestRepository;
    @Lazy
    @Autowired
    protected SingleDataPlaneProvider singleDataPlaneProvider;
    @Lazy
    @Autowired
    protected EventRepository eventRepository;

    protected List<TableName> tables = asList(TableName.values());

    public void purge(List<TableName> exclude) {
        List<TableName> tableToProcess = tables.stream()
                .filter(t -> !ofNullable(exclude).orElse(emptyList()).contains(t))
                .collect(Collectors.toList());

        for (TableName toProcess : tableToProcess) {
            expiredDataSweeper(toProcess)
                    .purgeExpiredData()
                    .subscribe(() -> LOGGER.debug("Purged expired data for table '{}'", toProcess));
        }
    }

    private ExpiredDataSweeper expiredDataSweeper(TableName tableName){
        return switch (tableName) {
            case access_tokens -> accessTokenRepository;
            case authorization_codes -> authorizationCodeRepository;
            case refresh_tokens -> refreshTokenRepository;
            case request_objects -> requestObjectRepository;
            case auth_flow_ctx -> authenticationFlowContextRepository;
            case pushed_authorization_requests -> pushedAuthRequestRepository;
            case ciba_auth_requests -> cibaAuthRequestRepository;
            case events ->  eventRepository;

            case scope_approvals -> singleDataPlaneProvider.get().getScopeApprovalRepository();
            case login_attempts -> singleDataPlaneProvider.get().getLoginAttemptRepository();
            case uma_permission_ticket -> singleDataPlaneProvider.get().getPermissionTicketRepository();
            case user_activities -> singleDataPlaneProvider.get().getUserActivityRepository();
            case devices -> singleDataPlaneProvider.get().getDeviceRepository();
        };
    }
}
