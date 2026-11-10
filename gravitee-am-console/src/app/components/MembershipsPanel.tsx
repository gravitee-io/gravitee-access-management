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
    CardAction,
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
    Field,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon, Trash2Icon, UserIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
    listMembers,
    listMembershipRoles,
    listOrganizationGroups,
    type Member,
    type MembershipRole,
    type MembershipRoleType,
    type NewMembership,
    removeMember,
    saveMember,
    searchOrganizationUsers,
} from '../../lib/api/memberships';
import { useSession } from '../../lib/session/session';
import { ConfirmDialog } from './ConfirmDialog';
import { ErrorPanel } from './ErrorPanel';

interface MembershipsPanelProps {
    /** The members endpoint of the resource, for example `.../applications/:id/members`. */
    readonly membersPath: string;
    readonly roleType: MembershipRoleType;
    /** The resource in the copy, for example `application`. */
    readonly resourceLabel: string;
    readonly canCreate: boolean;
    readonly canUpdate: boolean;
    readonly canDelete: boolean;
}

const isPrimaryOwner = (member: Member) => member.roleName.endsWith('_PRIMARY_OWNER');
const isRoleDisabled = (role: MembershipRole) => role.system && role.name.endsWith('_PRIMARY_OWNER');
const toNewMemberType = (member: Member): NewMembership['memberType'] => (member.memberType === 'user' ? 'USER' : 'GROUP');

function RoleOptions({ roles }: { readonly roles: readonly MembershipRole[] }) {
    return (
        <SelectContent>
            {roles.map(role => (
                <SelectItem key={role.id} value={role.id} disabled={isRoleDisabled(role)}>
                    {role.name}
                </SelectItem>
            ))}
        </SelectContent>
    );
}

export function MembershipsPanel({ membersPath, roleType, resourceLabel, canCreate, canUpdate, canDelete }: MembershipsPanelProps) {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const queryKey = ['members', membersPath];
    const [adding, setAdding] = useState(false);
    const [updating, setUpdating] = useState<NewMembership | null>(null);
    const [removing, setRemoving] = useState<Member | null>(null);
    const members = useQuery({ queryKey, queryFn: () => listMembers(membersPath) });
    // The roles need organization_role_list. Without it the members still show, with read-only roles.
    const roles = useQuery({
        queryKey: ['membership-roles', user.org, roleType],
        queryFn: () => listMembershipRoles(user.org, roleType),
        staleTime: Infinity,
    });
    const save = useMutation({
        mutationFn: ({ member }: { member: NewMembership; message: string }) => saveMember(membersPath, member),
        onSuccess: (_, { message }) => {
            void queryClient.invalidateQueries({ queryKey });
            toast.success(message);
            setAdding(false);
            setUpdating(null);
        },
        onError: error => toast.error(`Could not save the member. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: (membershipId: string) => removeMember(membersPath, membershipId),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey });
            toast.success('Member deleted');
            setRemoving(null);
        },
        onError: error => toast.error(`Could not remove the member. ${error.message}`),
    });
    const isEditable = (member: Member) => !isPrimaryOwner(member) && !(member.memberType === 'user' && member.memberId === user.sub);
    const rows = [...(members.data ?? [])].sort((a, b) => b.memberType.localeCompare(a.memberType));

    return (
        <Card>
            <CardHeader>
                <CardTitle>Members</CardTitle>
                <CardDescription>Console users and groups who can manage this {resourceLabel}, and their role.</CardDescription>
                {canCreate && (
                    <CardAction>
                        <Button size="sm" onClick={() => setAdding(true)}>
                            <PlusIcon aria-hidden />
                            Add member
                        </Button>
                    </CardAction>
                )}
            </CardHeader>
            <CardContent>
                {members.error ? (
                    <ErrorPanel description="Could not load the members." error={members.error} />
                ) : !members.data || roles.isPending ? (
                    <Skeleton className="h-32 w-full" />
                ) : rows.length === 0 ? (
                    <p className="py-6 text-center text-sm text-muted-foreground">No members yet.</p>
                ) : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Member</TableHead>
                                <TableHead>Type</TableHead>
                                <TableHead>Role</TableHead>
                                <TableHead>
                                    <span className="sr-only">Actions</span>
                                </TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {rows.map(member => (
                                <TableRow key={member.id}>
                                    <TableCell className="font-medium">{member.name}</TableCell>
                                    <TableCell>
                                        <Badge variant="outline">
                                            {member.memberType === 'user' ? <UserIcon aria-hidden /> : <UsersIcon aria-hidden />}
                                            {member.memberType === 'user' ? 'User' : 'Group'}
                                        </Badge>
                                    </TableCell>
                                    <TableCell>
                                        {isEditable(member) && roles.data ? (
                                            <Select
                                                value={member.roleId}
                                                disabled={!canUpdate}
                                                onValueChange={role =>
                                                    setUpdating({ memberId: member.memberId, memberType: toNewMemberType(member), role })
                                                }
                                            >
                                                <SelectTrigger className="h-8 w-64" aria-label={`Role of ${member.name}`}>
                                                    <SelectValue />
                                                </SelectTrigger>
                                                <RoleOptions roles={roles.data} />
                                            </Select>
                                        ) : (
                                            <span className="font-mono text-sm">{member.roleName}</span>
                                        )}
                                    </TableCell>
                                    <TableCell className="text-right">
                                        {canDelete && isEditable(member) && (
                                            <Button
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Remove ${member.name}`}
                                                onClick={() => setRemoving(member)}
                                            >
                                                <Trash2Icon aria-hidden />
                                            </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
            </CardContent>
            {adding && (
                <AddMemberDialog
                    roles={roles.data ?? []}
                    existing={new Set(rows.map(member => member.memberId))}
                    saving={save.isPending}
                    onOpenChange={setAdding}
                    onAdd={member => save.mutate({ member, message: 'Member added' })}
                />
            )}
            <ConfirmDialog
                open={updating !== null}
                title="Update member"
                description="Are you sure you want to change this membership?"
                confirmLabel="Update"
                pending={save.isPending}
                onOpenChange={open => !open && setUpdating(null)}
                onConfirm={() => updating && save.mutate({ member: updating, message: 'Member updated' })}
            />
            <ConfirmDialog
                open={removing !== null}
                title="Remove member"
                description={`They will no longer be able to manage this ${resourceLabel} through this membership.`}
                confirmLabel="Remove"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setRemoving(null)}
                onConfirm={() => removing && remove.mutate(removing.id)}
            />
        </Card>
    );
}

