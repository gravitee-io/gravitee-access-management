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
import { Button, Card, CardContent, Field, FieldDescription, FieldLabel, Input, PageFocused, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createTag } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useSession } from '../../lib/session/session';
import { useOrganizationPath } from './useOrganizationPath';

export function NewTagPage() {
    const { user } = useSession();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const base = useOrganizationPath();
    const listPath = `${base}/tags`;
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const create = useMutation({
        mutationFn: () => createTag(user.org, { name, description }),
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['tags', user.org] });
            toast.success(`Sharding Tag ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: error => toast.error(`Could not create the sharding tag. ${error.message}`),
    });
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Sharding tags', to: listPath }, { label: 'New' }]);

    const valid = name.trim() !== '';
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
                            Sharding tags
                        </Link>
                    </Button>
                    <h1>New Sharding Tag</h1>
                    <p className="text-sm text-muted-foreground">
                        A sharding-tag determines how security domain will be deployed across multiple AM Gateway.
                    </p>
                </div>
                <Card>
                    <CardContent className="grid gap-5">
                        <Field>
                            <FieldLabel htmlFor="tag-name">Name</FieldLabel>
                            <Input id="tag-name" value={name} onChange={event => setName(event.target.value)} required />
                            <FieldDescription>A name for the sharding tag.</FieldDescription>
                        </Field>
                        <Field>
                            <FieldLabel htmlFor="tag-description">Description</FieldLabel>
                            <Input id="tag-description" value={description} onChange={event => setDescription(event.target.value)} />
                            <FieldDescription>A description for the sharding tag.</FieldDescription>
                        </Field>
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
