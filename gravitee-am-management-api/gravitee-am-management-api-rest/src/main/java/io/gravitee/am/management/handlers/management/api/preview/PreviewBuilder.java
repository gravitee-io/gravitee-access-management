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
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.model.PreviewRequest;
import io.gravitee.am.management.handlers.management.api.model.PreviewResponse;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.theme.ThemeResolution;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.exceptions.TemplateProcessingException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.gravitee.am.common.utils.ConstantKeys.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PreviewBuilder {
    private final Logger logger = LoggerFactory.getLogger(PreviewBuilder.class);
    public static final String EMPTY_STRING = "";
    public static final String SITE_KEY = "siteKey";
    public static final String PARAMETER_NAME = "parameterName";
    public static final String FACTOR_TYPE = "factorType";
    public static final String FACTOR_NAME = "name";
    public static final String FACTOR_TARGET = "target";
    public static final String ENROLLMENT = "enrollment";
    public static final String ID = "id";

    private final TemplateEngine templateEngine;
    private final TemplateResolver templateResolver;
    private PreviewRequest request;
    private Domain domain;
    private Client client;
    private ThemeResolution theme;
    private Locale locale;
    private String baseUrl;

    public PreviewBuilder(TemplateEngine templateEngine, TemplateResolver templateResolver, String baseUrl) {
        this.templateEngine = templateEngine;
        this.templateResolver = templateResolver;
        this.baseUrl = baseUrl;
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

        variables.put(PARAM_CONTEXT_KEY, Map.of());

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
            previewForm.setContent(convertAssertsPath(processedTemplate, baseUrl));
        } catch (TemplateInputException e) {
            logger.debug("Preview error on domain {}", this.domain.getId(), e);
            throw new PreviewException("Preview error, document structure maybe invalid." +
                    " (error at line: " + e.getLine() + ", col: " + e.getCol() + " )");
        } catch (TemplateProcessingException e) {
            logger.debug("Preview error on domain {}", this.domain.getId(), e);
            throw new PreviewException("Preview error, expression or variable maybe invalid (error at line: " + e.getLine() + ", col: " + e.getCol() + ")");
        } catch (TemplateEngineException e) {
            logger.info("Unexpected preview error on domain {}", this.domain.getId(), e);
            throw new PreviewException("Unexpected preview error");
        } finally {
            this.templateResolver.removeForm(previewForm);
        }

        return new PreviewResponse(previewForm.getContent(), this.request.getType(), this.request.getTemplate());
    }

    private String convertAssertsPath(String content, String baseUrl) {
        final Document document = Jsoup.parse(content);

        replaceAssetValueInHtmlElement(document, "img", "src", baseUrl);
        replaceAssetValueInHtmlElement(document, "script", "src", baseUrl);
        replaceAssetValueInHtmlElement(document, "link", "href", baseUrl);

        return document.html();
    }

    private static void replaceAssetValueInHtmlElement(Document document,String elementName, String attrName, String baseUrl) {
        final Elements imageElements = document.getElementsByTag(elementName);
        imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr(attrName))
                .filter(imageElement -> !imageElement.attr(attrName).startsWith("http"))
                .forEach(imageElement -> {
                    final String src = imageElement.attr(attrName).replaceAll("(..[/])*assets", baseUrl +"/auth/assets/preview");
                    imageElement.attr(attrName, src);
                });
    }

    private Map<String, Object> generateTemplateVariables(String template) {
        final Map<String, Object> variables = new HashMap<>();

        variables.put(ConstantKeys._CSRF, Map.of(PARAMETER_NAME, EMPTY_STRING, TOKEN_CONTEXT_KEY, EMPTY_STRING));
        variables.put(ConstantKeys.PARAM_CONTEXT_KEY, Map.of(USERNAME_PARAM_KEY, EMPTY_STRING, Parameters.CLIENT_ID, EMPTY_STRING));
        variables.put(ConstantKeys.ACTION_KEY, EMPTY_STRING);

        switch (Template.parse(template)) {
            case LOGIN:
                variables.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, Collections.emptyList());
                variables.put(ConstantKeys.TEMPLATE_KEY_FORGOT_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_WEBAUTHN_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_CBA_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_REGISTER_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_BACK_LOGIN_IDENTIFIER_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, EMPTY_STRING);
                variables.put(ConstantKeys.DEVICE_IDENTIFIER_PROVIDER_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, Boolean.FALSE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_HIDE_FORM_CONTEXT_KEY, Boolean.FALSE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_REGISTER_CONTEXT_KEY, Boolean.TRUE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_PASSWORDLESS_CONTEXT_KEY, Boolean.TRUE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_CBA_CONTEXT_KEY, Boolean.TRUE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_ALLOW_FORGOT_PASSWORD_CONTEXT_KEY, Boolean.TRUE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_IDENTIFIER_FIRST_LOGIN_CONTEXT_KEY, Boolean.FALSE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of(SITE_KEY, EMPTY_STRING));
                variables.put(ConstantKeys.TEMPLATE_KEY_REMEMBER_ME_KEY, Boolean.TRUE.toString());
                break;

            case REGISTRATION:
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, EMPTY_STRING);
                variables.put(ConstantKeys.LOGIN_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of(SITE_KEY, EMPTY_STRING));
                break;

            case REGISTRATION_CONFIRMATION:
                variables.put(ConstantKeys.TOKEN_CONTEXT_KEY, EMPTY_STRING);
                break;

            case FORGOT_PASSWORD:
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_PLUGIN, EMPTY_STRING);
                variables.put(ConstantKeys.FORGOT_PASSWORD_FIELDS_KEY, Arrays.asList(FormField.getEmailField()));
                variables.put(ConstantKeys.LOGIN_ACTION_KEY, EMPTY_STRING);
                variables.put(ConstantKeys.TEMPLATE_KEY_BOT_DETECTION_CONFIGURATION, Map.of(SITE_KEY, EMPTY_STRING));
                break;

            case MFA_ENROLL:
            case MFA_CHALLENGE_ALTERNATIVES:
                final Enrollment otpEnrollment = new Enrollment();
                otpEnrollment.setBarCode("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAADIAQAAAACFI5MzAAABuElEQVR4Xu2WWWoEMQwFDb6WwVc36FqGTpXCLAzJn5X8tGiGtmtA27Pc7frN2ufG025yE+0vyWqtr75GXzP2mC5LSPCsvtme1+65rCGrR6y528A17zxlZI1htpNMS8l17abvaxcSntgN749y1hAlES/70M45opEgAkGKoXftPFkzK3nFtpSI/hXbWdJtGwk2TtXgZ8wSEh7f5vkFjGxgCUHtnt6lRnCfMikh6d2WpeSbBa4gTgl6Rr7T0rouISxpGnvB4HPQPit6lviG4q0s7sU1xFKaXeSfmOmEUkIQIJ1ja3SS5vUZwVGCWwafVxOnC8W3WUSmOSJ6JyBlHYnOE66+ruCppH9x3NaQHgoju+eYWK8IjpLIGLYtW770SztPnOK2zVpmE58RHCare6xUh8G8TaSzhEKSLRE0QnBkvGnnJGGbSjqV8rJ1p4gs76O58iC7fijxLKGgXOYL5+EXkaGUEI1LI2cFT1a4giw/sUI9ZjBegyUkrKmKN4TUyqwhWVL2bVzk/V5GLpsH8ihT2zqS5yny20v/JYQnch7x6UVpvyVynqgQ9Q70gLF+ZHqW/Gw3uYn2/+QLiTKJ//OwuCgAAAAASUVORK5CYII=");
                final Map<String, Object> factorOTP = Map.of(ID, "idotp" ,
                        FACTOR_TYPE, FactorType.OTP.getType(),
                        FACTOR_NAME, "OTP Factor name",
                        ENROLLMENT, otpEnrollment);

                final Enrollment smsEnrollment = new Enrollment();
                smsEnrollment.setCountries(List.of("us", "en", "fr"));
                final Map<String, Object> factorSms = Map.of(
                        ID, "idsms" ,
                        FACTOR_TYPE, FactorType.SMS.getType(),
                        FACTOR_TARGET, "123456",
                        FACTOR_NAME, "SMS Factor name",
                        ENROLLMENT, smsEnrollment);

                final Enrollment emailEnrollment = new Enrollment();
                emailEnrollment.setKey(EMPTY_STRING);
                final Map<String, Object> factorEmail = Map.of(ID, "idemail",
                        FACTOR_TYPE, FactorType.EMAIL.getType(),
                        FACTOR_TARGET, "john@doe.com",
                        FACTOR_NAME, "EMAIL Factor name",
                        ENROLLMENT, emailEnrollment);

                variables.put(ConstantKeys.FACTORS_KEY, List.of(factorOTP, factorSms, factorEmail));
                break;

            case MFA_CHALLENGE:
                final Factor factor = new Factor();
                factor.setFactorType(FactorType.OTP);
                variables.put(ConstantKeys.FACTOR_KEY, factor);
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, Boolean.FALSE.toString());
                variables.put(ConstantKeys.MFA_ALTERNATIVES_ENABLE_KEY, Boolean.TRUE.toString());
                variables.put(ConstantKeys.MFA_ALTERNATIVES_ACTION_KEY, EMPTY_STRING);
                break;

            case WEBAUTHN_LOGIN:
            case CBA_LOGIN:
                variables.put(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE, Boolean.FALSE.toString());
                variables.put(ConstantKeys.TEMPLATE_KEY_REMEMBER_ME_KEY, Boolean.TRUE.toString());
                break;

            case ERROR:
                variables.put(ERROR_PARAM_KEY, "login_failed");
                variables.put(ERROR_DESCRIPTION_PARAM_KEY, "Wrong user or password");
                break;

            case MFA_RECOVERY_CODE:
                variables.put(TEMPLATE_KEY_RECOVERY_CODES_KEY, IntStream.range(0, 6).mapToObj(i -> UUID.randomUUID().toString()).toList());
                variables.put(TEMPLATE_KEY_RECOVERY_CODES_URL_KEY, EMPTY_STRING);
                break;

            case IDENTIFIER_FIRST_LOGIN:
                variables.put(ConstantKeys.TEMPLATE_KEY_REMEMBER_ME_KEY, Boolean.TRUE.toString());
                break;

            // template without specific variables
            case RESET_PASSWORD:
            case OAUTH2_USER_CONSENT:
            case BLOCKED_ACCOUNT:
            case COMPLETE_PROFILE:
            case WEBAUTHN_REGISTER:
            case CERTIFICATE_EXPIRATION:
            case CLIENT_SECRET_EXPIRATION:
            default:
                break;
        }
        return variables;
    }

    private UserProperties generateFakeUser() {
        final UserProperties fakeUser = new UserProperties();
        fakeUser.setDomain(this.domain.getId());
        fakeUser.setEmail("john.doe@gravitee.io");
        fakeUser.setFirstName("John");
        fakeUser.setLastName("Doe");
        fakeUser.setClaims(Map.of());
        fakeUser.setAdditionalInformation(Map.of());
        return fakeUser;
    }

    private Client generateFakeApplication() {
        final Client fakeClient = new Client();
        fakeClient.setClientName("PreviewApp");
        fakeClient.setClientId("<fakeClient-id>");
        fakeClient.setDomain(this.domain.getId());
        return fakeClient;
    }
}
