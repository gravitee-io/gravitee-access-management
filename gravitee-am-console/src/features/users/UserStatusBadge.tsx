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
import { Badge } from '@gravitee/graphene-core';
import { CircleCheckIcon, CircleStopIcon, LockIcon } from '@gravitee/graphene-core/icons';
import type { User } from '../../lib/api/users';
import { type UserStatus, userStatus } from './user-helpers';

const STATUS: Record<UserStatus, { label: string; title: string; variant: 'success' | 'secondary' | 'warning'; icon: typeof LockIcon }> = {
    enabled: { label: 'Enabled', title: 'User enabled', variant: 'success', icon: CircleCheckIcon },
    disabled: { label: 'Disabled', title: 'User disabled', variant: 'secondary', icon: CircleStopIcon },
    locked: { label: 'Locked', title: 'User locked', variant: 'warning', icon: LockIcon },
};

export function UserStatusBadge({ user }: { readonly user: User }) {
    const status = STATUS[userStatus(user)];
    const Icon = status.icon;
    return (
        <Badge variant={status.variant} title={status.title}>
            <Icon className="mr-1 size-3" aria-hidden />
            {status.label}
        </Badge>
    );
}
