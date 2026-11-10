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
import { Badge, Card, CardContent, DateCell } from '@gravitee/graphene-core';
import {
    ActivityIcon,
    AppWindowIcon,
    CalendarIcon,
    ClockIcon,
    DatabaseIcon,
    KeyIcon,
    LockIcon,
    ServerIcon,
} from '@gravitee/graphene-core/icons';
import type { ComponentType, ReactNode } from 'react';
import type { User } from '../../lib/api/users';
import { IconCardHeader } from './IconCardHeader';
import { isAccountLocked } from './user-helpers';

type Row = { readonly label: string; readonly icon: ComponentType<{ className?: string }>; readonly value: ReactNode };

function formatDate(value: number | null | undefined): string {
    return value ? new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '';
}

export function UserActivityCard({ user }: { readonly user: User }) {
    const rows: Row[] = [
        { label: 'Created', icon: CalendarIcon, value: formatDate(user.createdAt) },
        {
            label: 'Password last reset',
            icon: KeyIcon,
            value: user.lastPasswordReset ? formatDate(user.lastPasswordReset) : 'Not reset',
        },
        { label: 'Last application', icon: AppWindowIcon, value: user.applicationEntity?.name },
        { label: 'Last identity provider', icon: ServerIcon, value: user.lastIdentityUsed },
        { label: 'Source', icon: DatabaseIcon, value: user.source && <Badge variant="outline">{user.source}</Badge> },
        ...(isAccountLocked(user)
            ? [
                  {
                      label: 'Locked until',
                      icon: LockIcon,
                      value: <Badge variant="warning">{formatDate(user.accountLockedUntil) || 'No end date'}</Badge>,
                  },
              ]
            : []),
    ];

    return (
        <Card className="self-start">
            <IconCardHeader icon={<ActivityIcon />} title="Activity" />
            <CardContent className="@container flex flex-col gap-5">
                <dl className="grid gap-3 @xs:grid-cols-2">
                    <div className="min-w-0 rounded-lg bg-muted/50 p-3">
                        <dt className="text-xs text-muted-foreground">Logins</dt>
                        <dd className="text-2xl font-semibold tabular-nums">{user.loginsCount ?? 0}</dd>
                    </div>
                    <div className="min-w-0 rounded-lg bg-muted/50 p-3">
                        <dt className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <ClockIcon aria-hidden className="size-3" />
                            Last login
                        </dt>
                        <dd className="truncate text-sm font-medium">
                            {user.loggedAt ? <DateCell value={new Date(user.loggedAt)} /> : 'Never logged in'}
                        </dd>
                    </div>
                </dl>
                <dl className="grid gap-4">
                    {rows.map(({ label, icon: Icon, value }) => (
                        <div key={label} className="min-w-0">
                            <dt className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                <Icon aria-hidden className="size-3" />
                                {label}
                            </dt>
                            <dd className="truncate text-sm">{value || '—'}</dd>
                        </div>
                    ))}
                </dl>
            </CardContent>
        </Card>
    );
}
