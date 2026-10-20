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
    DataTable,
    DataTableEmptyState,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
    Field,
    FieldDescription,
    FieldLabel,
    Label,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import {
    CircleXIcon,
    CopyIcon,
    KeyRoundIcon,
    MoreVerticalIcon,
    PlusIcon,
    RefreshCwIcon,
    SettingsIcon,
    Trash2Icon,
    TriangleAlertIcon,
} from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { type Certificate, deleteCertificate, listCertificateKeys, listCertificates, rotateCertificate } from '../../lib/api/certificates';
import { updateCertificateSettings } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { certificateTypeName } from './certificate-types';

const USAGE_LABELS: Record<string, string> = { enc: 'Encryption', sig: 'Signature', mtls: 'mTLS' };

/** The Angular `usageBadgeLabel`: one usage by name, several as a count. */
export function usageLabel(usage: readonly string[]): string {
    if (usage.length > 1) {
        return `${usage.length} usages`;
    }
    if (usage.length === 1) {
        return USAGE_LABELS[usage[0]] ?? usage[0];
    }
    return 'Undefined';
}

function plural(count: number, word: string): string {
    return `${count} ${word}${count === 1 ? '' : 's'}`;
}

function daysUntil(epoch: number): number {
    return Math.ceil((epoch - Date.now()) / (1000 * 3600 * 24));
}

const dateFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' });

function ExpiryCell({ cert }: { readonly cert: Certificate }) {
    if (!cert.expiresAt) {
        return <span className="text-sm text-muted-foreground">—</span>;
    }
    const status =
        cert.status === 'will_expire'
            ? { icon: TriangleAlertIcon, className: 'text-warning', label: `Expiring in ${daysUntil(cert.expiresAt)} days` }
            : cert.status === 'expired'
              ? { icon: CircleXIcon, className: 'text-destructive', label: 'Expired' }
              : cert.status === 'renewed'
                ? {
                      icon: RefreshCwIcon,
                      className: 'text-muted-foreground',
                      label: 'Deprecated in favor of the most recent system certificate',
                  }
                : null;
    const Icon = status?.icon;
    return (
        <span className="flex items-center gap-1.5 text-sm">
            {dateFormat.format(new Date(cert.expiresAt))}
            {Icon && status && (
                <span title={status.label} className="flex items-center">
                    <Icon className={`size-4 ${status.className}`} aria-label={status.label} />
                </span>
            )}
        </span>
    );
}

function UsedBy({ cert }: { readonly cert: Certificate }) {
    const applications = cert.applications ?? [];
    const providers = cert.identityProviders ?? [];
    const parts = [
        ...(applications.length ? [plural(applications.length, 'app')] : []),
        ...(providers.length ? [plural(providers.length, 'provider')] : []),
    ];
    if (!parts.length) {
        return null;
    }
    const names = [...applications, ...providers].map(usage => usage.name);
    return (
        <Badge variant="outline" title={names.slice(0, 4).join(' / ') + (names.length > 4 ? ' / …' : '')}>
            {parts.join(', ')}
        </Badge>
    );
}

