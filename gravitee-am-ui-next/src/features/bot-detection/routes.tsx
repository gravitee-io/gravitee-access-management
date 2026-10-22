/*
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
import { Route } from 'react-router-dom';
import { lazyPage } from '../../lib/routing/lazyPage';
import { RequirePermission } from '../../lib/session/permissions';

export const botDetectionRoutes = (
    <Route path="bot-detection" element={<RequirePermission anyOf={['domain_bot_detection_list']} />}>
        <Route index element={lazyPage(() => import('./BotDetectionsPage'), 'BotDetectionsPage')} />
        <Route
            path="new"
            element={
                <RequirePermission anyOf={['domain_bot_detection_create']}>
                    {lazyPage(() => import('./CreateBotDetectionPage'), 'CreateBotDetectionPage')}
                </RequirePermission>
            }
        />
        <Route
            path=":botDetectionId"
            element={
                <RequirePermission anyOf={['domain_bot_detection_read']}>
                    {lazyPage(() => import('./BotDetectionPage'), 'BotDetectionPage')}
                </RequirePermission>
            }
        />
    </Route>
);
