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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.command.Command;
import io.gravitee.am.common.oidc.command.CommandConstants;
import io.gravitee.am.gateway.handler.common.command.impl.CommandTokenServiceImpl;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainReadService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommandTokenServiceImplTest {

    @InjectMocks
    private CommandTokenServiceImpl commandTokenService = new CommandTokenServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private JWTService jwtService;

    @Mock
    private DomainReadService domainReadService;

    @Test
    public void shouldMintCommandTokenWithSpecClaims() {
        when(domain.getHrid()).thenReturn("my-domain");
        when(domainReadService.buildUrl(eq(domain), eq("/oidc"))).thenReturn("https://gw.example.com/my-domain/oidc");
        var jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encodeCommand(jwtCaptor.capture(), any())).thenReturn(Single.just("signed-command-token"));

        final var staging = new CommandStaging();
        staging.setId("command-1");
        staging.setCommand(Command.INVALIDATE.value());
        staging.setUserId("user-1");

        final var client = new Client();
        client.setClientId("client-1");
        client.setCommandEndpoint("https://rp.example.com/commands");

        commandTokenService.mintToken(staging, client)
                .test()
                .assertComplete()
                .assertValue("signed-command-token");

        final JWT jwt = jwtCaptor.getValue();
        assertEquals("https://gw.example.com/my-domain/oidc", jwt.getIss());
        // per spec, the audience of a command token is the command endpoint URL
        assertEquals("https://rp.example.com/commands", jwt.getAud());
        assertEquals("client-1", jwt.get(Claims.CLIENT_ID));
        assertEquals("user-1", jwt.getSub());
        assertEquals("invalidate", jwt.get(CommandConstants.COMMAND_CLAIM));
        assertEquals("my-domain", jwt.get(CommandConstants.TENANT_CLAIM));
        assertNotNull(jwt.getJti());
        assertTrue(jwt.getIat() <= Instant.now().getEpochSecond());
        assertTrue(jwt.getExp() > jwt.getIat());
        // the spec prohibits nonce in command tokens
        assertFalse(jwt.containsKey("nonce"));
    }
}
