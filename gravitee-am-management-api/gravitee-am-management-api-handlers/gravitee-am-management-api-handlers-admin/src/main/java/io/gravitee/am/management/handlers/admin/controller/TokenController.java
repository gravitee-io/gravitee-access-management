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
package io.gravitee.am.management.handlers.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.admin.provider.jwt.JWTGenerator;
import io.gravitee.am.management.handlers.admin.service.AuthenticationService;
import io.gravitee.common.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class TokenController {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    private AuthenticationService authenticationService;

    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public void token(HttpServletResponse response, Authentication authentication) throws IOException {
        // prepare response
        response.setContentType(MediaType.APPLICATION_JSON);
        ServletOutputStream out = response.getOutputStream();
        // connect user
        io.gravitee.am.model.User endUser = authenticationService.onAuthenticationSuccess(authentication);
        // enhance principal with user information
        final User principal = (User) authentication.getPrincipal();
        ((DefaultUser) principal).setId(endUser.getId());
        principal.getAdditionalInformation().put(StandardClaims.SUB, endUser.getId());
        principal.getAdditionalInformation().put(Claims.domain, endUser.getReferenceId());
        Set<String> roles = endUser.getRoles() != null ? new HashSet<>(endUser.getRoles()) : new HashSet<>();
        if (principal.getRoles() != null) {
            roles.addAll(principal.getRoles());
        }
        principal.getAdditionalInformation().put(CustomClaims.ROLES, roles);
        // generate token
        Map<String, Object> token = jwtGenerator.generateToken(principal);
        String output = objectMapper.writeValueAsString(token);
        out.print(output);
    }
}
