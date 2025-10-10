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
package io.gravitee.am.management.handlers.management.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.adapter.ScopeApprovalAdapter;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.handlers.management.api.spring.UserBulkConfiguration;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.management.service.AuthenticationDeviceNotifierPluginService;
import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.management.service.BotDetectionServiceProxy;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DeviceIdentifierPluginService;
import io.gravitee.am.management.service.DomainGroupService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.ExtensionGrantPluginService;
import io.gravitee.am.management.service.FactorPluginService;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.ManagementUserService;
import io.gravitee.am.management.service.NewsletterService;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.management.service.PermissionService;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.management.service.ResourcePluginService;
import io.gravitee.am.management.service.RevokeTokenManagementService;
import io.gravitee.am.management.service.TagService;
import io.gravitee.am.management.service.dataplane.CredentialManagementService;
import io.gravitee.am.management.service.dataplane.DeviceManagementService;
import io.gravitee.am.management.service.dataplane.UserActivityManagementService;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Organization;
import io.gravitee.am.plugins.handlers.api.core.AmPluginManager;
import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationValidatorsRegistry;
import io.gravitee.am.service.*;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.validators.email.UserEmail;
import io.gravitee.am.service.validators.email.UserEmailConstraintValidator;
import io.gravitee.am.service.validators.email.resource.EmailTemplateValidator;
import io.gravitee.am.service.validators.flow.FlowValidator;
import io.gravitee.am.service.validators.plugincfg.PluginJsonFormValidator;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.thymeleaf.TemplateEngine;

import javax.inject.Named;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SpringJUnitConfig(classes = JerseySpringTest.ContextConfiguration.class)
public abstract class JerseySpringTest {

    protected static final String USER_NAME = "UnitTests";

    protected ObjectMapper objectMapper = new ObjectMapperResolver()
            .getContext(ObjectMapper.class)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    @Named("managementOrganizationUserService")
    protected OrganizationUserService organizationUserService;

    @Autowired
    protected io.gravitee.am.service.OrganizationUserService commonOrganizationUserService;

    @Autowired
    protected DomainService domainService;

    @Autowired
    protected ManagementUserService userService;

    @Autowired
    protected UserActivityManagementService userActivityService;

    @Autowired
    protected ScopeService scopeService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected IdentityProviderServiceProxy identityProviderService;

    @Autowired
    protected ExtensionGrantService extensionGrantService;

    @Autowired
    protected CertificateServiceProxy certificateService;

    @Autowired
    protected CertificatePluginService certificatePluginService;

    @Autowired
    protected RevokeTokenManagementService tokenService;

    @Autowired
    protected ExtensionGrantPluginService extensionGrantPluginService;

    @Autowired
    protected IdentityProviderPluginService identityProviderPluginService;

    @Autowired
    protected CertificateManager certificateManager;

    @Autowired
    protected IdentityProviderManager identityProviderManager;

    @Autowired
    protected DefaultIdentityProviderService defaultIdentityProviderService;

    @Autowired
    protected EmailTemplateService emailTemplateService;

    @Autowired
    protected EmailManager emailManager;

    @Autowired
    protected FormService formService;

    @Autowired
    protected UserValidator userValidator;

    @Autowired
    protected ScopeApprovalService scopeApprovalService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected AuditReporterManager AuditReporterManager;

    @Autowired
    protected ReporterServiceProxy reporterService;

    @Autowired
    protected TagService tagService;

    @Autowired
    protected DomainGroupService domainGroupService;

    @Autowired
    protected OrganizationGroupService organizationGroupService;

    @Autowired
    protected ApplicationService applicationService;

    @Autowired
    protected FactorService factorService;

    @Autowired
    protected FactorPluginService factorPluginService;

    @Autowired
    protected PermissionService permissionService;

    @Autowired
    protected OrganizationService organizationService;

    @Autowired
    protected MembershipService membershipService;

    @Autowired
    protected EntrypointService entrypointService;

    @Autowired
    protected CredentialManagementService credentialService;

    @Autowired
    protected FlowService flowService;

    @Autowired
    protected ResourcePluginService resourcePluginService;

    @Autowired
    protected BotDetectionPluginService botDetectionPluginService;

