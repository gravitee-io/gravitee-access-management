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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import freemarker.template.TemplateException;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.DomainNotifierService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.CertificateNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils;
import io.gravitee.am.model.*;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.spring.email.EmailConfiguration;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration.CERTIFICATE_EXPIRY_TPL;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_UI_NOTIFIER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainNotifierServiceImpl implements DomainNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainNotifierServiceImpl.class);

    @Value("${services.notifier.am.email.enabled:true}")
    private boolean emailNotifierEnabled;

    @Value("${services.notifier.am.email.sslTrustAll:false}")
    private boolean emailSSLTrustAll;

    @Value("${services.notifier.am.email.sslKeyStore}")
    private String emailSSLKeyStore;

    @Value("${services.notifier.am.email.sslKeyStorePassword}")
    private String emailSSLKeyStorePassword;

    @Value("${services.notifier.am.ui.enabled:true}")
    private boolean uiNotifierEnabled;

    @Value("${services.notifier.am.cronExpression:0 0 5 * * *}") // default: 0 0 5 * * * (every day at 5am)
    private String cronExpression;

    @Value("${services.notifier.am.certificate.expiryThreshold:7}")
    private int certificateExpiryThreshold;

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private OrganizationUserService userService;

    @Autowired
    private EmailConfiguration emailConfiguration;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    public void registerCertificateExpiration(CertificateProvider provider, Certificate certificate) {
        findDomain(certificate.getDomain())
                .flatMapPublisher(domain ->
                        retrieveDomainOwners(domain)
                                .flatMap(user -> Flowable.fromArray(
                                        buildEmailNotificationDefinition(provider, certificate, domain, user),
                                        buildUINotificationDefinition(provider, certificate, domain, user))))
                .subscribe(definition -> {
                    if (definition.isPresent()) {
                        notifierService.register(definition.get(), new CertificateNotificationCondition(certificateExpiryThreshold));
                    }
                });
    }

    @Override
    public void unregisterCertificateExpiration(String domainId, String certificateId) {
        final Domain domain = findDomain(domainId).blockingGet();
        retrieveDomainOwners(domain).blockingForEach(user -> this.notifierService.unregister(certificateId, TYPE_EMAIL_NOTIFIER, user.getId()));
    }

    @Override
    public Completable deleteCertificateExpirationAcknowledge(String certificateId) {
        LOGGER.debug("Remove All NotificationAcknowledge for the certificate {}", certificateId);
        return this.notifierService.deleteAcknowledge(certificateId);
    }

    private Flowable<User> retrieveDomainOwners(Domain domain) {
        return findEnvironment(domain).flatMapPublisher(env -> Maybe.concat(
                        roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN),
                        roleService.findDefaultRole(env.getOrganizationId(), DefaultRole.DOMAIN_OWNER, ReferenceType.DOMAIN)
                ).map(role -> role.getId())
                .flatMap(roleId -> {
                    final MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setRoleId(roleId);
                    return membershipService.findByCriteria(ReferenceType.DOMAIN, domain.getId(), criteria);
                }).flatMap(membership -> {
                    if (membership.getMemberType() == MemberType.USER) {
                        return userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), membership.getMemberId()).toFlowable();
                    } else {
                        return readUsersFromGroup(membership, 0, 10);
                    }
                }));
    }

    private Single<Environment> findEnvironment(Domain domain) {
        return environmentService.findById(domain.getReferenceId());
    }

    private Single<Domain> findDomain(String domainId) {
        return domainService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)));
    }

    private Flowable<User> readUsersFromGroup(Membership membership, int pageIndex, int size) {
        return groupService.findMembers(ReferenceType.DOMAIN, membership.getReferenceId(), membership.getMemberId(), pageIndex, size)
                .flatMapPublisher(page -> {
                    if (page.getData().size() < 10) {
                        return Flowable.fromIterable(page.getData());
                    } else {
                        return Flowable.concat(Flowable.fromIterable(page.getData()), readUsersFromGroup(membership, pageIndex + 1, size));
                    }
                });
    }

    private Optional<NotificationDefinition> buildEmailNotificationDefinition(CertificateProvider provider, Certificate certificate, Domain domain, User user) {
        if (emailNotifierEnabled && emailConfiguration.isEnabled() && !Strings.isNullOrEmpty(user.getEmail())) {
            try {

                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withDomain(domain)
                        .withUser(user)
                        .withCertificate(certificate, provider.getExpirationDate().orElse(null))
                        .build();

                final Email email = emailService.getFinalEmail(domain, null, Template.CERTIFICATE_EXPIRATION, user, data);

                final EmailNotifierConfiguration notifierConfig = new EmailNotifierConfiguration(emailConfiguration, emailSSLTrustAll, emailSSLKeyStore, emailSSLKeyStorePassword);
                notifierConfig.setSubject(email.getSubject());
                notifierConfig.setBody(email.getContent());
                notifierConfig.setTo(user.getEmail());

                final NotificationDefinition definition = new NotificationDefinition();
                definition.setType(TYPE_EMAIL_NOTIFIER);
                definition.setConfiguration(mapper.writeValueAsString(notifierConfig));
                definition.setResourceId(certificate.getId());
                definition.setAudienceId(user.getId());
                definition.setCron(this.cronExpression);
                definition.setData(data);

                return Optional.of(definition);
            } catch (IOException | TemplateException e) {
                LOGGER.warn("Unable to generate email template for certificate expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Optional.empty();
    }

    private Optional<NotificationDefinition> buildUINotificationDefinition(CertificateProvider provider, Certificate certificate, Domain domain, User user) {
        if (uiNotifierEnabled) {
            try {
                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withDomain(domain)
                        .withUser(user)
                        .withCertificate(certificate, provider.getExpirationDate().orElse(null))
                        .build();

                ManagementUINotifierConfiguration value = new ManagementUINotifierConfiguration();
                value.setTemplate(CERTIFICATE_EXPIRY_TPL);

                final NotificationDefinition definition = new NotificationDefinition();
                definition.setType(TYPE_UI_NOTIFIER);
                definition.setConfiguration(mapper.writeValueAsString(value));
                definition.setResourceId(certificate.getId());
                definition.setAudienceId(user.getId());
                definition.setCron(this.cronExpression);
                definition.setData(data);

                return Optional.of(definition);
            } catch (IOException e) {
                LOGGER.warn("Unable to generate ui configuration for certificate expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Optional.empty();
    }
}
