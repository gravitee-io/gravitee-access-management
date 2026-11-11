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
import { CircleCheckIcon, CircleXIcon, ClockIcon, CloudIcon, RefreshCwOffIcon } from '@gravitee/graphene-core/icons';
import type { ComponentType } from 'react';

export type Status = 'NONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DELETED';

export interface StatusView {
    readonly title: string;
    readonly message: string;
    readonly badge: string;
    readonly badgeVariant: 'success' | 'secondary' | 'destructive' | 'outline';
    readonly icon: ComponentType<{ className?: string }>;
    readonly tone: string;
}

export const STATUS_VIEWS: Record<Status, StatusView> = {
    NONE: {
        title: 'Meet Cockpit',
        message: 'Create an account on Cockpit, register your current installation and start creating new organizations and environments.',
        badge: 'Not connected',
        badgeVariant: 'outline',
        icon: CloudIcon,
        tone: 'text-muted-foreground',
    },
    PENDING: {
        title: 'Installation pending',
        message: 'Your installation is connected, but you still have to accept it on Cockpit.',
        badge: 'Pending',
        badgeVariant: 'secondary',
        icon: ClockIcon,
        tone: 'text-warning',
    },
    ACCEPTED: {
        title: 'Installation connected',
        message: 'Your installation is connected to Cockpit. You can now explore everything Cockpit offers.',
        badge: 'Connected',
        badgeVariant: 'success',
        icon: CircleCheckIcon,
        tone: 'text-success',
    },
    REJECTED: {
        title: 'Installation rejected',
        message: 'Your installation is connected to Cockpit, but it has been rejected.',
        badge: 'Rejected',
        badgeVariant: 'destructive',
        icon: CircleXIcon,
        tone: 'text-destructive',
    },
    DELETED: {
        title: 'Installation unlinked',
        message: 'Your installation is connected to Cockpit, but it is no longer linked.',
        badge: 'Unlinked',
        badgeVariant: 'secondary',
        icon: RefreshCwOffIcon,
        tone: 'text-muted-foreground',
    },
};

function isStatus(value: string): value is Status {
    return value in STATUS_VIEWS;
}

export function statusOf(value: string | undefined): Status {
    return value !== undefined && isStatus(value) ? value : 'NONE';
}
