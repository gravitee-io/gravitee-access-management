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
import io.gravitee.am.common.email.Email;
import io.gravitee.am.management.service.DomainNotifierService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.*;
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
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainNotifierServiceImpl implements DomainNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainNotifierServiceImpl.class);

    @Value("${notifiers.email.enabled:false}")
    private boolean emailNotifierEnabled;

    @Value("${notifiers.ui.enabled:true}")
    private boolean uiNotifierEnabled;

    @Value("${services.certificate.cronExpression:0 0 5 * * *}") // default: 0 0 5 * * * (every day at 5am)
    private String certificateCronExpression;

    @Value("${services.certificate.resendAfter:2}")
    private int certificateExpiryResendAfter;

    @Value("${services.certificate.expiryThreshold:14}")
    private int certificateExpiryThreshold;

    @Value("${services.certificate.enabled:true}")
    private boolean certificateNotificationEnabled = true;

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
    private EmailNotifierConfiguration emailConfiguration;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    @Override
    public void registerCertificateExpiration(Certificate certificate) {
        if (this.certificateNotificationEnabled) {
            findDomain(certificate.getDomain())
                    .flatMapPublisher(domain ->
                            retrieveDomainOwners(domain)
                                    .flatMap(user -> Flowable.fromArray(
                                            buildEmailNotificationDefinition(certificate, domain, user),
                                            buildUINotificationDefinition(certificate, domain, user))))
                    .subscribe(definition -> {
                        if (definition.isPresent()) {
                            notifierService.register(definition.get(),
                                    new CertificateNotificationCondition(certificateExpiryThreshold),
                                    new CertificateResendNotificationCondition(this.certificateExpiryResendAfter));
                        }
                    });
        }
    }

    @Override
    public void unregisterCertificateExpiration(String domainId, String certificateId) {
        if (this.certificateNotificationEnabled) {
            final Domain domain = findDomain(domainId).blockingGet();
            retrieveDomainOwners(domain).blockingForEach(user -> this.notifierService.unregister(certificateId, TYPE_EMAIL_NOTIFIER, user.getId()));
        }
    }

    @Override
    public Completable deleteCertificateExpirationAcknowledge(String certificateId) {
        if (this.certificateNotificationEnabled) {
            LOGGER.debug("Remove All NotificationAcknowledge for the certificate {}", certificateId);
            return this.notifierService.deleteAcknowledge(certificateId);
        } else {
            return Completable.complete();
        }
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

    private Optional<NotificationDefinition> buildEmailNotificationDefinition(Certificate certificate, Domain domain, User user) {
        if (emailNotifierEnabled && !Strings.isNullOrEmpty(user.getEmail())) {
            try {

                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withDomain(domain)
                        .withUser(user)
                        .withCertificate(certificate)
                        .build();

                final Email email = emailService.getFinalEmail(domain, null, Template.CERTIFICATE_EXPIRATION, user, data);

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

                return Optional.of(definition);
            } catch (IOException | TemplateException e) {
                LOGGER.warn("Unable to generate email template for certificate expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Optional.empty();
    }

    private Optional<NotificationDefinition> buildUINotificationDefinition(Certificate certificate, Domain domain, User user) {
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
