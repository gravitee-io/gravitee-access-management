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
import { CircleCheckIcon, CircleStopIcon } from '@gravitee/graphene-core/icons';
import type { ApplicationType } from '../../lib/api/applications';
import type { SidebarBadge } from '../../lib/layout/useSectionSidebar';
import { APPLICATION_TYPE_ICONS, APPLICATION_TYPE_LABELS } from './application-types';

export function applicationTypeBadge(type: ApplicationType): SidebarBadge {
    return { label: APPLICATION_TYPE_LABELS[type] ?? type, variant: 'outline', icon: APPLICATION_TYPE_ICONS[type] };
}

export function applicationStatusBadge(application: { readonly enabled?: boolean; readonly template?: boolean }): SidebarBadge {
    if (!application.enabled) {
        return { label: 'Disabled', variant: 'secondary', icon: CircleStopIcon };
    }
    return application.template
        ? { label: 'Template', variant: 'outline' }
        : { label: 'Enabled', variant: 'success', icon: CircleCheckIcon };
}