function PublicKeyDialog({ cert, onClose }: { readonly cert: Certificate | null; readonly onClose: () => void }) {
    const { ref } = useCurrentDomain();
    const { data, error } = useQuery({
        queryKey: ['certificate-keys', ref, cert?.id],
        queryFn: () => listCertificateKeys(ref, cert!.id),
        enabled: cert !== null,
    });
    const copy = (payload: string) =>
        navigator.clipboard.writeText(payload).then(
            () => toast.success('Certificate key copied to the clipboard'),
            () => toast.error('Could not copy the certificate key'),
        );
    return (
        <Dialog open={cert !== null} onOpenChange={open => !open && onClose()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Public certificate key</DialogTitle>
                    <DialogDescription>{cert?.name}</DialogDescription>
                </DialogHeader>
                {error ? (
                    <p className="text-sm text-muted-foreground">Failed to load public key</p>
                ) : !data ? (
                    <Skeleton className="h-32 w-full" />
                ) : (
                    <div className="grid gap-4">
                        {data.map(key => (
                            <div key={key.fmt} className="space-y-2">
                                <div className="flex items-center justify-between">
                                    <Label htmlFor={`certificate-key-${key.fmt}`}>{key.fmt}</Label>
                                    <Button variant="ghost" size="sm" onClick={() => copy(key.payload)}>
                                        <CopyIcon aria-hidden />
                                        Copy
                                    </Button>
                                </div>
                                <Textarea
                                    id={`certificate-key-${key.fmt}`}
                                    rows={5}
                                    readOnly
                                    value={key.payload}
                                    className="font-mono text-xs"
                                />
                            </div>
                        ))}
                    </div>
                )}
                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>
                        Close
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

/** The Angular `CertificateSettingsDialogComponent`: the domain's fallback certificate. Clear empties the selection. */
function FallbackDialog({
    certificates,
    current,
    onClose,
}: {
    readonly certificates: readonly Certificate[];
    readonly current: string | null;
    readonly onClose: () => void;
}) {
    const { domain, ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const [selected, setSelected] = useState<string | null>(current);
    const save = useMutation({
        mutationFn: (fallbackCertificate: string | null) => updateCertificateSettings(ref, { fallbackCertificate }),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['domain', ref.organizationId, ref.environmentId, domain.hrid] });
            toast.success('Certificate settings updated');
            onClose();
        },
        onError: error => toast.error(`Could not update the certificate settings. ${error.message}`),
    });

    return (
        <Dialog open onOpenChange={open => !open && onClose()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Certificate settings</DialogTitle>
                </DialogHeader>
                <Field>
                    <FieldLabel htmlFor="fallback-certificate">Fallback certificate</FieldLabel>
                    <Select value={selected ?? ''} onValueChange={setSelected}>
                        <SelectTrigger id="fallback-certificate">
                            <SelectValue placeholder="Select a fallback certificate" />
                        </SelectTrigger>
                        <SelectContent>
                            {certificates.map(cert => (
                                <SelectItem key={cert.id} value={cert.id}>
                                    {cert.name}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <FieldDescription>The fallback certificate is used if the selected certificate fails to load or sign.</FieldDescription>
                </Field>
                <DialogFooter>
                    <Button variant="ghost" onClick={onClose} disabled={save.isPending}>
                        Cancel
                    </Button>
                    <Button variant="outline" onClick={() => setSelected(null)} disabled={save.isPending}>
                        Clear
                    </Button>
                    <Button onClick={() => save.mutate(selected)} disabled={save.isPending}>
                        Confirm
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

/**
 * The Angular `DomainSettingsCertificatesComponent`: the domain's certificates with their origin, usage, expiry and
 * consumers; the public key of each; the system key rotation; and the domain's fallback certificate.
 */
export function CertificatesPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/certificates`;
    const fallback = domain.certificateSettings?.fallbackCertificate ?? null;
    const [publicKeyOf, setPublicKeyOf] = useState<Certificate | null>(null);
    const [pendingDelete, setPendingDelete] = useState<Certificate | null>(null);
    const [confirmRotate, setConfirmRotate] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const { data, error, isFetching } = useQuery({ queryKey: ['certificates', ref], queryFn: () => listCertificates(ref) });
    const reload = () => queryClient.invalidateQueries({ queryKey: ['certificates', ref] });
    const remove = useMutation({
        mutationFn: (id: string) => deleteCertificate(ref, id),
        onSuccess: () => {
            setPendingDelete(null);
            void reload();
            toast.success('Certificate deleted');
        },
        onError: deleteError => toast.error(`Could not delete the certificate. ${deleteError.message}`),
    });
    const rotate = useMutation({
        mutationFn: () => rotateCertificate(ref),
        onMutate: () => setConfirmRotate(false),
        onSuccess: () => {
            toast.success('New system certificate created');
            void reload();
            // The plugin reads the expiry date while it generates the certificate, so Angular reloads again after 5 s.
            setTimeout(() => void reload(), 5000);
        },
        onError: rotateError => toast.error(`Could not rotate the system key. ${rotateError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Certificates' },
    ]);

    const columns = useMemo<ColumnDef<Certificate, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) => (
                    <span className="flex items-center gap-2">
                        {has('domain_certificate_read') ? (
                            <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                                {row.original.name}
                            </Link>
                        ) : (
                            <span className="font-medium">{row.original.name}</span>
                        )}
                        {row.original.id === fallback && (
                            <Badge variant="outline" data-testid="certificateFallbackBadge">
                                Fallback
                            </Badge>
                        )}
                    </span>
                ),
            },
            {
                accessorKey: 'type',
                header: 'Type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="outline">{certificateTypeName(row.original.type)}</Badge>,
            },
            {
                accessorKey: 'system',
                header: 'Origin',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="secondary">{row.original.system ? 'System' : 'Own'}</Badge>,
            },
            {
                id: 'usage',
                header: 'Usage',
                enableSorting: false,
                cell: ({ row }) => {
                    const usage = row.original.usage ?? [];
                    return row.original.system ? null : (
                        <Badge variant="outline" title={usage.map(value => USAGE_LABELS[value] ?? value).join(', ')}>
                            {usageLabel(usage)}
                        </Badge>
                    );
                },
            },
            {
                accessorKey: 'expiresAt',
                header: 'Expires on',
                cell: ({ row }) => <ExpiryCell cert={row.original} />,
            },
            {
                id: 'usedBy',
                header: 'Used by',
                enableSorting: false,
                cell: ({ row }) => <UsedBy cert={row.original} />,
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) => (
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" aria-label={`Actions for ${row.original.name}`}>
                                <MoreVerticalIcon />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                            <DropdownMenuItem onSelect={() => setPublicKeyOf(row.original)}>
                                <KeyRoundIcon aria-hidden />
                                Get public key
                            </DropdownMenuItem>
                            {has('domain_certificate_delete') && (
                                <>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem
                                        variant="destructive"
                                        disabled={row.original.id === fallback}
                                        title={
                                            row.original.id === fallback
                                                ? 'Cannot delete: certificate is configured as fallback'
                                                : undefined
                                        }
                                        onSelect={() => setPendingDelete(row.original)}
                                    >
                                        <Trash2Icon aria-hidden />
                                        Delete
                                    </DropdownMenuItem>
                                </>
                            )}
                        </DropdownMenuContent>
                    </DropdownMenu>
                ),
            },
        ],
        [fallback, has, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add certificate
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Certificates"
                description="Certificates sign and encrypt the tokens this domain issues, and secure mTLS client authentication."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_certificate_create']}>
                            <div className="flex shrink-0 items-center gap-2">
                                <Button variant="outline" disabled={rotate.isPending} onClick={() => setConfirmRotate(true)}>
                                    <RefreshCwIcon aria-hidden />
                                    Rotate system key
                                </Button>
                                <Button variant="outline" onClick={() => setSettingsOpen(true)}>
                                    <SettingsIcon aria-hidden />
                                    Settings
                                </Button>
                                {createButton}
                            </div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Could not load the certificates." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<KeyRoundIcon />}
                    title="No certificates yet"
                    description="Create a certificate that can be used for signature, encryption and mTLS."
                    primaryAction={has('domain_certificate_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Certificates" columns={columns} data={data ?? []} loading={isFetching} />
            )}
            <PublicKeyDialog cert={publicKeyOf} onClose={() => setPublicKeyOf(null)} />
            {settingsOpen && data && <FallbackDialog certificates={data} current={fallback} onClose={() => setSettingsOpen(false)} />}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete certificate"
                description="Are you sure you want to delete this certificate?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
            <ConfirmDialog
                open={confirmRotate}
                title="Rotate key"
                description="Are you sure you want to generate a new system certificate? This only affects the latest system certificate created."
                confirmLabel="Rotate"
                onOpenChange={setConfirmRotate}
                onConfirm={() => rotate.mutate()}
            />
        </div>
    );
}