    @Autowired
    protected BotDetectionServiceProxy botDetectionService;

    @Autowired
    protected DeviceIdentifierPluginService deviceIdentifierPluginService;

    @Autowired
    protected DeviceIdentifierService deviceIdentifierService;

    @Autowired
    protected DeviceManagementService deviceService;

    @Autowired
    protected AuthenticationDeviceNotifierPluginService authDeviceNotifierPluginService;

    @Autowired
    protected ThemeService themeService;

    @Autowired
    protected PreviewService previewService;

    @Autowired
    protected I18nDictionaryService i18nDictionaryService;

    @Autowired
    protected PolicyPluginService policyPluginService;

    @Autowired
    protected FlowValidator flowValidator;

    @Autowired
    protected EmailTemplateValidator emailResourceValidator;

    @Autowired
    protected HttpServletRequest httpServletRequest;

    @Autowired
    protected PasswordPolicyService passwordPolicyService;

    @Autowired
    protected ScopeApprovalAdapter scopeApprovalAdapter;

    @Autowired
    protected NewsletterService newsletterService;

    @Autowired
    protected ProtectedResourceService protectedResourceService;

    @BeforeEach
    public void init() {
        when(permissionService.hasPermission(any(User.class), any(PermissionAcls.class))).thenReturn(Single.just(true));
    }

    @Configuration
    static class ContextConfiguration {


        @Bean
        public ScopeApprovalAdapter scopeApprovalAdapter(){
            return Mockito.mock(ScopeApprovalAdapter.class);
        }
        @Bean
        public UserEmailConstraintValidator userEmailConstraintValidator(){
            MockEnvironment mockEnvironment = new MockEnvironment().withProperty(UserEmail.PROPERTY_USER_EMAIL_REQUIRED, "false");
            return new UserEmailConstraintValidator(mockEnvironment);
        }

        @Bean
        public PluginJsonFormValidator pluginJsonFormValidator(){
            return new PluginJsonFormValidator(new PluginConfigurationValidatorsRegistry());
        }

        @Bean
        public OrganizationService organizationService() {
            return mock(OrganizationService.class);
        }

        @Bean
        public EnvironmentService environmentService() {
            return mock(EnvironmentService.class);
        }

        @Bean
        public ObjectMapper objectMapper(){
            return new ObjectMapper();
        }

        @Bean
        public DomainService domainService() {
            return mock(DomainService.class);
        }

        @Bean("managementOrganizationUserService")
        public OrganizationUserService organizationUserService() {
            return mock(OrganizationUserService.class);
        }

        @Bean
        public io.gravitee.am.service.OrganizationUserService commonOrganizationUserService() {
            return mock(io.gravitee.am.service.OrganizationUserService.class);
        }

        @Bean
        public ManagementUserService userService() {
            return mock(ManagementUserService.class);
        }

        @Bean
        public ManagementUserService managementUserService() {
            return mock();
        }

        @Bean
        public UserActivityManagementService userActivityService() {
            return mock(UserActivityManagementService.class);
        }

        @Bean
        public ScopeService scopeService() {
            return mock(ScopeService.class);
        }

        @Bean
        public RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        public IdentityProviderService identityProviderService() {
            return mock(IdentityProviderService.class);
        }

        @Bean
        public IdentityProviderServiceProxy identityProviderServiceProxy() {
            return mock(IdentityProviderServiceProxy.class);
        }

        @Bean
        public ExtensionGrantService extensionGrantService() {
            return mock(ExtensionGrantService.class);
        }

        @Bean
        public CertificateService certificateService() {
            return mock(CertificateService.class);
        }

        @Bean
        public CertificateServiceProxy certificateServiceProxy() {
            return mock(CertificateServiceProxy.class);
        }

        @Bean
        public CertificatePluginService certificatePluginService() {
            return mock(CertificatePluginService.class);
        }

        @Bean
        public RevokeTokenManagementService tokenService() {
            return mock(RevokeTokenManagementService.class);
        }

        @Bean
        public ExtensionGrantPluginService extensionGrantPluginService() {
            return mock(ExtensionGrantPluginService.class);
        }

