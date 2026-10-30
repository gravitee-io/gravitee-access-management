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
    Checkbox,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    FieldLegend,
    FieldSet,
    Input,
    Label,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { TagInput } from '../../app/components/TagInput';
import { patchDomainWebProtectionSettings } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { CspDirectiveFields } from './CspDirectiveFields';
import { ProtectionSection } from './ProtectionSection';
import { type CspDirectiveRow, allowsValue, analyzeCspDirectives } from './csp-directives';
import { type Cors, type Csp, type FormState, type Header, fromDomain, nameError, toSettings, valueError } from './web-protection-form';

const FORM_ID = 'domain-web-protection-form';
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE', 'CONNECT'];
const XFRAME_ACTIONS = ['DENY', 'SAMEORIGIN'];
const NO_ANALYSIS = { rows: [], formErrors: [], hasBlockingIssue: false };

export function DomainWebProtectionPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Web Protection' },
    ]);

    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () => patchDomainWebProtectionSettings(ref, toSettings(form)),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Web protection settings for ${updated.name} updated`);
        },
        onError: error => toast.error(`Could not update the web protection settings. ${error.message}`),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const corsActive = form.cors.inherited === false && form.cors.enabled === true;
    const cspActive = form.csp.inherited === false && form.csp.enabled === true;
    const analysis = cspActive ? analyzeCspDirectives([...form.directives], form.csp.reportOnly === true) : NO_ANALYSIS;
    const rowErrors = form.directives.map((row, index) => ({
        name: nameError(row, analysis.rows[index]?.duplicate === true),
        value: valueError(row),
    }));
    const maxAgeError =
        corsActive && typeof form.cors.maxAge === 'number' && form.cors.maxAge < 0 ? 'Enter a number of seconds, 0 or more.' : undefined;
    const invalid = !!maxAgeError || (cspActive && (analysis.hasBlockingIssue || rowErrors.some(errors => errors.name || errors.value)));

    const setCors = (patch: Partial<Cors>) => setForm(current => ({ ...current, cors: { ...current.cors, ...patch } }));
    const setCsp = (patch: Partial<Csp>) => setForm(current => ({ ...current, csp: { ...current.csp, ...patch } }));
    const setXframe = (patch: Partial<Header>) => setForm(current => ({ ...current, xframe: { ...current.xframe, ...patch } }));
    const setXss = (patch: Partial<Header>) => setForm(current => ({ ...current, xss: { ...current.xss, ...patch } }));
    const setDirectives = (directives: readonly CspDirectiveRow[]) => setForm(current => ({ ...current, directives }));
    const setRow = (index: number, patch: Partial<CspDirectiveRow>) =>
        setForm(current => ({
            ...current,
            directives: current.directives.map((row, i) => {
                if (i !== index) {
                    return row;
                }
                const next = { ...row, ...patch };
                // A directive that takes no value must not keep the previous one.
                return allowsValue(next.name) ? next : { ...next, value: '' };
            }),
        }));
    const allowedMethods = form.cors.allowedMethods ?? [];
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Web Protection"
                description="HTTP security headers and CORS for this security domain. Each setting follows the platform configuration until you turn inheritance off. Changes apply without a restart."
            />

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                <ProtectionSection
                    id="cors"
                    title="Cross-origin resource sharing (CORS)"
                    inheritHint="Use the same cross-origin settings as the rest of the platform."
                    enableLabel="Enable CORS"
                    enableHint="Turn this off to reject cross-origin requests for this domain, even if CORS is enabled at platform level."
                    settings={form.cors}
                    editable={canEdit}
                    onChange={setCors}
                >
                    <Field>
                        <FieldLabel htmlFor="cors-origins">Allow-Origin</FieldLabel>
                        <TagInput
                            id="cors-origins"
                            values={form.cors.allowedOrigins ?? []}
                            disabled={!canEdit}
                            addOnBlur
                            placeholder="*, https://mydomain.com, (http|https).*.mydomain.com"
                            onChange={allowedOrigins => setCors({ allowedOrigins })}
                            onDuplicate={value => toast.error(`Error : Origin "${value}" already exists`)}
                        />
                        <FieldDescription>
                            URIs that may access the resource. Enter * to allow all origins. Regular expressions are supported.
                        </FieldDescription>
                    </Field>
                    <FieldSet>
                        <FieldLegend variant="label">Allow-Methods</FieldLegend>
                        <div className="flex flex-wrap gap-4">
                            {HTTP_METHODS.map(method => (
                                <div key={method} className="flex items-center gap-2">
                                    <Checkbox
                                        id={`cors-method-${method}`}
                                        checked={allowedMethods.includes(method)}
                                        disabled={!canEdit}
                                        onCheckedChange={checked =>
                                            setCors({
                                                allowedMethods: checked
                                                    ? HTTP_METHODS.filter(
                                                          candidate => candidate === method || allowedMethods.includes(candidate),
                                                      )
                                                    : allowedMethods.filter(candidate => candidate !== method),
                                            })
                                        }
                                    />
                                    <Label htmlFor={`cors-method-${method}`} className="font-mono text-sm font-normal">
                                        {method}
                                    </Label>
                                </div>
                            ))}
                        </div>
                        <FieldDescription>The methods allowed to access the resource.</FieldDescription>
                    </FieldSet>
                    <Field>
                        <FieldLabel htmlFor="cors-headers">Allow-Headers</FieldLabel>
                        <TagInput
                            id="cors-headers"
                            values={form.cors.allowedHeaders ?? []}
                            disabled={!canEdit}
                            addOnBlur
                            placeholder="Content-Type"
                            onChange={allowedHeaders => setCors({ allowedHeaders })}
                            onDuplicate={value => toast.error(`Error : Header "${value}" already exists`)}
                        />
                        <FieldDescription>The headers allowed in cross-origin requests.</FieldDescription>
                    </Field>
                    <Field data-invalid={!!maxAgeError}>
                        <FieldLabel htmlFor="cors-max-age">Max age (seconds)</FieldLabel>
                        <Input
                            id="cors-max-age"
                            type="number"
                            min={0}
                            placeholder="86400 (1 day)"
                            value={form.cors.maxAge ?? ''}
                            disabled={!canEdit}
                            aria-invalid={!!maxAgeError}
                            aria-describedby={maxAgeError ? 'cors-max-age-error' : undefined}
                            onChange={event => setCors({ maxAge: event.target.value === '' ? undefined : Number(event.target.value) })}
                        />
                        {maxAgeError ? (
                            <FieldError id="cors-max-age-error">{maxAgeError}</FieldError>
                        ) : (
                            <FieldDescription>How long the results of a pre-flight request can be cached.</FieldDescription>
                        )}
                    </Field>
                    <SettingToggle
                        id="cors-credentials"
                        label="Allow use of credentials"
                        description="Allow cookies, authorization headers or TLS client certificates on cross-origin requests."
                        checked={form.cors.allowCredentials === true}
                        disabled={!canEdit}
                        onChange={allowCredentials => setCors({ allowCredentials })}
                    />
                </ProtectionSection>

                <ProtectionSection
                    id="csp"
                    title="Content Security Policy (CSP)"
                    inheritHint="Use the same Content Security Policy as the rest of the platform."
                    enableLabel="Enable CSP"
                    enableHint="Turn this off to remove the Content-Security-Policy header for this domain."
                    settings={form.csp}
                    editable={canEdit}
                    onChange={setCsp}
                >
                    <SettingToggle
                        id="csp-report-only"
                        label="Report only"
                        description="Deliver the policy as Content-Security-Policy-Report-Only."
                        checked={form.csp.reportOnly === true}
                        disabled={!canEdit}
                        onChange={reportOnly => setCsp({ reportOnly })}
                    />
                    <SettingToggle
                        id="csp-nonce"
                        label="Script inline nonce"
                        description="Include a nonce in the script-src directive for inline scripts."
                        checked={form.csp.scriptInlineNonce === true}
                        disabled={!canEdit}
                        onChange={scriptInlineNonce => setCsp({ scriptInlineNonce })}
                    />
                    <div className="flex flex-col gap-1">
                        <p className="text-sm font-medium">Directives</p>
                        <p className="text-sm text-muted-foreground">
                            Values are saved as typed. Quote keywords, nonces and hashes (&apos;self&apos;, &apos;none&apos;,
                            &apos;unsafe-inline&apos;); leave URLs, hosts and schemes unquoted (https://cdn.example.com, data:). Separate
                            sources with spaces and leave out semicolons.
                        </p>
                    </div>
                    {form.directives.map((row, index) => (
                        <CspDirectiveFields
                            key={index}
                            index={index}
                            row={row}
                            issue={analysis.rows[index]}
                            nameError={rowErrors[index].name}
                            valueError={rowErrors[index].value}
                            editable={canEdit}
                            onChange={patch => setRow(index, patch)}
                            onRemove={() => setDirectives(form.directives.filter((_, i) => i !== index))}
                        />
                    ))}
                    {analysis.formErrors.map(message => (
                        <p key={message} className="text-sm text-destructive">
                            {message}
                        </p>
                    ))}
                    {canEdit && (
                        <div>
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setDirectives([...form.directives, { name: '', value: '' }])}
                            >
                                <PlusIcon aria-hidden />
                                Add directive
                            </Button>
                        </div>
                    )}
                </ProtectionSection>

                <ProtectionSection
                    id="xframe"
                    title="X-Frame-Options"
                    inheritHint="Use the same X-Frame-Options policy as the rest of the platform."
                    enableLabel="Enable X-Frame-Options"
                    enableHint="Turn this off to omit the X-Frame-Options header for this domain."
                    settings={form.xframe}
                    editable={canEdit}
                    onChange={setXframe}
                >
                    <Field>
                        <FieldLabel htmlFor="xframe-action">Action</FieldLabel>
                        <Select value={form.xframe.action} disabled={!canEdit} onValueChange={action => setXframe({ action })}>
                            <SelectTrigger id="xframe-action" className="w-48">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {XFRAME_ACTIONS.map(action => (
                                    <SelectItem key={action} value={action}>
                                        {action}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <FieldDescription>Whether pages can be displayed in a frame on another site.</FieldDescription>
                    </Field>
                </ProtectionSection>

                <ProtectionSection
                    id="xss"
                    title="X-XSS-Protection"
                    inheritHint="Use the same X-XSS-Protection policy as the rest of the platform."
                    enableLabel="Enable X-XSS-Protection"
                    enableHint="Turn this off to omit the X-XSS-Protection header for this domain."
                    settings={form.xss}
                    editable={canEdit}
                    onChange={setXss}
                >
                    <Field>
                        <FieldLabel htmlFor="xss-action">Action</FieldLabel>
                        <Input
                            id="xss-action"
                            value={form.xss.action ?? ''}
                            disabled={!canEdit}
                            onChange={event => setXss({ action: event.target.value })}
                        />
                        <FieldDescription>Value of the X-XSS-Protection response header.</FieldDescription>
                    </Field>
                </ProtectionSection>
            </form>

            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={invalid}
                    onDiscard={() => setForm(fromDomain(domain))}
                />
            )}
        </div>
    );
}
