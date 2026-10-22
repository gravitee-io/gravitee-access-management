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
    Checkbox,
    Combobox,
    ComboboxContent,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Label,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Tooltip,
    TooltipContent,
    TooltipTrigger,
    toast,
} from '@gravitee/graphene-core';
import { InfoIcon, PlusIcon, Trash2Icon, TriangleAlertIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { TagInput } from '../../app/components/TagInput';
import { type CorsSettings, type Domain, type WebProtectionSettings, patchDomainWebProtectionSettings } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import {
    CSP_DIRECTIVE_NAME_PATTERN,
    CSP_DIRECTIVE_NAMES,
    CSP_DIRECTIVE_VALUE_PATTERN,
    type CspDirectiveRow,
    allowsValue,
    analyzeCspDirectives,
    parseCspDirectives,
    requiresValue,
    serializeCspDirectives,
} from './csp-directives';

type Mutable<T> = { -readonly [K in keyof T]: T[K] };
type Cors = Mutable<CorsSettings>;
type Csp = Mutable<Omit<NonNullable<WebProtectionSettings['csp']>, 'directives'>>;
type Header = Mutable<NonNullable<WebProtectionSettings['xframe']>>;

interface FormState {
    readonly cors: Cors;
    readonly csp: Csp;
    readonly directives: readonly CspDirectiveRow[];
    readonly xframe: Header;
    readonly xss: Header;
}

interface Inheritable {
    readonly inherited?: boolean;
    readonly enabled?: boolean;
}

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE', 'CONNECT'];
const XFRAME_ACTIONS = ['DENY', 'SAMEORIGIN'];
const NAME_RE = new RegExp(`^${CSP_DIRECTIVE_NAME_PATTERN}$`);
const VALUE_RE = new RegExp(`^${CSP_DIRECTIVE_VALUE_PATTERN}$`);
const NO_ANALYSIS = { rows: [], formErrors: [], hasBlockingIssue: false };

// The Angular defaults for a domain saved before these settings existed.
const DEFAULT_CORS: Cors = {
    inherited: true,
    allowedOrigins: [],
    allowedMethods: [],
    allowedHeaders: [],
    allowCredentials: false,
    enabled: false,
};
const DEFAULT_CSP = { inherited: true, enabled: false, reportOnly: false, scriptInlineNonce: true, directives: [] };
const DEFAULT_XFRAME: Header = { inherited: true, enabled: false, action: 'DENY' };
const DEFAULT_XSS: Header = { inherited: true, enabled: false, action: '1; mode=block' };

/** The Angular `normalizeInherited`: a section saved before `inherited` existed inherits unless it was enabled. */
function normalize<T extends Inheritable>(settings: T): T {
    return { ...settings, inherited: settings.inherited ?? !settings.enabled };
}

/** The Angular `initializeSettings`. */
function fromDomain(domain: Domain): FormState {
    const cors = domain.corsSettings ?? DEFAULT_CORS;
    const { directives = [], ...csp } = domain.webProtectionSettings?.csp ?? DEFAULT_CSP;
    return {
        cors: normalize({
            ...cors,
            allowedOrigins: cors.allowedOrigins ?? [],
            allowedHeaders: cors.allowedHeaders ?? [],
            maxAge: cors.maxAge === 0 ? undefined : cors.maxAge,
        }),
        csp: normalize(csp),
        directives: parseCspDirectives([...directives]),
        xframe: normalize(domain.webProtectionSettings?.xframe ?? DEFAULT_XFRAME),
        xss: normalize(domain.webProtectionSettings?.xss ?? DEFAULT_XSS),
    };
}

function nameError(row: CspDirectiveRow, duplicate: boolean): string | undefined {
    if (!row.name) {
        return 'Enter a directive name';
    }
    if (!NAME_RE.test(row.name)) {
        return 'Use letters, digits and hyphens only';
    }
    return duplicate ? 'This directive is already configured' : undefined;
}

function valueError(row: CspDirectiveRow): string | undefined {
    if (!allowsValue(row.name)) {
        return undefined;
    }
    if (requiresValue(row.name) && !row.value) {
        return 'Enter a value';
    }
    return VALUE_RE.test(row.value) ? undefined : 'Use one directive per row, without ";" or control characters';
}

/** The Angular `filteredDirectiveNames`: the known names that contain the typed text. */
function directiveNames(name: string): string[] {
    const typed = name.trim().toLowerCase();
    return typed ? CSP_DIRECTIVE_NAMES.filter(candidate => candidate.includes(typed)) : CSP_DIRECTIVE_NAMES;
}

interface ProtectionSectionProps {
    readonly id: string;
    readonly title: string;
    readonly inheritHint: string;
    readonly enableLabel: string;
    readonly enableHint: string;
    readonly settings: Inheritable;
    readonly editable: boolean;
    readonly onChange: (patch: Inheritable) => void;
    readonly children?: ReactNode;
}

/** "Inherit platform settings", then "Enable …", then the section's own fields. */
function ProtectionSection({
    id,
    title,
    inheritHint,
    enableLabel,
    enableHint,
    settings,
    editable,
    onChange,
    children,
}: ProtectionSectionProps) {
    const inherited = settings.inherited !== false;
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">{title}</CardTitle>
            </CardHeader>
            <CardContent>
                <SettingToggle
                    id={`${id}-inherit`}
                    label="Inherit platform settings"
                    description={inheritHint}
                    checked={inherited}
                    disabled={!editable}
                    onChange={checked => onChange(checked ? { inherited: true, enabled: false } : { inherited: false })}
                />
                {inherited ? null : (
                    <SettingToggle
                        id={`${id}-enabled`}
                        label={enableLabel}
                        description={enableHint}
                        checked={settings.enabled === true}
                        disabled={!editable}
                        onChange={enabled => onChange({ enabled })}
                    >
                        {settings.enabled ? children : null}
                    </SettingToggle>
                )}
            </CardContent>
        </Card>
    );
}

