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
package io.gravitee.am.gateway.services.sync;

import io.gravitee.am.gateway.core.event.DomainEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";
    private static final String SHARDING_TAGS_SEPARATOR = ",";

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Environment environment;

    private final Map<String, Domain> deployedDomains = new HashMap<>();

    private Optional<List<String>> shardingTags;

    public void afterPropertiesSet() {
        this.initShardingTags();
    }

    public void refresh() {
        logger.debug("Refreshing sync state...");

        // Registered domains
        Set<Domain> domains = domainRepository.findAll()
                // remove master domains
                .map(registeredDomains -> {
                    if (registeredDomains != null) {
                        return registeredDomains
                                .stream()
                                .filter(domain -> !domain.isMaster())
                                .collect(Collectors.toSet());
                    }
                    return Collections.<Domain>emptySet();
                })
                .blockingGet();

        // Look for deleted domains
        if (deployedDomains.size() > domains.size()) {
            Set<String> domainIds = domains.stream().map(domain -> domain.getId()).collect(Collectors.toSet());
            Set<String> deployedDomainIds = new HashSet<>(deployedDomains.keySet());
            deployedDomainIds.forEach(domainId -> {
                if (!domainIds.contains(domainId)) {
                    Domain deployedDomain = deployedDomains.get(domainId);
                    if (deployedDomain != null) {
                        deployedDomains.remove(domainId);
                        eventManager.publishEvent(DomainEvent.UNDEPLOY, deployedDomain);
                    }
                }
            });
        }

        // Look for disabled domains
        domains.stream()
                .filter(domain -> !domain.isEnabled())
                .forEach(domain -> {
                    Domain deployedDomain = deployedDomains.get(domain.getId());
                    if (deployedDomain != null) {
                        deployedDomains.remove(domain.getId());
                        eventManager.publishEvent(DomainEvent.UNDEPLOY, deployedDomain);
                    }
                });

        // Deploy domains
        domains.stream()
                .filter(Domain::isEnabled)
                .forEach(domain -> {
                    Domain deployedDomain = deployedDomains.get(domain.getId());

                    // Does the security domain have a matching sharding tags ?
                    if (hasMatchingTags(domain)) {

                        // Security domain is not yet deployed, so let's do it !
                        if (deployedDomain == null) {
                            eventManager.publishEvent(DomainEvent.DEPLOY, domain);
                            deployedDomains.put(domain.getId(), domain);
                        } else {
                            // Check last update date
                            if (domain.getUpdatedAt().after(deployedDomain.getUpdatedAt())) {
                                // get event type and publish corresponding event
                                Event lastEvent = domain.getLastEvent();
                                Enum eventType = io.gravitee.am.gateway.core.event.Event.valueOf(lastEvent);
                                Object content = Type.DOMAIN.equals(lastEvent.getType()) ? domain : lastEvent.getPayload();
                                eventManager.publishEvent(eventType, content);

                                // update local domains map
                                deployedDomains.put(domain.getId(), domain);
                            }
                        }
                    } else {
                        // Check that the security domain was not previously deployed with other tags
                        // In that case, we must undeploy it
                        if (deployedDomain != null) {
                            deployedDomains.remove(domain.getId());
                            eventManager.publishEvent(DomainEvent.UNDEPLOY, deployedDomain);
                        }
                    }
                });
    }

    private void initShardingTags() {
        String systemPropertyTags = System.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY);
        String tags = systemPropertyTags == null ?
                environment.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY) : systemPropertyTags;
        if (tags != null && ! tags.isEmpty()) {
            shardingTags = Optional.of(Arrays.asList(tags.split(SHARDING_TAGS_SEPARATOR)));
        } else {
            shardingTags = Optional.empty();
        }
    }

    private boolean hasMatchingTags(Domain domain) {
        if (shardingTags.isPresent()) {
            List<String> tagList = shardingTags.get();
            if (domain.getTags() != null) {
                final List<String> inclusionTags = tagList.stream()
                        .map(String::trim)
                        .filter(tag -> !tag.startsWith("!"))
                        .collect(Collectors.toList());

                final List<String> exclusionTags = tagList.stream()
                        .map(String::trim)
                        .filter(tag -> tag.startsWith("!"))
                        .map(tag -> tag.substring(1))
                        .collect(Collectors.toList());

                if (inclusionTags.stream().anyMatch(exclusionTags::contains)) {
                    throw new IllegalArgumentException("You must not configure a tag to be included and excluded");
                }

                final boolean hasMatchingTags =
                        inclusionTags.stream()
                                .anyMatch(tag -> domain.getTags().stream()
                                        .anyMatch(apiTag -> {
                                            final Collator collator = Collator.getInstance();
                                            collator.setStrength(Collator.NO_DECOMPOSITION);
                                            return collator.compare(tag, apiTag) == 0;
                                        })
                                ) || (!exclusionTags.isEmpty() &&
                                exclusionTags.stream()
                                        .noneMatch(tag -> domain.getTags().stream()
                                                .anyMatch(apiTag -> {
                                                    final Collator collator = Collator.getInstance();
                                                    collator.setStrength(Collator.NO_DECOMPOSITION);
                                                    return collator.compare(tag, apiTag) == 0;
                                                })
                                        ));

                if (!hasMatchingTags) {
                    logger.debug("The security domain {} has been ignored because not in configured tags {}", domain.getName(), tagList);
                }
                return hasMatchingTags;
            }
            logger.debug("Tags {} are configured on gateway instance but not found on the security domain {}", tagList, domain.getName());
            return false;
        }
        // no tags configured on this gateway instance
        return true;
    }
}
