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
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldLabel,
    Input,
    PageFocused,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { SaveBar } from '../../app/components/SaveBar';
import { deleteTag, type Tag, updateTag } from '../../lib/api/tags';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { DeleteSection } from './DeleteSection';
import { TagUsageCard } from './TagUsageCard';

const FORM_ID = 'tag-form';

export function TagDetail({ tag, listPath }: { readonly tag: Tag; readonly listPath: string }) {
    const { user } = useSession();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const canEdit = has('organization_tag_update');
    const canDelete = has('organization_tag_delete');
    const [name, setName] = useState(tag.name);
    const [description, setDescription] = useState(tag.description ?? '');
    const save = useMutation({
        mutationFn: () => updateTag(user.org, tag.id, { name, description }),
        onSuccess: updated => {
            queryClient.setQueryData(['tag', user.org, tag.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['tags', user.org] });
            setName(updated.name);
            setDescription(updated.description ?? '');
            toast.success('Sharding tag updated');
        },
        onError: updateError => toast.error(`Could not update the sharding tag. ${updateError.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteTag(user.org, tag.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['tags', user.org] });
            toast.success('Sharding tag deleted');
            navigate(listPath);
        },
        onError: deleteError => toast.error(`Could not delete the sharding tag. ${deleteError.message}`),
    });

    const dirty = name !== tag.name || description !== (tag.description ?? '');
    const valid = name.trim() !== '';
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (valid) {
            save.mutate();
        }
    };

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Sharding tags
                        </Link>
                    </Button>
                    <h1>{tag.name}</h1>
                </div>

                <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">Details</CardTitle>
                        </CardHeader>
                        <CardContent className="grid gap-5">
                            <Field>
                                <FieldLabel htmlFor="tag-id">ID</FieldLabel>
                                <Input id="tag-id" value={tag.id} disabled readOnly />
                                <FieldDescription>
                                    Sharding tag’s technical id which will be set to the gateway configuration file
                                </FieldDescription>
                            </Field>
                            <Field>
                                <FieldLabel htmlFor="tag-name">Name</FieldLabel>
                                <Input
                                    id="tag-name"
                                    value={name}
                                    readOnly={!canEdit}
                                    required
                                    onChange={event => setName(event.target.value)}
                                />
                                <FieldDescription>Sharding tag’s name</FieldDescription>
                            </Field>
                            <Field>
                                <FieldLabel htmlFor="tag-description">Description</FieldLabel>
                                <Input
                                    id="tag-description"
                                    value={description}
                                    readOnly={!canEdit}
                                    onChange={event => setDescription(event.target.value)}
                                />
                                <FieldDescription>Sharding tag’s description</FieldDescription>
                            </Field>
                        </CardContent>
                    </Card>
                </form>

                <TagUsageCard tagId={tag.id} />

                {canEdit && (
                    <SaveBar
                        formId={FORM_ID}
                        dirty={dirty}
                        saving={save.isPending}
                        invalid={!valid}
                        onDiscard={() => {
                            setName(tag.name);
                            setDescription(tag.description ?? '');
                        }}
                    />
                )}

                {canDelete && (
                    <DeleteSection
                        heading="Delete sharding tag"
                        description="Sharding tag will be deleted and will not be used for gateway deployment. This operation cannot be undone."
                        confirmTitle="Delete Sharding Tag"
                        confirmDescription="Are you sure you want to delete this sharding tag ?"
                        pending={remove.isPending}
                        onDelete={() => remove.mutate()}
                    />
                )}
            </div>
        </PageFocused>
    );
}
