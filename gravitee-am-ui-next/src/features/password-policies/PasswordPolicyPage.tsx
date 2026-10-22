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
    Badge,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Checkbox,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Label,
    PageFocused,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon, XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { isNotFound } from '../../lib/api/http';
import {
    assignPasswordPolicy,
    type IdentityProviderSummary,
    listIdentityProviderPlugins,
    listUserIdentityProviders,
} from '../../lib/api/identity-providers';
import {
    createPasswordPolicy,
    getPasswordPolicy,
    type NewPasswordPolicy,
    type PasswordPolicy,
    updatePasswordPolicy,
} from '../../lib/api/password-policies';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

type Form = { -readonly [K in keyof NewPasswordPolicy]?: NewPasswordPolicy[K] | null };

/** Provider id to linked (true) or unlinked (false), for the providers the user changed only. */
type Links = Readonly<Record<string, boolean>>;

/** The Angular `refreshPolicyData`: the stored policy without its id, reference and dates. */
function fromPolicy(policy: PasswordPolicy | undefined): Form {
    if (!policy) {
        return {};
    }
    const {
        id: _id,
        referenceId: _referenceId,
        referenceType: _referenceType,
        createdAt: _createdAt,
        updatedAt: _updatedAt,
        ...rest
    } = policy;
    return rest;
}

/** The checks of the Angular template constraints and of `DomainPasswordPolicyComponent.save`. */
function validate(form: Form) {
    const errors: Partial<Record<'name' | 'minLength' | 'maxLength' | 'expiryDuration' | 'oldPasswords', string>> = {};
    const { name, minLength, maxLength, expiryDuration, passwordHistoryEnabled, oldPasswords } = form;
    if (!name) {
        errors.name = 'A name is required.';
    }
    if (typeof minLength === 'number' && minLength < 0) {
        errors.minLength = 'Min length must be greater than zero and smaller than Max length';
    } else if (typeof minLength === 'number' && typeof maxLength === 'number' && minLength > maxLength) {
        errors.minLength = 'Min length must be smaller than max length.';
    }
    if (typeof maxLength === 'number' && maxLength < 0) {
        errors.maxLength = 'Max length must be greater than zero';
    }
    if (typeof expiryDuration === 'number' && expiryDuration < 0) {
        errors.expiryDuration = 'Enter 0 or more days.';
    }
    if (passwordHistoryEnabled && (!oldPasswords || oldPasswords < 1 || oldPasswords > 24)) {
        errors.oldPasswords = 'Number of old passwords must be within the range [1, 24]';
    }
    return errors;
}

const numberOrNull = (raw: string) => (raw === '' ? null : Number(raw));

/**
 * The Angular `DomainPasswordPolicyComponent`: creates a password policy (`…/password-policies/new`) or edits one
 * (`…/:policyId`), and links it to identity providers. The links live on the providers, so they are saved with
 * `ProviderService.assignPasswordPolicy` after the policy, as Angular does.
 */
export function PasswordPolicyPage() {
    const { policyId } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/password-policies`;
    const policy = useQuery({
        queryKey: ['password-policy', ref, policyId],
        queryFn: () => getPasswordPolicy(ref, policyId ?? ''),
        enabled: !!policyId,
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Password Policies', to: listPath },
        { label: policyId ? (policy.data?.name ?? '…') : 'New' },
    ]);

    if (policy.error) {
        return isNotFound(policy.error) ? (
            <NotFoundPanel
                title="Password policy not found"
                description={`No password policy with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to password policies</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the password policy." error={policy.error} />
        );
    }
    if (policyId && !policy.data) {
        return <Skeleton className="h-64 w-full" />;
    }
    return <PasswordPolicyForm key={`${policy.data?.id}-${policy.data?.updatedAt}`} policy={policy.data} listPath={listPath} />;
}

