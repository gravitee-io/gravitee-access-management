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
import { Button, PageFocused, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { createScope } from '../../lib/api/scopes';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { ScopeFields, type ScopeFormValues } from './ScopeFields';
import { consentDraftInvalid, draftSeconds } from './consent';

const DEFAULTS: ScopeFormValues = {
    key: '',
    name: '',
    description: '',
    discovery: false,
    parameterized: false,
    amount: '',
    unit: '',
    clearedExpiry: false,
};

/** The Angular `ScopeCreationComponent`: key, name and description are required, the consent period is optional. */
export function CreateScopePage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/scopes`;
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Scopes', to: listPath },
        { label: 'New' },
    ]);
    const form = useForm<ScopeFormValues>({ mode: 'onChange', defaultValues: DEFAULTS });
    const { isDirty, isValid, errors } = form.formState;
    const [draftAmount, draftUnit] = useWatch({ control: form.control, name: ['amount', 'unit'] });
    const consentInvalid = consentDraftInvalid({ amount: draftAmount, unit: draftUnit });

    const create = useMutation({
        mutationFn: (values: ScopeFormValues) =>
            createScope(ref, {
                key: values.key,
                name: values.name,
                description: values.description,
                // Angular sends a switch only once it was used. An unused one stays at its default and is left out.
                discovery: values.discovery || undefined,
                parameterized: values.parameterized || undefined,
                expiresIn: draftSeconds(values),
            }),
        onSuccess: scope => {
            void queryClient.invalidateQueries({ queryKey: ['scopes', ref] });
            toast.success(`Scope ${scope.name} created`);
            navigate(`${listPath}/${scope.id}`);
        },
        onError: error => toast.error(`Could not create the scope. ${error.message}`),
    });

    return (
        <PageFocused>
            <form noValidate onSubmit={form.handleSubmit(values => create.mutate(values))} className="flex flex-col gap-6">
                <div className="flex flex-col gap-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2 self-start">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Scopes
                        </Link>
                    </Button>
                    <h1>Create a scope</h1>
                    <p className="text-sm text-muted-foreground">
                        Scopes limit access to a resource, for example WRITE access to restricted resources, or READ access to public ones.
                    </p>
                </div>
                <ScopeFields control={form.control} register={form.register} errors={errors} canEdit />
                <div className="flex items-center justify-between">
                    <Button type="button" variant="outline" disabled={create.isPending} onClick={() => navigate(listPath)}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={!isDirty || !isValid || consentInvalid || create.isPending}>
                        {create.isPending ? 'Creating…' : 'Create scope'}
                    </Button>
                </div>
            </form>
        </PageFocused>
    );
}
