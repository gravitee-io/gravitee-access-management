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
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Label,
    Skeleton,
    Switch,
    Tabs,
    TabsContent,
    TabsList,
    TabsTrigger,
    toast,
} from '@gravitee/graphene-core';
import { CodeEditor } from '@gravitee/graphene-core/code-editor';
import { ArrowLeftIcon, InfoIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import {
    createOrganizationForm,
    deleteOrganizationForm,
    getOrganizationForm,
    type OrganizationForm,
    updateOrganizationForm,
} from '../../lib/api/forms';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { LOGIN_FORM_EXAMPLE } from './login-form-example';
import { useOrganizationPath } from './useOrganizationPath';

const DEFAULT_CONTENT = '// Custom form...';
const FORM_ID = 'organization-form';

const templateWords = (template: string) => template.toLowerCase().replace(/_/g, ' ');

const formKey = (organizationId: string, template: string | null) => ['organization-form', organizationId, template];

function displayName(template: string): string {
    const words = templateWords(template);
    return words.charAt(0).toUpperCase() + words.slice(1);
}

export function OrganizationFormPage() {
    const [params] = useSearchParams();
    const template = params.get('template');
    const { user } = useSession();
    const base = useOrganizationPath();
    const listPath = `${base}/forms`;
    const form = useQuery({
        queryKey: formKey(user.org, template),
        queryFn: () => getOrganizationForm(user.org, template ?? ''),
        enabled: template !== null,
    });
    useBreadcrumbs([
        { label: 'Organization', to: `${base}/general` },
        { label: 'Forms', to: listPath },
        { label: template ? templateWords(template) : '' },
    ]);

    if (template === null) {
        return (
            <NotFoundPanel
                title="Form not found"
                description="No form template is selected."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to forms</Link>
                    </Button>
                }
            />
        );
    }
    if (form.error) {
        return <ErrorPanel description="Could not load the form." error={form.error} />;
    }
    if (!form.data) {
        return <Skeleton className="h-72 w-full" />;
    }
    return <FormEditor key={form.dataUpdatedAt} template={template} form={form.data} listPath={listPath} />;
}

interface FormEditorProps {
    readonly template: string;
    readonly form: OrganizationForm;
    readonly listPath: string;
}

function FormEditor({ template, form, listPath }: FormEditorProps) {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const found = form.id !== undefined;
    const canEdit = found ? has('organization_form_update') : has('organization_form_create');
    const canDelete = found && has('organization_form_delete');
    const savedContent = form.content ?? DEFAULT_CONTENT;
    const [enabled, setEnabled] = useState(form.enabled ?? false);
    const [content, setContent] = useState(savedContent);
    const [tab, setTab] = useState('html');
    const [confirmOpen, setConfirmOpen] = useState(false);
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
            setConfirmOpen(false);
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

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Content</CardTitle>
                        <CardDescription>
                            {enabled ? 'The HTML served instead of the default.' : 'Enable the custom form to edit its HTML.'}
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Tabs value={tab} onValueChange={setTab}>
                            <TabsList>
                                <TabsTrigger value="html">Html</TabsTrigger>
                                <TabsTrigger value="preview" disabled={!enabled || !canEdit}>
                                    Preview
                                </TabsTrigger>
                            </TabsList>
                            <TabsContent value="html" className="pt-3">
                                <CodeEditor
                                    language="html"
                                    value={content}
                                    readOnly={!enabled || !canEdit}
                                    height={480}
                                    onChange={value => setContent(value ?? '')}
                                />
                            </TabsContent>
                            <TabsContent value="preview" className="pt-3">
                                {/* No script runs in the preview, as the content is user-authored HTML. */}
                                <iframe
                                    title="Preview"
                                    sandbox=""
                                    srcDoc={content}
                                    className="h-[560px] w-full rounded-md border border-border bg-background"
                                />
                            </TabsContent>
                        </Tabs>
                    </CardContent>
                </Card>
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
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-1 flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete form</p>
                                <p className="text-sm text-muted-foreground">
                                    Form template will be deleted, it will no longer be use to override default {name} template. This
                                    operation cannot be undone.
                                </p>
                            </div>
                            <Button variant="destructive" disabled={remove.isPending} onClick={() => setConfirmOpen(true)}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            )}

            <ConfirmDialog
                open={confirmOpen}
                title="Delete form"
                description="Are you sure you want to delete this form ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmOpen}
                onConfirm={() => remove.mutate()}
            />

            <Dialog open={infoOpen} onOpenChange={setInfoOpen}>
                <DialogContent className="sm:max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>Write your own {phrase} page</DialogTitle>
                        <DialogDescription>This is the minimal HTML element required to perform {phrase}.</DialogDescription>
                    </DialogHeader>
                    {template === 'LOGIN' && (
                        <pre className="max-h-96 overflow-auto rounded-md bg-muted p-3 font-mono text-xs">{LOGIN_FORM_EXAMPLE}</pre>
                    )}
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setInfoOpen(false)}>
                            Close
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