        @Bean
        public IdentityProviderPluginService identityProviderPluginService() {
            return mock(IdentityProviderPluginService.class);
        }

        @Bean
        public CertificateManager certificateManager() {
            return mock(CertificateManager.class);
        }

        @Bean
        public AmPluginManager<CertificateManager> certificatePluginManager() {
            return mock(AmPluginManager.class);
        }

        @Bean
        public IdentityProviderManager identityProviderManager() {
            return mock(IdentityProviderManager.class);
        }

        @Bean
        public DefaultIdentityProviderService defaultIdentityProviderService() {return mock(DefaultIdentityProviderService.class);}

        @Bean
        public EmailTemplateService emailTemplateService() {
            return mock(EmailTemplateService.class);
        }

        @Bean
        public EmailManager emailManager() {
            return mock(EmailManager.class);
        }

        @Bean
        public FormService formService() {
            return mock(FormService.class);
        }

        @Bean
        public PasswordService passwordService() {
            return mock(PasswordService.class);
        }

        @Bean
        public UserValidator userValidator() {
            return mock(UserValidator.class);
        }

        @Bean
        public ScopeApprovalService scopeApprovalService() {
            return mock(ScopeApprovalService.class);
        }

        @Bean
        public AuditService auditService() {
            return mock(AuditService.class);
        }

        @Bean
        public AuditReporterManager auditReporterManager() {
            return mock(AuditReporterManager.class);
        }

        @Bean
        public ReporterServiceProxy reporterServiceProxy() {
            return mock(ReporterServiceProxy.class);
        }

        @Bean
        public ReporterService reporterService() {
            return mock(ReporterService.class);
        }

        @Bean
        public TagService tagService() {
            return mock(TagService.class);
        }

        @Bean
        public DomainGroupService domainGroupService() {
            return mock(DomainGroupService.class);
        }

        @Bean
        public OrganizationGroupService organizationGroupService() {
            return mock(OrganizationGroupService.class);
        }

        @Bean
        public ApplicationService applicationService() {
            return mock(ApplicationService.class);
        }

        @Bean
        public FactorService factorService() {
            return mock(FactorService.class);
        }

        @Bean
        public FactorPluginService factorPluginService() {
            return mock(FactorPluginService.class);
        }

        @Bean
        public PermissionService permissionService() {
            return mock(PermissionService.class);
        }

        @Bean
        public MembershipService membershipService() {
            return mock(MembershipService.class);
        }

        @Bean
        public EntrypointService entrypointService() {
            return mock(EntrypointService.class);
        }

        @Bean
        public CredentialManagementService credentialService() {
            return mock(CredentialManagementService.class);
        }

        @Bean
        public FlowService flowService() {
            return mock(FlowService.class);
        }

        @Bean
        public FlowValidator flowValidator() {
            return mock(FlowValidator.class);
        }

        @Bean
        public EmailTemplateValidator emailResourceValidator() {
            EmailTemplateValidator mock = mock(EmailTemplateValidator.class);
            when(mock.validate(Mockito.any())).thenReturn(Completable.complete());
            return mock;
        }

        @Bean
        public ResourcePluginService resourcePluginService() {
            return mock(ResourcePluginService.class);
        }

        @Bean
        public BotDetectionPluginService botDetectionPluginService() {
            return mock(BotDetectionPluginService.class);
        }

        @Bean
        public BotDetectionService botDetectionService() {
            return mock(BotDetectionService.class);
        }

        @Bean
        public BotDetectionServiceProxy botDetectionServiceProxy() {
            return mock(BotDetectionServiceProxy.class);
        }

        @Bean
        public DeviceIdentifierPluginService deviceIdentifierPluginService() {
            return mock(DeviceIdentifierPluginService.class);
        }

        @Bean
        public DeviceIdentifierService deviceIdentifierService() {
            return mock(DeviceIdentifierService.class);
        }

        @Bean
        public DeviceManagementService deviceService() {
            return mock(DeviceManagementService.class);
        }

        @Bean
        public AuthenticationDeviceNotifierPluginService authDeviceNotifierPluginService() {
            return mock(AuthenticationDeviceNotifierPluginService.class);
        }

        @Bean
        public ThemeService themeService() {
            return mock(ThemeService.class);
        }

