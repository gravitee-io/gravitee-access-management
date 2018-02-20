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
package io.gravitee.am.management.handlers.oauth2.provider.endpoint;

import io.gravitee.am.identityprovider.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
@RequestMapping("/userinfo")
public class UserInfoEndpoint {

    private final Logger logger = LoggerFactory.getLogger(UserInfoEndpoint.class);

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public Object loginInfo(OAuth2Authentication oAuth2Authentication) {
        if (oAuth2Authentication == null) {
            return null;
        }

        try {
            User user = (User) oAuth2Authentication.getUserAuthentication().getPrincipal();
            return user.getAdditionalInformation();
        } catch (Exception e) {
            logger.warn("Failed to get user profile information, fallback to default user authentication", e);
            return oAuth2Authentication.getUserAuthentication().getPrincipal();
        }
    }
}
