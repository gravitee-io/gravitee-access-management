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
import io.gravitee.am.management.service.DomainNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.CertificateNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.CertificateResendNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration.CERTIFICATE_EXPIRY_TPL;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.RESOURCE_TYPE_CERTIFICATE;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_LOG_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_UI_NOTIFIER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainNotifierServiceImpl implements DomainNotifierService, InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainNotifierServiceImpl.class);
    public static final String DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS = "20,15,10,5,1";

    @Value("${notifiers.email.enabled:false}")
    private boolean emailNotifierEnabled;

    @Value("${notifiers.ui.enabled:true}")
    private boolean uiNotifierEnabled;

    @Value("${services.certificate.cronExpression:0 0 5 * * *}") // default: 0 0 5 * * * (every day at 5am)
    private String certificateCronExpression;

    private List<Integer> certificateExpiryThresholds;

    @Value("${services.certificate.enabled:true}")
    private boolean certificateNotificationEnabled = true;

    @Value("${notifiers.log.enabled:true}")
    private boolean isLogNotifierEnabled;

    @Autowired
    private org.springframework.core.env.Environment env;

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
    private OrganizationGroupService organizationGroupService;

    @Autowired
    private OrganizationUserService userService;

    @Autowired
    private EmailNotifierConfiguration emailConfiguration;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    @Override
    public void afterPropertiesSet() throws Exception {
        final String expiryThresholds = env.getProperty("services.certificate.expiryThresholds", String.class, DEFAULT_CERTIFICATE_EXPIRY_THRESHOLDS);
        this.certificateExpiryThresholds = List.of(expiryThresholds.trim().split(","))
                .stream()
                .map(String::trim)
                .map(Integer::valueOf)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    @Override
    public void registerCertificateExpiration(Certificate certificate) {
        if (this.certificateNotificationEnabled && certificate.getExpiresAt() != null) {
            findDomain(certificate.getDomain())
                    .flatMapPublisher(domain ->
                            retrieveDomainOwners(domain)
                                    .flatMap(user -> {
                                        final Flowable<NotificationDefinition> emailNotificationDef = buildEmailNotificationDefinition(certificate, domain, user).toFlowable();
                                        final Flowable<NotificationDefinition> uiNotificationDef = buildUINotificationDefinition(certificate, domain, user).toFlowable();
                                        final Flowable<NotificationDefinition> logNotificationDef = buildLogNotificationDefinition(certificate, domain).toFlowable();
                                        return Flowable.mergeArray(emailNotificationDef, uiNotificationDef, logNotificationDef);
                                    }))
                    .subscribe(definition ->
                        notifierService.register(definition,
                            new CertificateNotificationCondition(this.certificateExpiryThresholds),
                            new CertificateResendNotificationCondition(this.certificateExpiryThresholds))
                    );
        }
    }

    @Override
    public void unregisterCertificateExpiration(String domainId, String certificateId) {
        if (this.certificateNotificationEnabled) {
            this.notifierService.unregisterAll(certificateId, RESOURCE_TYPE_CERTIFICATE);
        }
    }

    @Override
    public Completable deleteCertificateExpirationAcknowledgement(String certificateId) {
        if (this.certificateNotificationEnabled) {
            LOGGER.debug("Remove All NotificationAcknowledge for the certificate {}", certificateId);
            return this.notifierService.deleteAcknowledge(certificateId, RESOURCE_TYPE_CERTIFICATE);
        } else {
            return Completable.complete();
        }
    }

    private Flowable<User> retrieveDomainOwners(Domain domain) {
        return findEnvironment(domain).flatMapPublisher(env -> Maybe.concat(
                        roleService.findSystemRole(SystemRole.DOMAIN_PRIMARY_OWNER, ReferenceType.DOMAIN),
                        roleService.findDefaultRole(env.getOrganizationId(), DefaultRole.DOMAIN_OWNER, ReferenceType.DOMAIN)
                ).map(Role::getId)
                .flatMap(roleId -> {
                    final MembershipCriteria criteria = new MembershipCriteria();
                    criteria.setRoleId(roleId);
                    return membershipService.findByCriteria(ReferenceType.DOMAIN, domain.getId(), criteria);
                }).flatMap(membership -> {
                    if (membership.getMemberType() == MemberType.USER) {
                        return userService.findById(ReferenceType.ORGANIZATION, env.getOrganizationId(), membership.getMemberId()).toFlowable();
                    } else {
                        return readUsersFromAnOrganizationGroup(env.getOrganizationId(), membership.getMemberId(), 0, 10);
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

    private Flowable<User> readUsersFromAnOrganizationGroup(String organizationId, String memberId, int pageIndex, int size) {
        return organizationGroupService.findMembers(organizationId, memberId, pageIndex, size)
                .flatMapPublisher(page -> {
                    if (page.getTotalCount() == 0) {
                        return Flowable.empty();
                    }

                    if (page.getData().size() < 10) {
                        return Flowable.fromIterable(page.getData());
                    } else {
                        return Flowable.concat(Flowable.fromIterable(page.getData()), readUsersFromAnOrganizationGroup(organizationId, memberId, pageIndex + 1, size));
                    }
                });
    }

    private Maybe<NotificationDefinition> buildEmailNotificationDefinition(Certificate certificate, Domain domain, User user) {
        if (emailNotifierEnabled && !Strings.isNullOrEmpty(user.getEmail())) {
            Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withUser(user)
                    .withCertificate(certificate)
                    .build();

            return emailService.getFinalEmail(domain, null, Template.CERTIFICATE_EXPIRATION, user, data)
                    .map(email -> {
                        EmailNotifierConfiguration notifierConfig = new EmailNotifierConfiguration(this.emailConfiguration);
                        notifierConfig.setSubject(email.getSubject());
                        notifierConfig.setBody(email.getContent());
                        notifierConfig.setTo(user.getEmail());

                        final NotificationDefinition definition = new NotificationDefinition();
                        definition.setType(TYPE_EMAIL_NOTIFIER);
                        definition.setConfiguration(mapper.writeValueAsString(notifierConfig));
                        definition.setResourceId(certificate.getId());
                        definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
                        definition.setAudienceId(user.getId());
                        definition.setCron(this.certificateCronExpression);
                        definition.setData(data);

                        return definition;
                    });
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildUINotificationDefinition(Certificate certificate, Domain domain, User user) {
        if (uiNotifierEnabled) {
            try {
                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withDomain(domain)
                        .withUser(user)
                        .withCertificate(certificate)
                        .build();

                ManagementUINotifierConfiguration value = new ManagementUINotifierConfiguration();
                value.setTemplate(CERTIFICATE_EXPIRY_TPL);

                final NotificationDefinition definition = new NotificationDefinition();
                definition.setType(TYPE_UI_NOTIFIER);
                definition.setConfiguration(mapper.writeValueAsString(value));
                definition.setResourceId(certificate.getId());
                definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
                definition.setAudienceId(user.getId());
                definition.setCron(this.certificateCronExpression);
                definition.setData(data);

                return Maybe.just(definition);
            } catch (IOException e) {
                LOGGER.warn("Unable to generate ui configuration for certificate expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildLogNotificationDefinition(Certificate certificate, Domain domain) {
        if (isLogNotifierEnabled) {
            final Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withCertificate(certificate)
                    .build();

            final NotificationDefinition definition = new NotificationDefinition();
            definition.setType(TYPE_LOG_NOTIFIER);
            definition.setResourceId(certificate.getId());
            definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
            definition.setCron(this.certificateCronExpression);
            definition.setData(data);

            return Maybe.just(definition);
        } else {
            LOGGER.debug("Ignoring log notification for certificate {}, log notification is disabled.", certificate.getId());
        }

        return Maybe.empty();
    }
}
