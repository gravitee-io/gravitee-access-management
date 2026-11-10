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
import { Button, DataTable } from '@gravitee/graphene-core';
import { LockIcon, PencilIcon } from '@gravitee/graphene-core/icons';
import type { ColumnDef } from '@tanstack/react-table';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { usePermissions } from '../../lib/session/permissions';
import { useOrganizationPath } from './useOrganizationPath';

interface FormTemplate {
    readonly template: string;
    readonly name: string;
    readonly description: string;
}

/** The organization customizes one page, the login page of the console itself. */
const ORGANIZATION_FORMS: readonly FormTemplate[] = [{ template: 'LOGIN', name: 'Login', description: 'Login page to authenticate users' }];

export function OrganizationFormsPage() {
    const base = useOrganizationPath();
    const { has } = usePermissions();
    const canView = has('organization_form_read');
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Forms' }]);

    const columns: ColumnDef<FormTemplate, unknown>[] = [
        {
            accessorKey: 'name',
            header: 'Name',
            enableSorting: false,
            cell: ({ row }) => (
                <div className="flex flex-col">
                    <span className="font-medium">{row.original.name}</span>
                    <small className="text-muted-foreground">{row.original.description}</small>
                </div>
            ),
        },
        {
            id: 'actions',
            header: () => <span className="sr-only">Actions</span>,
            enableSorting: false,
            cell: ({ row }) =>
                canView ? (
                    <Button variant="ghost" size="icon" asChild>
                        <Link
                            to={`${base}/forms/form?template=${row.original.template}`}
                            aria-label={`Edit ${row.original.name} template`}
                            title="Edit template"
                        >
                            <PencilIcon />
                        </Link>
                    </Button>
                ) : (
                    <span title="You don't have required permission">
                        <LockIcon aria-label="You don't have required permission" className="size-4 text-muted-foreground" />
                    </span>
                ),
        },
    ];

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Forms" description="The sign-in page of the Access Management console itself." />
            <DataTable aria-label="Forms" columns={columns} data={[...ORGANIZATION_FORMS]} />
        </div>
    );
}
