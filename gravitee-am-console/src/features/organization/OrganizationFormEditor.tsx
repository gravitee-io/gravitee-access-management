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
import { Button, Card, CardContent, Label, Switch, toast } from '@gravitee/graphene-core';
import { ArrowLeftIcon, InfoIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { SaveBar } from '../../app/components/SaveBar';
import { createOrganizationForm, deleteOrganizationForm, type OrganizationForm, updateOrganizationForm } from '../../lib/api/forms';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { DeleteSection } from './DeleteSection';
import { FormContentCard } from './FormContentCard';
import { FormTemplateInfoDialog } from './FormTemplateInfoDialog';
import { displayName, formKey, templateWords } from './form-template';

const DEFAULT_CONTENT = '// Custom form...';
const FORM_ID = 'organization-form';

interface OrganizationFormEditorProps {
    readonly template: string;
    readonly form: OrganizationForm;
    readonly listPath: string;
}

export function OrganizationFormEditor({ template, form, listPath }: OrganizationFormEditorProps) {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const found = form.id !== undefined;
    const canEdit = found ? has('organization_form_update') : has('organization_form_create');
    const canDelete = found && has('organization_form_delete');
    const savedContent = form.content ?? DEFAULT_CONTENT;
    const [enabled, setEnabled] = useState(form.enabled ?? false);
    const [content, setContent] = useState(savedContent);
    const [infoOpen, setInfoOpen] = useState(false);
    const name = displayName(template);
    const phrase = templateWords(template);
    const cacheKey = formKey(user.org, template);
    const dirty = enabled !== (form.enabled ?? false) || content !== savedContent;

    const save = useMutation({
        mutationFn: () => {
            const input = { template, enabled, content };
            return form.id === undefined ? createOrganizationForm(user.org, input) : updateOrganizationForm(user.org, form.id, input);
        },
        onSuccess: saved => {
            queryClient.setQueryData(cacheKey, saved);
            toast.success(found ? 'Form updated' : 'Form created');
        },
        onError: error => toast.error(`Could not save the form. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteOrganizationForm(user.org, form.id ?? ''),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: cacheKey });
            toast.success('Form deleted');
        },
        onError: error => toast.error(`Could not delete the form. ${error.message}`),
    });

    return (
        <div className="flex flex-col gap-6">
            <div className="space-y-1">
                <Button asChild variant="ghost" size="sm" className="-ml-2">
                    <Link to={listPath}>
                        <ArrowLeftIcon aria-hidden />
                        Forms
                    </Link>
                </Button>
                <div className="flex items-center gap-2">
                    <h1>{name}</h1>
                    <Button variant="ghost" size="icon" aria-label="How to write your own page" onClick={() => setInfoOpen(true)}>
                        <InfoIcon />
                    </Button>
                </div>
            </div>

            <form
                id={FORM_ID}
                className="flex flex-col gap-6"
                onSubmit={event => {
                    event.preventDefault();
                    save.mutate();
                }}
            >
                <Card>
                    <CardContent>
                        <div className="flex items-start justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <Label htmlFor="organization-form-enabled">Enable custom {phrase} form.</Label>
                                <p className="text-sm text-muted-foreground">
                                    Override the default {phrase} page to fit your company requirements.
                                </p>
                            </div>
                            <Switch id="organization-form-enabled" checked={enabled} disabled={!canEdit} onCheckedChange={setEnabled} />
                        </div>
                    </CardContent>
                </Card>

                <FormContentCard enabled={enabled} readOnly={!enabled || !canEdit} content={content} onChange={setContent} />
            </form>

            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    onDiscard={() => {
                        setEnabled(form.enabled ?? false);
                        setContent(savedContent);
                    }}
                />
            )}

            {canDelete && (
                <DeleteSection
                    heading="Delete form"
                    description={`Form template will be deleted, it will no longer be use to override default ${name} template. This operation cannot be undone.`}
                    confirmTitle="Delete form"
                    confirmDescription="Are you sure you want to delete this form?"
                    pending={remove.isPending}
                    onDelete={close => remove.mutate(undefined, { onSuccess: close })}
                />
            )}

            <FormTemplateInfoDialog open={infoOpen} template={template} phrase={phrase} onOpenChange={setInfoOpen} />
        </div>
    );
}