interface AddMemberDialogProps {
    readonly roles: readonly MembershipRole[];
    /** The member ids already listed. The user search and the group list leave them out. */
    readonly existing: ReadonlySet<string>;
    readonly saving: boolean;
    readonly onOpenChange: (open: boolean) => void;
    readonly onAdd: (member: NewMembership) => void;
}

function AddMemberDialog({ roles, existing, saving, onOpenChange, onAdd }: AddMemberDialogProps) {
    const { user } = useSession();
    const [type, setType] = useState<NewMembership['memberType']>('USER');
    const [search, setSearch] = useState('');
    const [member, setMember] = useState<{ id: string; label: string } | null>(null);
    const [role, setRole] = useState('');
    const text = search.trim();
    const users = useQuery({
        queryKey: ['organization-users', user.org, text],
        queryFn: () => searchOrganizationUsers(user.org, text),
        enabled: type === 'USER' && !member && text.length > 0,
    });
    const groups = useQuery({
        queryKey: ['organization-groups', user.org],
        queryFn: () => listOrganizationGroups(user.org),
        enabled: type === 'GROUP',
    });
    const foundUsers = (users.data ?? []).filter(found => !existing.has(found.id));
    const freeGroups = (groups.data ?? []).filter(group => !existing.has(group.id));

    return (
        <Dialog open onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Add member</DialogTitle>
                    <DialogDescription>Search console users and groups, then choose their role.</DialogDescription>
                </DialogHeader>
                <div className="grid gap-4">
                    <Field>
                        <FieldLabel htmlFor="member-type">Type</FieldLabel>
                        <Select
                            value={type}
                            onValueChange={value => {
                                setType(value === 'GROUP' ? 'GROUP' : 'USER');
                                setMember(null);
                                setSearch('');
                            }}
                        >
                            <SelectTrigger id="member-type">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="USER">User</SelectItem>
                                <SelectItem value="GROUP">Group</SelectItem>
                            </SelectContent>
                        </Select>
                    </Field>
                    {type === 'USER' ? (
                        <Field>
                            <FieldLabel htmlFor="member-user">User</FieldLabel>
                            <Input
                                id="member-user"
                                placeholder="Enter username"
                                value={member ? member.label : search}
                                onChange={event => {
                                    setMember(null);
                                    setSearch(event.target.value);
                                }}
                            />
                            {!member && text && users.data && (
                                <ul aria-label="Matching users" className="rounded-md border border-border">
                                    {foundUsers.length === 0 ? (
                                        <li className="px-3 py-2 text-sm text-muted-foreground">No matching users.</li>
                                    ) : (
                                        foundUsers.map(found => (
                                            <li key={found.id} className="border-b border-border last:border-b-0">
                                                <button
                                                    type="button"
                                                    className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-muted/50"
                                                    onClick={() => setMember({ id: found.id, label: found.username })}
                                                >
                                                    <span className="font-medium">{found.displayName ?? found.username}</span>
                                                    <span className="text-xs text-muted-foreground">
                                                        {found.username}
                                                        {found.source && ` · ${found.source}`}
                                                    </span>
                                                </button>
                                            </li>
                                        ))
                                    )}
                                </ul>
                            )}
                        </Field>
                    ) : (
                        <Field>
                            <FieldLabel htmlFor="member-group">Group</FieldLabel>
                            <Select
                                value={member?.id ?? ''}
                                onValueChange={id => setMember({ id, label: freeGroups.find(group => group.id === id)?.name ?? id })}
                            >
                                <SelectTrigger id="member-group">
                                    <SelectValue placeholder="Select a group" />
                                </SelectTrigger>
                                <SelectContent>
                                    {freeGroups.map(group => (
                                        <SelectItem key={group.id} value={group.id}>
                                            {group.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </Field>
                    )}
                    <Field>
                        <FieldLabel htmlFor="member-role">Role</FieldLabel>
                        <Select value={role} onValueChange={setRole}>
                            <SelectTrigger id="member-role">
                                <SelectValue placeholder="Select a role" />
                            </SelectTrigger>
                            <RoleOptions roles={roles} />
                        </Select>
                    </Field>
                </div>
                <DialogFooter>
                    <Button variant="ghost" onClick={() => onOpenChange(false)}>
                        Cancel
                    </Button>
                    <Button
                        disabled={!member || !role || saving}
                        onClick={() => member && onAdd({ memberId: member.id, memberType: type, role })}
                    >
                        Add member
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
