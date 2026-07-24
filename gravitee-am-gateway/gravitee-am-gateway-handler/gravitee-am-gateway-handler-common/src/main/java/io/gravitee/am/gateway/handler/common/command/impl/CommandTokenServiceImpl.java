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
package io.gravitee.am.gateway.handler.common.command.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.command.CommandConstants;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.command.CommandTokenService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainReadService;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;

/**
 * Assembles command token claims per OpenID Provider Commands 1.0 (draft) and signs
 * them with the same certificate the domain uses for the client's ID tokens.
 * Per spec prohibition, command tokens never carry a nonce.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class CommandTokenServiceImpl implements CommandTokenService {

    private static final String OIDC_PATH = "/oidc";
    private static final int DEFAULT_TOKEN_VALIDITY_IN_SECONDS = 120;

    @Autowired
    private Domain domain;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private DomainReadService domainReadService;

    @Value("${commands.dispatch.tokenValidity:" + DEFAULT_TOKEN_VALIDITY_IN_SECONDS + "}")
    private int tokenValidityInSeconds = DEFAULT_TOKEN_VALIDITY_IN_SECONDS;

    @Override
    public Single<String> mintToken(CommandStaging commandStaging, Client client) {
        final var now = Instant.now();
        final var jwt = new JWT();
        // same issuer value as the OIDC discovery metadata of the domain
        jwt.setIss(domainReadService.buildUrl(domain, OIDC_PATH));
        // per spec, the audience of a command token is the command endpoint URL
        jwt.setAud(client.getCommandEndpoint());
        jwt.put(Claims.CLIENT_ID, client.getClientId());
        jwt.setSub(commandStaging.getUserId());
        jwt.setIat(now.getEpochSecond());
        jwt.setExp(now.plusSeconds(tokenValidityInSeconds).getEpochSecond());
        jwt.setJti(SecureRandomString.generate());
        jwt.put(CommandConstants.COMMAND_CLAIM, commandStaging.getCommand());
        jwt.put(CommandConstants.TENANT_CLAIM, domain.getHrid() != null ? domain.getHrid() : domain.getId());
        return jwtService.encodeCommand(jwt, client);
    }
}
