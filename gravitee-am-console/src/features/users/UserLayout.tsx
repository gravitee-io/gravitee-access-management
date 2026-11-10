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
    PasswordInput,
    Skeleton,
    Tabs,
    TabsList,
    TabsTrigger,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon, ChevronDownIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { UserAvatar } from '../../app/components/UserAvatar';
import { isNotFound } from '../../lib/api/http';
import { getActivePasswordPolicy } from '../../lib/api/password-policies';
import {
    deleteUser,
    getUser,
    resetUserPassword,
    sendRegistrationConfirmation,
    unlockUser,
    updateUserStatus,
    type User,
} from '../../lib/api/users';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { PasswordRuleList, usePasswordCheck } from './PasswordPolicyCheck';
import { UserStatusBadge } from './UserStatusBadge';
import { isAccountLocked, isPendingRegistration } from './user-helpers';

const TABS = [
    { key: 'profile', label: 'Profile' },
    { key: 'history', label: 'History' },
];

type Action = 'enable' | 'disable' | 'unlock' | 'resend' | 'delete';

const CONFIRM: Record<Action, { title: string; description: string; label: string; destructive?: boolean }> = {
    enable: { title: 'Enable user', description: 'The user will be able to sign in to their applications again.', label: 'Enable' },
    disable: { title: 'Disable user', description: 'The user will no longer be able to sign in to their applications.', label: 'Disable' },
    unlock: {
        title: 'Unlock user',
        description: 'The account is locked due to too many failed login attempts. Unlock the user to regain access.',
        label: 'Unlock',
    },
    resend: {
        title: 'Send confirmation email',
        description: 'An email will be sent to the user asking to complete their account.',
        label: 'Send email',
    },
    delete: {
        title: 'Delete user',
        description: 'The user will be deleted and will no longer be able to sign in to applications. This operation cannot be undone.',
        label: 'Delete',
        destructive: true,
    },
};

export interface UserContext {
    readonly user: User;
    readonly userPath: string;
}

export function useUser(): UserContext {
    return useOutletContext<UserContext>();
}

export function userQueryKey(ref: object, userId: string) {
    return ['user', ref, userId];
}