/**
 * The Angular `DomainSettingsWebProtectionComponent` (Settings › Web Protection): CORS, Content Security Policy,
 * X-Frame-Options and X-XSS-Protection, each inheriting the platform configuration unless overridden. The CSP
 * directives are checked with `csp-directives.ts`, a copy of the Angular helper, and block the save when the policy would be rejected.
 * Saved with the fields of `DomainService.patchWebProtectionSettings`.
 */
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

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () =>
            patchDomainWebProtectionSettings(ref, {
                // Angular sends null for an empty max age, which the int field stores as 0. A missing key keeps the 86400 default.
                corsSettings: { ...form.cors, maxAge: form.cors.maxAge ?? 0 },
                webProtectionSettings: {
                    csp: { ...form.csp, directives: serializeCspDirectives([...form.directives]) },
                    xframe: form.xframe,
                    xss: form.xss,
                },
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Web protection settings for ${updated.name} updated`);
        },
        onError: error => toast.error(error.message || 'Unable to update the web protection settings'),
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

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Web Protection"
                description="HTTP security headers and CORS for this security domain. Each setting follows the platform configuration until you turn inheritance off. Changes apply without a restart."
            />

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
                <div className="grid gap-5">
                    <Field>
                        <FieldLabel htmlFor="cors-origins">Allow-Origin</FieldLabel>
                        <TagInput
                            id="cors-origins"
                            values={form.cors.allowedOrigins ?? []}
                            disabled={!canEdit}
                            placeholder="*, https://mydomain.com, (http|https).*.mydomain.com"
                            onChange={allowedOrigins => setCors({ allowedOrigins })}
                            onDuplicate={value => toast.error(`Error : Origin "${value}" already exists`)}
                        />
                        <FieldDescription>
                            URIs that may access the resource. Enter * to allow all origins. Regular expressions are supported.
                        </FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel>Allow-Methods</FieldLabel>
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
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="cors-headers">Allow-Headers</FieldLabel>
                        <TagInput
                            id="cors-headers"
                            values={form.cors.allowedHeaders ?? []}
                            disabled={!canEdit}
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
                            onChange={event => setCors({ maxAge: event.target.value === '' ? undefined : Number(event.target.value) })}
                        />
                        {maxAgeError ? (
                            <FieldError>{maxAgeError}</FieldError>
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
                </div>
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
                <div className="flex flex-col gap-4">
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
                    {form.directives.map((row, index) => {
                        const issue = analysis.rows[index];
                        const errors = rowErrors[index];
                        return (
                            <div key={index} className="flex items-start gap-3">
                                <Field className="flex-1" data-invalid={!!errors.name}>
                                    <FieldLabel htmlFor={`csp-name-${index}`} className="flex items-center gap-1">
                                        Name
                                        {issue?.unknownName ? (
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <TriangleAlertIcon
                                                        className="size-3.5 text-warning"
                                                        aria-label="Unrecognized directive"
                                                    />
                                                </TooltipTrigger>
                                                <TooltipContent>
                                                    This is not a directive we recognize. It will be saved as entered, so check the
                                                    spelling.
                                                </TooltipContent>
                                            </Tooltip>
                                        ) : null}
                                    </FieldLabel>
                                    <Combobox
                                        items={directiveNames(row.name)}
                                        // A free-text field: the selected value follows the text, and Escape keeps it.
                                        value={row.name}
                                        onValueChange={(name: string | null, details: { reason: string }) =>
                                            details.reason !== 'escape-key' && setRow(index, { name: name ?? '' })
                                        }
                                        inputValue={row.name}
                                        onInputValueChange={(name, details) => details.reason !== 'escape-key' && setRow(index, { name })}
                                        filter={null}
                                        disabled={!canEdit}
                                    >
                                        <ComboboxInput
                                            id={`csp-name-${index}`}
                                            placeholder="default-src"
                                            aria-invalid={!!errors.name}
                                            showTrigger={false}
                                        />
                                        <ComboboxContent>
                                            <ComboboxList>
                                                {(name: string) => (
                                                    <ComboboxItem key={name} value={name}>
                                                        {name}
                                                    </ComboboxItem>
                                                )}
                                            </ComboboxList>
                                        </ComboboxContent>
                                    </Combobox>
                                    {errors.name ? <FieldError>{errors.name}</FieldError> : null}
                                </Field>
                                <Field className="flex-1" data-invalid={!!errors.value}>
                                    <FieldLabel htmlFor={`csp-value-${index}`} className="flex items-center gap-1">
                                        Value
                                        {issue?.reportToNeedsEndpoint ? (
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <InfoIcon
                                                        className="size-3.5 text-muted-foreground"
                                                        aria-label="Reporting endpoint needed"
                                                    />
                                                </TooltipTrigger>
                                                <TooltipContent>
                                                    report-to needs a Reporting-Endpoints header, which AM does not send. Add report-uri as
                                                    well, or have a proxy supply the header.
                                                </TooltipContent>
                                            </Tooltip>
                                        ) : null}
                                    </FieldLabel>
                                    <Input
                                        id={`csp-value-${index}`}
                                        placeholder={allowsValue(row.name) ? "'self' https://cdn.example.com" : 'No value required'}
                                        value={row.value}
                                        disabled={!canEdit || !allowsValue(row.name)}
                                        aria-invalid={!!errors.value}
                                        onChange={event => setRow(index, { value: event.target.value })}
                                    />
                                    {errors.value ? <FieldError>{errors.value}</FieldError> : null}
                                </Field>
                                {canEdit ? (
                                    <Button
                                        variant="ghost"
                                        size="icon-sm"
                                        className="mt-6"
                                        aria-label={`Remove directive ${row.name || index + 1}`}
                                        onClick={() => setDirectives(form.directives.filter((_, i) => i !== index))}
                                    >
                                        <Trash2Icon aria-hidden />
                                    </Button>
                                ) : null}
                            </div>
                        );
                    })}
                    {analysis.formErrors.map(message => (
                        <p key={message} className="text-sm text-destructive">
                            {message}
                        </p>
                    ))}
                    {canEdit ? (
                        <div>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setDirectives([...form.directives, { name: '', value: '' }])}
                            >
                                <PlusIcon aria-hidden />
                                Add directive
                            </Button>
                        </div>
                    ) : null}
                </div>
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

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={invalid}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
