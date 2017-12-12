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
package io.gravitee.am.gateway.handler.oauth2.controller;

import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class LoginController {

    private final static String LOGIN_VIEW = "login";

    @Autowired
    private ClientService clientService;

    @Autowired
    private Domain domain;

    @RequestMapping(value = "/login")
    public ModelAndView login(
            @RequestParam(value = OAuth2Utils.CLIENT_ID) String clientId) {
        Client client = clientService.findByDomainAndClientId(domain.getId(), clientId);
        Map<String,Object> params = new HashMap<>();
        params.put(OAuth2Utils.CLIENT_ID, client.getClientId());
        params.put("domain", domain);
        return new ModelAndView(LOGIN_VIEW, params);
    }
}