export function UserLayout() {
    const { userId = '' } = useParams();
    const { pathname } = useLocation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const canEdit = has('domain_user_update');
    const canDelete = has('domain_user_delete');
    const listPath = `${basePath}/settings/users`;
    const userPath = `${listPath}/${userId}`;
    const queryKey = userQueryKey(ref, userId);
    const { data: user, error } = useQuery({ queryKey, queryFn: () => getUser(ref, userId) });
    const [pending, setPending] = useState<Action | null>(null);
    const [resetOpen, setResetOpen] = useState(false);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Users', to: listPath },
        { label: user?.username ?? '…' },
    ]);

    // A write answers with the stored user, without the application and identity provider names that GET adds.
    const refetchUser = () => queryClient.invalidateQueries({ queryKey });
    const run = useMutation({
        mutationFn: async (action: Action): Promise<void> => {
            if (action === 'enable' || action === 'disable') {
                await updateUserStatus(ref, userId, action === 'enable');
            } else if (action === 'unlock') {
                await unlockUser(ref, userId);
            } else if (action === 'resend') {
                await sendRegistrationConfirmation(ref, userId);
            } else {
                await deleteUser(ref, userId);
                return;
            }
            await refetchUser();
        },
        onSuccess: (_, action) => {
            setPending(null);
            void queryClient.invalidateQueries({ queryKey: ['users', ref] });
            if (action === 'delete') {
                toast.success(`User ${user?.username} deleted`);
                navigate(listPath);
                return;
            }
            toast.success({ enable: 'User enabled', disable: 'User disabled', unlock: 'User unlocked', resend: 'Email sent' }[action]);
        },
        onError: (failure, action) => {
            setPending(null);
            toast.error(`Could not ${CONFIRM[action].title.toLowerCase()}. ${failure.message}`);
        },
    });

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="User not found"
                description="This user does not exist or has been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to users</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the user." error={error} />
        );
    }
    if (!user) {
        return <Skeleton className="h-64 w-full" />;
    }

    const active = pathname.slice(userPath.length + 1).split('/')[0] || TABS[0].key;
    const actions: { key: Action | 'reset'; label: string }[] = canEdit
        ? [
              ...(user.internal && !user.serviceAccount ? [{ key: 'reset' as const, label: 'Reset password' }] : []),
              ...(isAccountLocked(user) ? [{ key: 'unlock' as const, label: 'Unlock account' }] : []),
              ...(user.internal && isPendingRegistration(user) ? [{ key: 'resend' as const, label: 'Send confirmation email' }] : []),
              user.enabled ? { key: 'disable' as const, label: 'Disable user' } : { key: 'enable' as const, label: 'Enable user' },
          ]
        : [];
    const context: UserContext = { user, userPath };

    return (
        <div className="flex flex-col gap-6">
            <div className="space-y-3">
                <Button asChild variant="ghost" size="sm" className="-ml-2">
                    <Link to={listPath}>
                        <ArrowLeftIcon aria-hidden />
                        Users
                    </Link>
                </Button>
                <header className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex min-w-0 items-center gap-4">
                        <UserAvatar user={user} size="lg" brand />
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                                <h1 className="truncate">{user.displayName}</h1>
                                <UserStatusBadge user={user} />
                            </div>
                            <div className="flex flex-wrap items-center gap-2">
                                <p className="truncate text-sm text-muted-foreground">{user.username}</p>
                                <Badge variant="highlight">{user.internal ? 'Internal' : 'External'}</Badge>
                                {isPendingRegistration(user) && <Badge variant="highlight">Pre-registered</Badge>}
                            </div>
                        </div>
                    </div>
                    {(actions.length > 0 || canDelete) && (
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button variant="outline" size="sm">
                                    Actions
                                    <ChevronDownIcon aria-hidden />
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                {actions.map(action => (
                                    <DropdownMenuItem
                                        key={action.key}
                                        onSelect={() => (action.key === 'reset' ? setResetOpen(true) : setPending(action.key))}
                                    >
                                        {action.label}
                                    </DropdownMenuItem>
                                ))}
                                {canDelete && (
                                    <>
                                        {actions.length > 0 && <DropdownMenuSeparator />}
                                        <DropdownMenuItem variant="destructive" onSelect={() => setPending('delete')}>
                                            Delete user
                                        </DropdownMenuItem>
                                    </>
                                )}
                            </DropdownMenuContent>
                        </DropdownMenu>
                    )}
                </header>
            </div>
            <Tabs value={active} onValueChange={value => navigate(`${userPath}/${value}`)}>
                <TabsList variant="line">
                    {TABS.map(tab => (
                        <TabsTrigger key={tab.key} value={tab.key}>
                            {tab.label}
                        </TabsTrigger>
                    ))}
                </TabsList>
            </Tabs>
            {/* A new user remounts the tab, so no edit state carries over from the previous one. */}
            <Fragment key={user.id}>
                <Outlet context={context} />
            </Fragment>

            <ConfirmDialog
                open={pending !== null}
                title={pending ? CONFIRM[pending].title : ''}
                description={pending ? CONFIRM[pending].description : ''}
                confirmLabel={pending ? CONFIRM[pending].label : ''}
                destructive={pending ? CONFIRM[pending].destructive : false}
                pending={run.isPending}
                onOpenChange={open => !open && setPending(null)}
                onConfirm={() => pending && run.mutate(pending)}
            />
            {resetOpen && <ResetPasswordDialog user={user} onClose={() => setResetOpen(false)} />}
        </div>
    );
}

function ResetPasswordDialog({ user, onClose }: { readonly user: User; readonly onClose: () => void }) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const [password, setPassword] = useState('');
    const policy = useQuery({
        queryKey: ['password-policy', ref, user.sourceId],
        queryFn: () => getActivePasswordPolicy(ref, user.sourceId),
    });
    const check = usePasswordCheck(policy.isError ? null : policy.data, password, user);
    const reset = useMutation({
        mutationFn: () => resetUserPassword(ref, user.id, password),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: userQueryKey(ref, user.id) });
            toast.success('Password reset');
            onClose();
        },
        onError: error => toast.error(`Could not reset the password. ${error.message}`),
    });

    return (
        <Dialog open onOpenChange={open => !open && !reset.isPending && onClose()}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Reset password</DialogTitle>
                    <DialogDescription>Set a new password for {user.displayName}.</DialogDescription>
                </DialogHeader>
                <Field>
                    <FieldLabel htmlFor="user-reset-password">New password</FieldLabel>
                    <PasswordInput
                        id="user-reset-password"
                        value={password}
                        autoComplete="new-password"
                        onChange={event => setPassword(event.target.value)}
                    />
                    <FieldDescription>Must satisfy the password policy of the user&apos;s identity provider.</FieldDescription>
                    <PasswordRuleList results={check.results} />
                </Field>
                <DialogFooter>
                    <Button variant="ghost" disabled={reset.isPending} onClick={onClose}>
                        Cancel
                    </Button>
                    <Button disabled={!password || reset.isPending} onClick={() => reset.mutate()}>
                        {reset.isPending ? 'Resetting…' : 'Reset password'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
