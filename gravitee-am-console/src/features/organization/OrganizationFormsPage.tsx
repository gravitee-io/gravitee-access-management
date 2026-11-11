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
import { Button, Item, ItemActions, ItemContent, ItemDescription, ItemGroup, ItemTitle } from '@gravitee/graphene-core';
import { LockIcon, PencilIcon } from '@gravitee/graphene-core/icons';
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

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Forms" description="The sign-in page of the Access Management console itself." />
            <ItemGroup aria-label="Forms">
                {ORGANIZATION_FORMS.map(form => (
                    <Item key={form.template} variant="outline">
                        <ItemContent>
                            <ItemTitle>{form.name}</ItemTitle>
                            <ItemDescription>{form.description}</ItemDescription>
                        </ItemContent>
                        <ItemActions>
                            {canView ? (
                                <Button variant="ghost" size="icon" asChild>
                                    <Link
                                        to={`${base}/forms/form?template=${form.template}`}
                                        aria-label={`Edit ${form.name} template`}
                                        title="Edit template"
                                    >
                                        <PencilIcon />
                                    </Link>
                                </Button>
                            ) : (
                                <span title="You don't have required permission">
                                    <LockIcon aria-hidden className="size-4 text-muted-foreground" />
                                    <span className="sr-only">You don&apos;t have required permission</span>
                                </span>
                            )}
                        </ItemActions>
                    </Item>
                ))}
            </ItemGroup>
        </div>
    );
}