function PasswordPolicyForm({ policy, listPath }: Readonly<{ policy: PasswordPolicy | undefined; listPath: string }>) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const canEdit = usePermissions().has('domain_settings_update');
    const providers = useQuery({ queryKey: ['user-identity-providers', ref], queryFn: () => listUserIdentityProviders(ref) });
    const plugins = useQuery({ queryKey: ['identity-provider-plugins'], queryFn: listIdentityProviderPlugins, staleTime: Infinity });
    const [form, setForm] = useState<Form>(() => fromPolicy(policy));
    const initialConsecutive = policy?.maxConsecutiveLetters && policy.maxConsecutiveLetters > 0 ? policy.maxConsecutiveLetters : 0;
    const [maxConsecutiveLetters, setMaxConsecutiveLetters] = useState(initialConsecutive);
    const [links, setLinks] = useState<Links>({});
    const [picking, setPicking] = useState(false);

    const save = useMutation({
        mutationFn: async () => {
            const body = {
                ...form,
                maxConsecutiveLetters: maxConsecutiveLetters > 0 ? maxConsecutiveLetters : undefined,
            } as NewPasswordPolicy;
            const saved = policy ? await updatePasswordPolicy(ref, policy.id, body) : await createPasswordPolicy(ref, body);
            const changed = Object.entries(links);
            if (changed.length > 0) {
                try {
                    await Promise.all(changed.map(([id, linked]) => assignPasswordPolicy(ref, id, linked ? saved.id : undefined)));
                } catch (reason) {
                    toast.error(reason instanceof Error ? reason.message : 'Identity providers could not be updated');
                }
            }
            return saved;
        },
        onSuccess: async saved => {
            // The form starts again from the saved policy, so the providers must show the new links first.
            await queryClient.invalidateQueries({ queryKey: ['user-identity-providers', ref] });
            queryClient.setQueryData(['password-policy', ref, saved.id], saved);
            void queryClient.invalidateQueries({ queryKey: ['password-policies', ref] });
            toast.success('Password settings configuration updated');
            if (!policy) {
                navigate(`${listPath}/${saved.id}`, { replace: true });
            }
        },
        onError: error => toast.error(error.message || 'Unable to save the password policy'),
    });

    const errors = validate(form);
    const invalid = Object.keys(errors).length > 0;
    const dirty =
        JSON.stringify(form) !== JSON.stringify(fromPolicy(policy)) ||
        maxConsecutiveLetters !== initialConsecutive ||
        Object.keys(links).length > 0;
    const set = (patch: Partial<Form>) => setForm(current => ({ ...current, ...patch }));
    const discard = () => {
        setForm(fromPolicy(policy));
        setMaxConsecutiveLetters(initialConsecutive);
        setLinks({});
    };

    const isLinked = (provider: IdentityProviderSummary) => links[provider.id] ?? (!!policy && provider.passwordPolicy === policy.id);
    const linkedProviders = (providers.data ?? []).filter(isLinked);
    // A provider that another policy governs is not offered, as in Angular.
    const selectable = (providers.data ?? []).filter(provider => !provider.passwordPolicy || provider.passwordPolicy === policy?.id);
    const typeName = (type: string) => plugins.data?.find(plugin => plugin.id === type)?.displayName ?? type;

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="flex flex-col gap-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2 self-start">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Password policies
                        </Link>
                    </Button>
                    <h1>{policy ? policy.name : 'Create a password policy'}</h1>
                    <p className="text-sm text-muted-foreground">
                        The rules a password must follow, and the identity providers they apply to.
                    </p>
                </div>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">General</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <Field data-invalid={!!errors.name && form.name !== undefined}>
                            <FieldLabel htmlFor="pp-name">Name</FieldLabel>
                            <Input
                                id="pp-name"
                                placeholder="Password policy name"
                                value={form.name ?? ''}
                                disabled={!canEdit}
                                aria-invalid={!!errors.name && form.name !== undefined}
                                onChange={event => set({ name: event.target.value })}
                            />
                            {errors.name && form.name !== undefined ? <FieldError>{errors.name}</FieldError> : null}
                        </Field>
                        <div className="grid gap-5 md:grid-cols-2">
                            <Field data-invalid={!!errors.minLength}>
                                <FieldLabel htmlFor="pp-min">Minimum length</FieldLabel>
                                <Input
                                    id="pp-min"
                                    type="number"
                                    min={0}
                                    value={form.minLength ?? ''}
                                    disabled={!canEdit}
                                    aria-invalid={!!errors.minLength}
                                    onChange={event => set({ minLength: numberOrNull(event.target.value) })}
                                />
                                {errors.minLength ? (
                                    <FieldError>{errors.minLength}</FieldError>
                                ) : (
                                    <FieldDescription>Passwords under 8 characters are considered weak (OWASP).</FieldDescription>
                                )}
                            </Field>
                            <Field data-invalid={!!errors.maxLength}>
                                <FieldLabel htmlFor="pp-max">Maximum length</FieldLabel>
                                <Input
                                    id="pp-max"
                                    type="number"
                                    min={form.minLength ?? 0}
                                    value={form.maxLength ?? ''}
                                    disabled={!canEdit}
                                    aria-invalid={!!errors.maxLength}
                                    onChange={event => set({ maxLength: numberOrNull(event.target.value) })}
                                />
                                {errors.maxLength ? (
                                    <FieldError>{errors.maxLength}</FieldError>
                                ) : (
                                    <FieldDescription>64 is common, given the limits of some hashing algorithms (OWASP).</FieldDescription>
                                )}
                            </Field>
                            <Field>
                                <FieldLabel htmlFor="pp-consecutive">Maximum consecutive letters</FieldLabel>
                                <Select
                                    value={String(maxConsecutiveLetters)}
                                    disabled={!canEdit}
                                    onValueChange={value => setMaxConsecutiveLetters(Number(value))}
                                >
                                    <SelectTrigger id="pp-consecutive" className="w-full">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="0">Unlimited</SelectItem>
                                        {[1, 2, 3, 4, 5].map(count => (
                                            <SelectItem key={count} value={String(count)}>
                                                {count}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <FieldDescription>How many times in a row the same character may repeat.</FieldDescription>
                            </Field>
                            <Field data-invalid={!!errors.expiryDuration}>
                                <FieldLabel htmlFor="pp-expiry">Expiration (days)</FieldLabel>
                                <Input
                                    id="pp-expiry"
                                    type="number"
                                    min={0}
                                    value={form.expiryDuration ?? ''}
                                    disabled={!canEdit}
                                    aria-invalid={!!errors.expiryDuration}
                                    onChange={event => set({ expiryDuration: numberOrNull(event.target.value) })}
                                />
                                {errors.expiryDuration ? (
                                    <FieldError>{errors.expiryDuration}</FieldError>
                                ) : (
                                    <FieldDescription>
                                        Days before users must change their password; 90 or 180 is usual. 0 turns it off.
                                    </FieldDescription>
                                )}
                            </Field>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Rules</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <SettingToggle
                            id="pp-numbers"
                            label="Password must include numbers"
                            checked={form.includeNumbers === true}
                            disabled={!canEdit}
                            onChange={includeNumbers => set({ includeNumbers })}
                        />
                        <SettingToggle
                            id="pp-special"
                            label="Password must include special characters"
                            checked={form.includeSpecialCharacters === true}
                            disabled={!canEdit}
                            onChange={includeSpecialCharacters => set({ includeSpecialCharacters })}
                        />
                        <SettingToggle
                            id="pp-mixed"
                            label="Password must include letters in mixed case"
                            checked={form.lettersInMixedCase === true}
                            disabled={!canEdit}
                            onChange={lettersInMixedCase => set({ lettersInMixedCase })}
                        />
                        <SettingToggle
                            id="pp-dictionary"
                            label="Exclude common passwords"
                            description="Common unsafe passwords are refused (123456, qwertyuiop, trustno1…)."
                            checked={form.excludePasswordsInDictionary === true}
                            disabled={!canEdit}
                            onChange={excludePasswordsInDictionary => set({ excludePasswordsInDictionary })}
                        />
                        <SettingToggle
                            id="pp-profile"
                            label="Exclude user profile information from passwords"
                            description="User names, first, middle and last names, nicknames, email and phone numbers are refused."
                            checked={form.excludeUserProfileInfoInPassword === true}
                            disabled={!canEdit}
                            onChange={excludeUserProfileInfoInPassword => set({ excludeUserProfileInfoInPassword })}
                        />
                        <SettingToggle
                            id="pp-history"
                            label="Maintain password history"
                            description="Keep each user's previous passwords so they cannot be reused."
                            checked={form.passwordHistoryEnabled === true}
                            disabled={!canEdit}
                            onChange={passwordHistoryEnabled => set({ passwordHistoryEnabled })}
                        >
                            {form.passwordHistoryEnabled ? (
                                <Field data-invalid={!!errors.oldPasswords}>
                                    <FieldLabel htmlFor="pp-old">Password history size</FieldLabel>
                                    <Input
                                        id="pp-old"
                                        type="number"
                                        min={1}
                                        max={24}
                                        className="w-48"
                                        value={form.oldPasswords ?? ''}
                                        disabled={!canEdit}
                                        aria-invalid={!!errors.oldPasswords}
                                        onChange={event => set({ oldPasswords: numberOrNull(event.target.value) })}
                                    />
                                    {errors.oldPasswords ? (
                                        <FieldError>{errors.oldPasswords}</FieldError>
                                    ) : (
                                        <FieldDescription>How many previous passwords to remember, up to 24.</FieldDescription>
                                    )}
                                </Field>
                            ) : null}
                        </SettingToggle>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="flex flex-row items-start justify-between gap-4">
                        <div className="flex flex-col gap-1">
                            <CardTitle className="text-base">Identity providers</CardTitle>
                            <CardDescription>
                                The identity providers this policy applies to. Several providers can share one policy.
                            </CardDescription>
                        </div>
                        {canEdit && !providers.error ? (
                            <Button variant="outline" size="sm" disabled={!providers.data} onClick={() => setPicking(true)}>
                                Select identity providers
                            </Button>
                        ) : null}
                    </CardHeader>
                    <CardContent>
                        {providers.error ? (
                            <ErrorPanel description="Could not load the identity providers." error={providers.error} />
                        ) : !providers.data ? (
                            <Skeleton className="h-9 w-full" />
                        ) : linkedProviders.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No identity provider uses this policy.</p>
                        ) : (
                            <div className="flex flex-wrap gap-2">
                                {linkedProviders.map(provider => (
                                    <Badge key={provider.id} variant="secondary" title={typeName(provider.type)}>
                                        {provider.name}
                                        {canEdit ? (
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                className="ml-1 size-4"
                                                aria-label={`Unlink ${provider.name}`}
                                                onClick={() => setLinks(current => ({ ...current, [provider.id]: false }))}
                                            >
                                                <XIcon className="size-3" aria-hidden />
                                            </Button>
                                        ) : null}
                                    </Badge>
                                ))}
                            </div>
                        )}
                    </CardContent>
                </Card>

                {policy ? (
                    <SaveBar
                        canEdit={canEdit}
                        dirty={dirty}
                        saving={save.isPending}
                        invalid={invalid}
                        onDiscard={discard}
                        onSave={() => save.mutate()}
                    />
                ) : (
                    <div className="flex items-center justify-between">
                        <Button type="button" variant="outline" disabled={save.isPending} onClick={() => navigate(listPath)}>
                            Cancel
                        </Button>
                        <Button disabled={!dirty || invalid || save.isPending} onClick={() => save.mutate()}>
                            {save.isPending ? 'Creating…' : 'Create policy'}
                        </Button>
                    </div>
                )}
            </div>

            {picking ? (
                <ProviderPicker
                    providers={selectable}
                    isLinked={isLinked}
                    typeName={typeName}
                    onClose={() => setPicking(false)}
                    onApply={selection => {
                        setLinks(current => ({ ...current, ...selection }));
                        setPicking(false);
                    }}
                />
            ) : null}
        </PageFocused>
    );
}

