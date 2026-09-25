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
import { Button, Field, FieldLabel, Spinner, Switch, toast } from '@gravitee/graphene-core';
import { CodeEditor } from '@gravitee/graphene-core/code-editor';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { type Form, getForm, saveForm } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';

const TEMPLATE = 'LOGIN';

function LoginFormEditor({ form }: { readonly form: Form }) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const [draft, setDraft] = useState(form);
    const save = useMutation({
        mutationFn: () => saveForm(ref, draft),
        onSuccess: saved => {
            queryClient.setQueryData(['form', ref, TEMPLATE], saved);
            setDraft(saved);
            toast.success('Login form saved.');
        },
        onError: error => toast.error(`Could not save the login form. ${error.message}`),
    });
    const dirty = draft.content !== form.content || draft.enabled !== form.enabled;

    return (
        <div className="flex flex-col gap-4">
            <Field orientation="horizontal">
                <Switch id="custom-form" checked={draft.enabled} onCheckedChange={enabled => setDraft({ ...draft, enabled })} />
                <FieldLabel htmlFor="custom-form">Use a custom login form</FieldLabel>
            </Field>
            <CodeEditor
                language="html"
                height="60vh"
                value={draft.content}
                disabled={!draft.enabled}
                onChange={content => setDraft({ ...draft, content: content ?? '' })}
            />
            <div>
                <Button onClick={() => save.mutate()} disabled={!dirty || save.isPending}>
                    Save
                </Button>
            </div>
        </div>
    );
}

export function LoginFormPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: basePath }, { label: 'Login form' }]);
    const { data: form, error } = useQuery({ queryKey: ['form', ref, TEMPLATE], queryFn: () => getForm(ref, TEMPLATE) });

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Login form" description="The HTML template of the sign-in page, rendered by the gateway with Thymeleaf." />
            {error && <p className="text-sm text-destructive">Could not load the login form.</p>}
            {form ? <LoginFormEditor key={form.id ?? 'default'} form={form} /> : !error && <Spinner />}
        </div>
    );
}