        @Bean
        public PreviewService previewService() {
            return mock(PreviewService.class);
        }

        @Bean
        public PolicyPluginService policyPluginService() {
            return mock(PolicyPluginService.class);
        }

        @Bean
        public I18nDictionaryService i18nDictionaryService() {
            return mock(I18nDictionaryService.class);
        }

        @Bean
        public TemplateEngine templateEngine() {
            return mock(TemplateEngine.class);
        }

        @Bean
        public TemplateResolver templateResolver() {
            return mock(TemplateResolver.class);
        }

        @Bean
        public HttpServletRequest httpServletRequest() {
            return mock(HttpServletRequest.class);
        }

        @Bean
        public PasswordPolicyService passwordPolicyService() {
            return mock(PasswordPolicyService.class);
        }

        @Bean
        public PasswordHistoryService passwordHistoryService() {
            return mock();
        }

        @Bean
        public UserBulkConfiguration userBulkConfiguration(){
            return new UserBulkConfiguration(1048576,1000);
        }

        @Bean
        public NewsletterService newsletterService() {
            return mock(NewsletterService.class);
        }

        @Bean
        public ProtectedResourceService protectedResourceService() {
            return mock(ProtectedResourceService.class);
        }
    }

    private JerseyTest _jerseyTest;

    public final WebTarget target(final String path) {

        if ("domains".equals(path)) {
            return _jerseyTest.target("organizations").path("DEFAULT").path("environments").path("DEFAULT").path(path);
        }

        return _jerseyTest.target(path);
    }

    @BeforeEach
    public void setup() throws Exception {
        _jerseyTest.setUp();
    }

    @AfterEach
    public void tearDown() throws Exception {
        _jerseyTest.tearDown();
    }

    @Autowired
    public void setApplicationContext(final ApplicationContext context) {
        _jerseyTest = new JerseyTest() {
            @Override
            protected Application configure() {
                ResourceConfig application = new ManagementApplication();
                application.register(AuthenticationFilter.class);
                application.property("contextConfig", context);

                return application;
            }
        };
    }

    @Priority(50)
    public static class AuthenticationFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestContext) throws IOException {
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    DefaultUser endUser = new DefaultUser(USER_NAME);
                    endUser.setAdditionalInformation(Map.of(Claims.ORGANIZATION, Organization.DEFAULT));
                    return new UsernamePasswordAuthenticationToken(endUser, null);
                }

                @Override
                public boolean isUserInRole(String string) {
                    return true;
                }

                @Override
                public boolean isSecure() {
                    return true;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "BASIC";
                }
            });
        }
    }

    /**
     * Allows to read response entity using object mapper instead of jersey's own implementation.
     * It is especially useful to ensure objects are deserialized from json using same feature (ex: lower cased enum, ...).
     *
     * @param response the jaxrs response.
     * @param clazz the type of entity
     * @param <T> the expected type of entity.
     *
     * @return the deserialized entity.
     */
    protected <T> T readEntity(Response response, Class<T> clazz) {

        try {
            if (clazz == String.class) {
                return (T) response.readEntity(String.class);
            }

            return objectMapper.readValue(response.readEntity(String.class), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T readEntity(Response response, TypeReference<T> clazz) {

        try {
            return objectMapper.readValue(response.readEntity(String.class), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> List<T> readListEntity(Response response, Class<T> entityClazz) {

        try {
            return objectMapper.readValue(response.readEntity(String.class), objectMapper.getTypeFactory().constructCollectionType(List.class, entityClazz));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allows to put entity using object mapper instead of jersey's own implementation.
     * It is especially useful to ensure objects are serialized to json using same feature (ex: lower cased enum, ...).
     *
     * @param webTarget the jersey web target.
     * @param value the entity to put.
     * @param <T> the type of entity.
     *
     * @return the resulted {@link Response}.
     */
    protected <T> Response put(WebTarget webTarget, T value) {

        try {
            return webTarget.request().put(Entity.entity(objectMapper.writeValueAsString(value), MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> Response post(WebTarget webTarget, T value) {

        try {
            return webTarget.request().post(Entity.entity(objectMapper.writeValueAsString(value), MediaType.APPLICATION_JSON_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