/** The Angular `PasswordPoliciesIdpSelectDialogComponent`: tick the providers this policy should govern. */
function ProviderPicker({
    providers,
    isLinked,
    typeName,
    onClose,
    onApply,
}: Readonly<{
    providers: readonly IdentityProviderSummary[];
    isLinked: (provider: IdentityProviderSummary) => boolean;
    typeName: (type: string) => string;
    onClose: () => void;
    onApply: (selection: Links) => void;
}>) {
    const [selection, setSelection] = useState<Links>(() =>
        Object.fromEntries(providers.map(provider => [provider.id, isLinked(provider)])),
    );
    const changed = providers.filter(provider => selection[provider.id] !== isLinked(provider));

    return (
        <Dialog open onOpenChange={open => !open && onClose()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Select identity providers</DialogTitle>
                    <DialogDescription>Providers already linked to another policy are not listed.</DialogDescription>
                </DialogHeader>
                {providers.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No identity provider can be linked to this policy.</p>
                ) : (
                    <div className="flex flex-col gap-3">
                        {providers.map(provider => (
                            <div key={provider.id} className="flex items-center gap-2">
                                <Checkbox
                                    id={`pp-idp-${provider.id}`}
                                    checked={!!selection[provider.id]}
                                    onCheckedChange={checked => setSelection(current => ({ ...current, [provider.id]: checked === true }))}
                                />
                                <Label htmlFor={`pp-idp-${provider.id}`} className="font-normal">
                                    {provider.name}
                                </Label>
                                <span className="text-sm text-muted-foreground">{typeName(provider.type)}</span>
                            </div>
                        ))}
                    </div>
                )}
                <DialogFooter>
                    <Button variant="ghost" onClick={onClose}>
                        Cancel
                    </Button>
                    {/* Angular enables "Add Selected" only once a box changed. */}
                    <Button
                        disabled={changed.length === 0}
                        onClick={() => onApply(Object.fromEntries(changed.map(provider => [provider.id, !!selection[provider.id]])))}
                    >
                        Apply
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
