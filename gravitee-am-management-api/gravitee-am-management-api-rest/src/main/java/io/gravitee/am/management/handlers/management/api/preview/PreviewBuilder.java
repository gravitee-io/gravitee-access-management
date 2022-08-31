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
package io.gravitee.am.management.handlers.management.api.preview;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.model.PreviewRequest;
import io.gravitee.am.management.handlers.management.api.model.PreviewResponse;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.exception.InvalidGroupException;
import io.gravitee.am.service.theme.ThemeResolution;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.exceptions.TemplateOutputException;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DOMAIN_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PreviewBuilder {

    private final TemplateEngine templateEngine;
    private final TemplateResolver templateResolver;
    private PreviewRequest request;
    private Domain domain;
    private Client client;
    private ThemeResolution theme;
    private Locale locale;

    public PreviewBuilder(TemplateEngine templateEngine, TemplateResolver templateResolver) {
        this.templateEngine = templateEngine;
        this.templateResolver = templateResolver;
    }

    public PreviewBuilder withDomain(Domain domain) {
        this.domain = domain;
        return this;
    }

    public Domain getDomain() {
        return domain;
    }

    public PreviewBuilder withRequest(PreviewRequest previewRequest) {
        this.request = previewRequest;
        return this;
    }

    public PreviewBuilder withClient(Application app) {
        this.client = app.toClient();
        return this;
    }

    public PreviewBuilder withTheme(ThemeResolution theme) {
        this.theme = theme;
        return this;
    }

    public PreviewBuilder withLocale(Locale locale) {
        this.locale = locale;
        return this;
    }

    public PreviewResponse buildPreview() {
        Map<String, Object> variables = generateTemplateVariables(this.request.getTemplate());
        variables.put(DOMAIN_CONTEXT_KEY, new DomainProperties(this.domain));
        if (this.client == null) {
            this.client = generateFakeApplication();
        }
        variables.put(CLIENT_CONTEXT_KEY, new ClientProperties(this.client));
        variables.put(USER_CONTEXT_KEY, generateFakeUser());
        variables.put("theme", this.theme);

        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setLocale(this.locale);
        context.setVariables(variables);

        Form previewForm = new Form(true, request.getTemplate());
        previewForm.setContent(request.getContent());
        previewForm.setReferenceId(this.domain.getId());
        previewForm.setReferenceType(ReferenceType.DOMAIN);

        final String previewId = "preview-" + UUID.randomUUID();
        previewForm.setReferenceId(previewId);

        this.templateResolver.addForm(previewForm);
        try {
            final String processedTemplate = templateEngine.process(this.templateResolver.getTemplateKey(previewForm), context);
            previewForm.setContent(processedTemplate);
        } catch (TemplateEngineException e) {
            throw new PreviewException("Unable to render the preview : \n" + e.getMessage());
        } finally {
            this.templateResolver.removeForm(previewForm);
        }

        return new PreviewResponse(previewForm.getContent(), this.request.getType(), this.request.getTemplate());
    }

    private Map<String, Object> generateTemplateVariables(String template) {
        final Map<String, Object> variables = new HashMap<>();

        variables.put(ConstantKeys._CSRF, Map.of("parameterName", "", "token", ""));
        variables.put(ConstantKeys.PARAM_CONTEXT_KEY, Map.of("username", "", "client_id", ""));
        variables.put(ConstantKeys.ACTION_KEY, "");

        switch (Template.parse(template)) {
            case LOGIN:
                variables.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, Collections.EMPTY_LIST);
                variables.put(ConstantKeys.TEMPLATE_KEY_FORGOT_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_WEBAUTHN_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_REGISTER_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_BACK_LOGIN_IDENTIFIER_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, "");
                variables.put(ConstantKeys.DEVICE_IDENTIFIER_PROVIDER_KEY, "");
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, "false");
                variables.put(ConstantKeys.TEMPLATE_KEY_HIDE_FORM_CONTEXT_KEY, "false");
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_REGISTER_CONTEXT_KEY, "true");
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_PASSWORDLESS_CONTEXT_KEY, "true");
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, "true");
                variables.put(ConstantKeys.TEMPLATE_KEY_IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY, "false");
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of("siteKey", ""));
                break;

            case REGISTRATION:
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, "");
                variables.put(ConstantKeys.LOGIN_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of("siteKey", ""));
                break;

            case REGISTRATION_CONFIRMATION:
                variables.put(ConstantKeys.TOKEN_CONTEXT_KEY, "");
                break;

            case FORGOT_PASSWORD:
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, "");
                variables.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, Arrays.asList(FormField.getEmailField()));
                variables.put(ConstantKeys.LOGIN_ACTION_KEY, "");
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of("siteKey", ""));
                break;

            case MFA_ENROLL:
                final Enrollment otpEnrollment = new Enrollment();
                otpEnrollment.setBarCode("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIAQAAAACFI5MzAAABuElEQVR4Xu2WWWoEMQwFDb6WwVc36FqGTpXCLAzJn5X8tGiGtmtA27Pc7frN2ufG025yE+0vyWqtr75GXzP2mC5LSPCsvtme1+65rCGrR6y528A17zxlZI1htpNMS8l17abvaxcSntgN749y1hAlES/70M45opEgAkGKoXftPFkzK3nFtpSI/hXbWdJtGwk2TtXgZ8wSEh7f5vkFjGxgCUHtnt6lRnCfMikh6d2WpeSbBa4gTgl6Rr7T0rouISxpGnvB4HPQPit6lviG4q0s7sU1xFKaXeSfmOmEUkIQIJ1ja3SS5vUZwVGCWwafVxOnC8W3WUSmOSJ6JyBlHYnOE66+ruCppH9x3NaQHgoju+eYWK8IjpLIGLYtW770SztPnOK2zVpmE58RHCare6xUh8G8TaSzhEKSLRE0QnBkvGnnJGGbSjqV8rJ1p4gs76O58iC7fijxLKGgXOYL5+EXkaGUEI1LI2cFT1a4giw/sUI9ZjBegyUkrKmKN4TUyqwhWVL2bVzk/V5GLpsH8ihT2zqS5yny20v/JYQnch7x6UVpvyVynqgQ9Q70gLF+ZHqW/Gw3uYn2/+QLiTKJ//OwuCgAAAAASUVORK5CYII=");
                final Map<String, Object> factorOTP = Map.of("id", "idotp" , "factorType", FactorType.OTP.getType(), "enrollment", otpEnrollment);

                final Enrollment smsEnrollment = new Enrollment();
                smsEnrollment.setCountries(List.of("us", "en", "fr"));
                final Map<String, Object> factorSms = Map.of("id", "idsms" ,"factorType", FactorType.SMS.getType(), "enrollment", smsEnrollment);

                final Enrollment emailEnrollment = new Enrollment();
                emailEnrollment.setKey("");
                final Map<String, Object> factorEmail = Map.of("id", "idemail", "factorType", FactorType.EMAIL.getType(), "enrollment", emailEnrollment);

                variables.put(ConstantKeys.FACTORS_KEY, List.of(factorOTP, factorSms, factorEmail));
                break;

            case MFA_CHALLENGE:
                final Factor factor = new Factor();
                factor.setFactorType(FactorType.OTP);
                variables.put(ConstantKeys.FACTOR_KEY, factor);
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, "false");
                variables.put(ConstantKeys.MFA_ALTERNATIVES_ENABLE_KEY, "true");
                variables.put(ConstantKeys.MFA_ALTERNATIVES_ACTION_KEY, "");
                break;

            case WEBAUTHN_LOGIN:
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, "false");
                break;

            case ERROR:
                variables.put(ERROR_PARAM_KEY, "login_failed");
                variables.put(ERROR_DESCRIPTION_PARAM_KEY, "Wrong user or password");
                break;
            // template without specific variables
            case RESET_PASSWORD:
            case OAUTH2_USER_CONSENT:
            case MFA_CHALLENGE_ALTERNATIVES:
            case MFA_RECOVERY_CODE:
            case BLOCKED_ACCOUNT:
            case COMPLETE_PROFILE:
            case WEBAUTHN_REGISTER:
            case IDENTIFIER_FIRST_LOGIN:
            case CERTIFICATE_EXPIRATION:
            default:
                break;
        }
        return variables;
    }

    private UserProperties generateFakeUser() {
        final UserProperties user = new UserProperties();
        user.setDomain(this.domain.getId());
        user.setEmail("john.doe@mycompany.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setClaims(Map.of());
        user.setAdditionalInformation(Map.of());
        return user;
    }

    private Client generateFakeApplication() {
        final Client client = new Client();
        client.setClientName("PreviewApp");
        client.setClientId("<client-id>");
        client.setDomain(this.domain.getId());
        return client;
    }
}
