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
import { Button, Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle, Spinner } from '@gravitee/graphene-core';
import { GlobeIcon } from '@gravitee/graphene-core/icons';
import { Link, Navigate } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { useEnvironmentDomains } from '../../lib/session/domains';
import { usePermissions } from '../../lib/session/permissions';

export function EnvironmentPage() {
    const { data, error } = useEnvironmentDomains();
    const { has } = usePermissions();

    if (error) {
        return <ErrorPanel description="Could not load the security domains." error={error} />;
    }
    if (!data) {
        return <Spinner />;
    }
    const first = data.data[0];
    if (first) {
        return <Navigate to={`domains/${first.hrid}`} replace />;
    }
    const canCreate = has('domain_create');
    return (
        <Empty className="border border-border">
            <EmptyHeader>
                <EmptyMedia variant="icon">
                    <GlobeIcon />
                </EmptyMedia>
                <EmptyTitle>No security domains</EmptyTitle>
                <EmptyDescription>
                    A security domain holds the applications, users and identity providers that share authentication and authorization
                    settings.
                </EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
                {canCreate ? (
                    <Button asChild>
                        <Link to="new-domain">Create a Security Domain</Link>
                    </Button>
                ) : (
                    <>
                        <Button disabled>Create a Security Domain</Button>
                        <p className="text-sm text-muted-foreground">
                            You do not have the permission to create a security domain. Ask an administrator for access.
                        </p>
                    </>
                )}
            </EmptyContent>
        </Empty>
    );
}
