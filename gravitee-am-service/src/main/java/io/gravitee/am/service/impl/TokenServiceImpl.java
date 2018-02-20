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
package io.gravitee.am.service.impl;

import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.TotalToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TokenServiceImpl implements TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenServiceImpl.class);

    @Autowired
    private ClientService clientService;

    @Autowired
    private TokenRepository tokenRepository;

    @Override
    public TotalToken findTotalTokensByDomain(String domain) {
        try {
            LOGGER.debug("Find total tokens by domain: {}", domain);
            TotalToken totalToken = new TotalToken();
            totalToken.setTotalAccessTokens(clientService.findByDomain(domain)
                    .parallelStream()
                    .mapToLong(c -> tokenRepository.findTokensByClientId(c.getClientId()).size()).sum());
            return totalToken;
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to find total tokens by domain: {}", domain, ex);
            throw new TechnicalManagementException(
                    String.format("An error occurs while trying to find total tokens by domain: %s", domain), ex);
        }
    }

    @Override
    public TotalToken findTotalTokens() {
        try {
            LOGGER.debug("Find total tokens");
            TotalToken totalToken = new TotalToken();
            totalToken.setTotalAccessTokens(clientService.findAll()
                    .parallelStream()
                    .mapToLong(c -> tokenRepository.findTokensByClientId(c.getClientId()).size()).sum());
            return totalToken;
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to find total tokens", ex);
            throw new TechnicalManagementException("An error occurs while trying to find total tokens", ex);
        }
    }
}
