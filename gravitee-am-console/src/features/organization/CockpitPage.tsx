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
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, CopyableText, Skeleton } from '@gravitee/graphene-core';
import { CircleCheckIcon, CircleXIcon, ClockIcon, CloudIcon, ExternalLinkIcon, RefreshCwOffIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ComponentType } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { getInstallation, type Installation } from '../../lib/api/organization';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useOrganizationPath } from './useOrganizationPath';

type Status = 'NONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DELETED';

interface StatusView {
    readonly title: string;
    readonly message: string;
    readonly badge: string;
    readonly badgeVariant: 'success' | 'secondary' | 'destructive' | 'outline';
    readonly icon: ComponentType<{ className?: string }>;
    readonly tone: string;
}

const STATUS_VIEWS: Record<Status, StatusView> = {
    NONE: {
        title: 'Meet Cockpit...',
        message: 'Create an account on Cockpit, register your current installation and start creating new organizations and environments !',
        badge: 'Not connected',
        badgeVariant: 'outline',
        icon: CloudIcon,
        tone: 'text-muted-foreground',
    },
    PENDING: {
        title: 'Almost there !',
        message: 'Your installation is connected but you still have to accept it on Cockpit !',
        badge: 'Pending',
        badgeVariant: 'secondary',
        icon: ClockIcon,
        tone: 'text-warning',
    },
    ACCEPTED: {
        title: 'Congratulation !',
        message: 'Your installation is now connected to Cockpit, you can now explore all the possibilities offered by Cockpit !',
        badge: 'Connected',
        badgeVariant: 'success',
        icon: CircleCheckIcon,
        tone: 'text-success',
    },
    REJECTED: {
        title: 'No luck !',
        message: 'Seems that your installation is connected to Cockpit, but has been rejected...',
        badge: 'Rejected',
        badgeVariant: 'destructive',
        icon: CircleXIcon,
        tone: 'text-destructive',
    },
    DELETED: {
        title: 'Installation unlinked !',
        message: 'Seems that your installation is connected to Cockpit, but is not linked anymore...',
        badge: 'Unlinked',
        badgeVariant: 'secondary',
        icon: RefreshCwOffIcon,
        tone: 'text-muted-foreground',
    },
};

function statusOf(value: string | undefined): Status {
    return value !== undefined && value in STATUS_VIEWS ? (value as Status) : 'NONE';
}

export function CockpitPage() {
    const base = useOrganizationPath();
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Discover cockpit' }]);
    const { data, error } = useQuery({ queryKey: ['installation-entity'], queryFn: getInstallation });

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Cockpit" description="Manage organizations and environments centrally with Gravitee Cockpit." />
            {error ? (
                <ErrorPanel description="Could not load the installation." error={error} />
            ) : !data ? (
                <Skeleton className="h-72 w-full" />
            ) : (
                <InstallationDetails installation={data} />
            )}
        </div>
    );
}

function InstallationDetails({ installation }: { readonly installation: Installation }) {
    const status = statusOf(installation.additionalInformation?.COCKPIT_INSTALLATION_STATUS);
    const view = STATUS_VIEWS[status];
    const Icon = view.icon;
    const url = installation.additionalInformation?.COCKPIT_URL;
    return (
        <>
            <Card>
                <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-3">
                        <Icon className={`size-6 ${view.tone}`} aria-hidden />
                    </div>
                    <h2 className="text-lg font-semibold">{view.title}</h2>
                    <p className="max-w-xl text-sm text-muted-foreground">{view.message}</p>
                    {url && (
                        <Button variant={status === 'NONE' ? 'default' : 'outline'} asChild>
                            <a href={url} target="_blank" rel="noopener noreferrer">
                                {status === 'NONE' ? 'Discover Cockpit' : 'Open Cockpit'}
                                <ExternalLinkIcon aria-hidden />
                            </a>
                        </Button>
                    )}
                </CardContent>
            </Card>
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Installation</CardTitle>
                </CardHeader>
                <CardContent>
                    <dl className="grid gap-5 text-sm sm:grid-cols-3">
                        <div className="min-w-0">
                            <dt className="text-muted-foreground">Status</dt>
                            <dd className="mt-1">
                                <Badge variant={view.badgeVariant}>{view.badge}</Badge>
                            </dd>
                        </div>
                        <div className="min-w-0">
                            <dt className="text-muted-foreground">Installation ID</dt>
                            <dd className="mt-1">
                                <CopyableText className="max-w-full" value={installation.id} />
                            </dd>
                        </div>
                        <div className="min-w-0">
                            <dt className="text-muted-foreground">Cockpit URL</dt>
                            <dd className="mt-1 break-all font-mono">{url}</dd>
                        </div>
                    </dl>
                </CardContent>
            </Card>
        </>
    );
}
