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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.UserNotificationService;
import io.gravitee.am.model.notification.UserNotification;
import io.gravitee.am.model.notification.UserNotificationStatus;
import io.gravitee.am.repository.management.api.UserNotificationRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserNotificationServiceImpl implements UserNotificationService {

    @Autowired
    @Lazy
    private UserNotificationRepository notificationRepository;


    @Override
    public Flowable<UserNotification> listAllNotifications(User user, UserNotificationStatus status) {
        return notificationRepository.findAllByAudienceAndStatus(user.getId(), status);
    }

    @Override
    public Completable markAsRead(User user, String notificationId) {
        return notificationRepository.updateNotificationStatus(notificationId, UserNotificationStatus.READ);
    }
}