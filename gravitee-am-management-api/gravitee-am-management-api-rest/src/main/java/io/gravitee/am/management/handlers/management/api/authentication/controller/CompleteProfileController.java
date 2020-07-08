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
package io.gravitee.am.management.handlers.management.api.authentication.controller;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.NewsletterService;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator.DEFAULT_REDIRECT_COOKIE_NAME;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class CompleteProfileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompleteProfileController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private NewsletterService newsletterService;

    @Autowired
    private RedirectStrategy redirectStrategy;

    @Value("${newsletter.policy-uri:https://gravitee.io/privacy}")
    private String privacyPolicyURI;

    @RequestMapping(value = "/completeProfile", method = RequestMethod.GET)
    public ModelAndView showForm(Authentication authentication) {
        // get current principal
        DefaultUser principal = (DefaultUser) authentication.getPrincipal();
        User endUser = userService.findById(principal.getId()).blockingGet();
        Map<String, Object> model = new HashMap<>();
        model.put("user", endUser);
        model.put("privacyPolicyURI", privacyPolicyURI);

        return new ModelAndView(Template.COMPLETE_PROFILE.template(), model);
    }

    @RequestMapping(value = "/completeProfile", method = RequestMethod.POST)
    public void submit(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            // get current principal
            DefaultUser principal = (DefaultUser) authentication.getPrincipal();
            // get request form parameters
            Map<String, String[]> parameters = request.getParameterMap();

            // update user
            User updatedUser = updateUser(parameters, principal);

            // subscribe to newsletter if requested
            if (updatedUser.getEmail() != null && updatedUser.isNewsletter()) {
                subscribeNewsletter(updatedUser);
            }

            // Redirect to the original request.
            redirectStrategy.sendRedirect(request, response, (String) request.getAttribute(DEFAULT_REDIRECT_COOKIE_NAME));
        } catch (Exception ex) {
            LOGGER.error("An error occurs while completing user profile", ex);
            redirectStrategy.sendRedirect(request, response, "/auth/completeProfile?error");
        }
    }

    private User updateUser(Map<String, String[]> parameters, DefaultUser principal) {
        User endUser = userService.findById(principal.getId()).blockingGet();

        String email = parameters.get(StandardClaims.EMAIL) != null ? parameters.get(StandardClaims.EMAIL)[0] : null;
        String firstName = parameters.get(StandardClaims.GIVEN_NAME) != null ? parameters.get(StandardClaims.GIVEN_NAME)[0] : null;
        String lastName = parameters.get(StandardClaims.FAMILY_NAME) != null ? parameters.get(StandardClaims.FAMILY_NAME)[0] : null;
        boolean newsletter = parameters.get("newsletter") == null ? false : "on".equals(parameters.get("newsletter")[0]) ? true : false;

        // update values
        Map<String, Object> additionalInformation = new HashMap<>(endUser.getAdditionalInformation());
        if (email != null && !email.isEmpty()) {
            endUser.setEmail(email);
        }
        if (firstName != null && !firstName.isEmpty()) {
            endUser.setFirstName(firstName);
            additionalInformation.put(StandardClaims.GIVEN_NAME, firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            endUser.setLastName(lastName);
            additionalInformation.put(StandardClaims.FAMILY_NAME, lastName);
        }
        endUser.setAdditionalInformation(additionalInformation);
        endUser.setNewsletter(newsletter);
        return userService.update(endUser).blockingGet();
    }

    private void subscribeNewsletter(User endUser) {
        Map<String, Object> object = new HashMap<>();
        object.put("email", endUser.getEmail());
        newsletterService.subscribe(object);
    }
}
