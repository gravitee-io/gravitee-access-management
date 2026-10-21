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
import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
    PageFocused,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { listDataPlanes } from '../../lib/api/data-planes';
import { createDomain } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentEnvironment } from '../../lib/session/environment';
import { useSession } from '../../lib/session/session';

/**
 * The Angular `DomainCreationComponent`: name, description and data plane. The data plane select only shows when the
 * installation has more than one data plane; with a single one the domain takes it.
 */
export function CreateDomainPage() {
    const { user } = useSession();
    const environment = useCurrentEnvironment();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `/environments/${environment.hrids[0]}/domains`;
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [selectedDataPlaneId, setSelectedDataPlaneId] = useState('');
    const dataPlanes = useQuery({
        queryKey: ['data-planes', user.org, environment.id],
        queryFn: () => listDataPlanes(user.org, environment.id),
        select: planes => [...planes].sort((a, b) => a.name.localeCompare(b.name)),
    });
    const oneDataPlane = dataPlanes.data?.length === 1;
    const dataPlaneId = oneDataPlane ? dataPlanes.data![0].id : selectedDataPlaneId;
    const create = useMutation({
        // The Angular form sends the fields the user filled: the description only once typed.
        mutationFn: () => createDomain(user.org, environment.id, { name, description: description || undefined, dataPlaneId }),
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${created.name} created`);
            navigate(`${listPath}/${created.hrid}`);
        },
        onError: error => toast.error(`Could not create the domain. ${error.message}`),
    });
    useBreadcrumbs([{ label: 'Domains', to: listPath }, { label: 'Create domain' }]);

    const valid = name.trim() !== '' && dataPlaneId !== '';
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (valid) {
            create.mutate();
        }
    };

    if (dataPlanes.error) {
        return <ErrorPanel description="Could not load the data planes, so a domain cannot be created." error={dataPlanes.error} />;
    }

    return (
        <PageFocused>
            <form className="space-y-6" onSubmit={submit}>
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Domains
                        </Link>
                    </Button>
                    <h1>Create a security domain</h1>
                    <p className="text-sm text-muted-foreground">
                        A security domain lets you register applications and users and manage identity providers, for centralized
                        authentication, authorization and user management.
                    </p>
                </div>

                <Card>
                    <CardHeader>
                        <CardTitle>Domain</CardTitle>
                        <CardDescription>You can change these later in the domain settings.</CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <Field>
                            <FieldLabel htmlFor="domain-name">Name</FieldLabel>
                            <Input id="domain-name" value={name} onChange={event => setName(event.target.value)} required />
                            <FieldDescription>
                                The name also makes the domain path, which becomes the context path for its OAuth 2.0 and OpenID Connect
                                endpoints.
                            </FieldDescription>
                        </Field>
                        <Field>
                            <FieldLabel htmlFor="domain-description">Description</FieldLabel>
                            <Textarea
                                id="domain-description"
                                value={description}
                                onChange={event => setDescription(event.target.value)}
                                rows={3}
                            />
                        </Field>
                        {dataPlanes.isPending ? (
                            <Skeleton className="h-9 w-full" />
                        ) : oneDataPlane ? null : (
                            <Field>
                                <FieldLabel htmlFor="domain-data-plane">Data plane</FieldLabel>
                                <Select value={selectedDataPlaneId} onValueChange={setSelectedDataPlaneId}>
                                    <SelectTrigger id="domain-data-plane">
                                        <SelectValue placeholder="Select a data plane" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {dataPlanes.data.map(plane => (
                                            <SelectItem key={plane.id} value={plane.id}>
                                                {plane.name} ({plane.id})
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <FieldDescription>The data plane that serves the traffic of this domain.</FieldDescription>
                            </Field>
                        )}
                    </CardContent>
                </Card>

                <div className="flex items-center justify-end gap-2">
                    <Button asChild variant="outline">
                        <Link to={listPath}>Cancel</Link>
                    </Button>
                    <Button type="submit" disabled={!valid || create.isPending}>
                        {create.isPending ? 'Creating…' : 'Create domain'}
                    </Button>
                </div>
            </form>
        </PageFocused>
    );
}
