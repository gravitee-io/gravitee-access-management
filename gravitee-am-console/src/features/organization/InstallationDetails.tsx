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
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, cn, CopyableText } from '@gravitee/graphene-core';
import { ExternalLinkIcon } from '@gravitee/graphene-core/icons';
import type { Installation } from '../../lib/api/organization';
import { STATUS_VIEWS, statusOf } from './cockpit-status';

export function InstallationDetails({ installation }: { readonly installation: Installation }) {
    const status = statusOf(installation.additionalInformation?.COCKPIT_INSTALLATION_STATUS);
    const view = STATUS_VIEWS[status];
    const Icon = view.icon;
    const url = installation.additionalInformation?.COCKPIT_URL;
    return (
        <>
            <Card>
                <CardContent className="flex flex-col items-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-3">
                        <Icon className={cn('size-6', view.tone)} aria-hidden />
                    </div>
                    <CardTitle role="heading" aria-level={2} className="text-lg">
                        {view.title}
                    </CardTitle>
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
