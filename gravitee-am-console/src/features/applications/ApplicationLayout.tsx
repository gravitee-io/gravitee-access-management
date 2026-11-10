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
import { Button, Skeleton } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Link, Outlet, useParams } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { APPLICATION_NAV_GROUPS, applicationNavGroups } from '../../app/navigation';
import { type Application, getApplication } from '../../lib/api/applications';
import { isNotFound } from '../../lib/api/http';
import { listApplicationPermissions } from '../../lib/api/permissions';
import { useSectionSidebar } from '../../lib/layout/useSectionSidebar';
import { useCurrentDomain } from '../../lib/session/domain';
import { LoadPermissionScope, RequirePermission, usePermissions } from '../../lib/session/permissions';
import { ApplicationContext, type ApplicationContextValue, applicationQueryKey } from './ApplicationContext';
import { APPLICATION_TYPE_LABELS } from './application-types';

/** The application permissions add to the domain ones, so a member of the application alone can open it. */
export function ApplicationLayout() {
    const { appId = '' } = useParams();
    const { ref } = useCurrentDomain();
    return (
        <LoadPermissionScope queryKey={['application-permissions', ref, appId]} queryFn={() => listApplicationPermissions(ref, appId)}>
            <RequirePermission anyOf={['application_read']}>
                <ApplicationLoader applicationId={appId} />
            </RequirePermission>
        </LoadPermissionScope>
    );
}

function ApplicationLoader({ applicationId }: { readonly applicationId: string }) {
    const { ref, basePath } = useCurrentDomain();
    const listPath = `${basePath}/applications`;
    const { data: application, error } = useQuery({
        queryKey: applicationQueryKey(ref, applicationId),
        queryFn: () => getApplication(ref, applicationId),
    });

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Application not found"
                description="This application does not exist or has been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to applications</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the application." error={error} />
        );
    }
    if (!application) {
        return <Skeleton className="h-64 w-full" />;
    }
    return <ApplicationSection application={application} listPath={listPath} />;
}

function ApplicationSection({ application, listPath }: { readonly application: Application; readonly listPath: string }) {
    const { domain, basePath, domainsPath } = useCurrentDomain();
    const { hasAny } = usePermissions();
    const applicationPath = `${listPath}/${application.id}`;
    useSectionSidebar({
        title: application.name,
        badges: [APPLICATION_TYPE_LABELS[application.type], application.enabled ? 'Enabled' : 'Disabled'],
        groups: applicationNavGroups(APPLICATION_NAV_GROUPS, { type: application.type, samlEnabled: !!domain.saml?.enabled, hasAny }),
        base: applicationPath,
        crumbs: [
            { label: 'Domains', to: domainsPath },
            { label: domain.name, to: `${basePath}/dashboard` },
            { label: 'Applications', to: listPath },
            { label: application.name, to: `${applicationPath}/overview` },
        ],
    });
    const context = useMemo<ApplicationContextValue>(
        () => ({ application, applicationPath, listPath }),
        [application, applicationPath, listPath],
    );
    return (
        <ApplicationContext value={context}>
            <Outlet />
        </ApplicationContext>
    );
}
