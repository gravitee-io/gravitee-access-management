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
import { Button, Card, CardContent, PageFocused, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createOrganizationEntrypoint } from '../../lib/api/entrypoints';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useSession } from '../../lib/session/session';
import { EntrypointFields, type EntrypointDraft, isEntrypointValid } from './EntrypointFields';
import { useOrganizationPath } from './useOrganizationPath';

export function NewOrganizationEntrypointPage() {
    const { user } = useSession();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const base = useOrganizationPath();
    const listPath = `${base}/entrypoints`;
    const [draft, setDraft] = useState<EntrypointDraft>({ name: '', description: '', url: '', tags: [] });
    const create = useMutation({
        mutationFn: () =>
            createOrganizationEntrypoint(user.org, { name: draft.name, description: draft.description, url: draft.url, tags: [] }),
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['organization-entrypoints', user.org] });
            toast.success(`Entrypoint ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: error => toast.error(`Could not create the entrypoint. ${error.message}`),
    });
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Entrypoints', to: listPath }, { label: 'New' }]);

    const valid = isEntrypointValid(draft);
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (valid) {
            create.mutate();
        }
    };

    return (
        <PageFocused>
            <form className="space-y-6" onSubmit={submit}>
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Entrypoints
                        </Link>
                    </Button>
                    <h1>New Entrypoint</h1>
                    <p className="text-sm text-muted-foreground">
                        An entrypoint allows you to display the url to use when end-user applications will contact the AM Gateway.
                    </p>
                </div>
                <Card>
                    <CardContent>
                        <EntrypointFields draft={draft} onChange={patch => setDraft(current => ({ ...current, ...patch }))} />
                    </CardContent>
                </Card>
                <div className="flex items-center justify-end gap-2">
                    <Button asChild variant="outline">
                        <Link to={listPath}>Cancel</Link>
                    </Button>
                    <Button type="submit" disabled={!valid || create.isPending}>
                        {create.isPending ? 'Creating…' : 'Create'}
                    </Button>
                </div>
            </form>
        </PageFocused>
    );
}
