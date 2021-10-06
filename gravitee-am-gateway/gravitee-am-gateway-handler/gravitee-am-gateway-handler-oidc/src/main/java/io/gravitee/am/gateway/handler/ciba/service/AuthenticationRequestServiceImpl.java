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
package io.gravitee.am.gateway.handler.ciba.service;

import io.gravitee.am.gateway.handler.ciba.service.request.AuthenticationRequestStatus;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestServiceImpl implements AuthenticationRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestServiceImpl.class);

    @Autowired
    private CibaAuthRequestRepository authRequestRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Override
    public Single<CibaAuthRequest> register(CibaAuthenticationRequest request, Client client) {
        Instant now = Instant.now();
        final Integer requestedExpiry = request.getRequestedExpiry();
        final long ttl = requestedExpiry != null ? requestedExpiry: domain.getOidc().getCibaSettings().getAuthReqExpiry();

        CibaAuthRequest entity = new CibaAuthRequest();
        entity.setClientId(client.getId());
        entity.setId(request.getId());
        entity.setScopes(request.getScopes());
        entity.setSubject(request.getSubject());
        entity.setUserCode(request.getUserCode());
        entity.setStatus(AuthenticationRequestStatus.ONGOING.name());
        entity.setCreatedAt(new Date(now.toEpochMilli()));
        entity.setExpireAt(new Date(now.plusSeconds(ttl).toEpochMilli()));

        LOGGER.debug("Register AuthenticationRequest with auth_req_id '{}' and expiry of '{}' seconds", entity.getId(), ttl);

        return authRequestRepository.create(entity);
    }
}
