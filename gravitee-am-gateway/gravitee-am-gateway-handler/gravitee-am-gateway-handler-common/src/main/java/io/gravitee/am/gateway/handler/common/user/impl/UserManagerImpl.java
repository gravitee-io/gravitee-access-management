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
package io.gravitee.am.gateway.handler.common.user.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.UserEvent;
import io.gravitee.am.gateway.handler.common.user.UserManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserManagerImpl extends AbstractService implements UserManager, EventListener<UserEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(UserManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private UserService userService;

    private UserStore userStore;

    public UserManagerImpl(UserStore userStore) {
        Objects.requireNonNull(userStore, "User store must not be null");
        this.userStore = userStore;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for user events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, UserEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for user events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, UserEvent.class, domain.getId());
        userStore.clear();
    }

    @Override
    public void onEvent(Event<UserEvent, Payload> event) {
        if (domain.getId().equals(event.content().getDomain())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateUser(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeUser(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Maybe<User> get(String userId) {
        return userStore.get(userId) != null ? Maybe.just(userStore.get(userId)) : Maybe.empty();
    }

    private void updateUser(String userId, UserEvent userEvent) {
        final String eventType = userEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} user event for {}", domain.getName(), eventType, userId);
        userService.findById(userId)
                .subscribe(
                        user -> {
                            // if registration process is not completed, no need to add the user
                            if (user.isPreRegistration() && !user.isRegistrationCompleted()) {
                                logger.debug("User {} is still not registered, continue", userId);
                            } else {
                                userStore.add(user);
                            }
                            logger.info("User {} {}d for domain {}", userId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} user for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No user found with id {}", userId));
    }

    private void removeUser(String userId) {
        logger.info("Domain {} has received user event, delete user {}", domain.getName(), userId);
        userStore.remove(userId);
    }
}
