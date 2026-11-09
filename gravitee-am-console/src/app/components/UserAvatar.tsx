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
import { Avatar, AvatarFallback, AvatarImage } from '@gravitee/graphene-core';

interface AvatarUser {
    readonly username?: string;
    readonly name?: string;
    readonly displayName?: string;
    readonly picture?: string;
    readonly additionalInformation?: { readonly picture?: string };
}

interface UserAvatarProps {
    readonly user: AvatarUser;
    readonly size?: 'default' | 'sm' | 'lg';
    /** Tints the initial with the brand color, as a default badge. */
    readonly brand?: boolean;
}

function initialsOf(text: string): string {
    const [first = '', second = ''] = text.split(/[\s.@_-]+/).filter(Boolean);
    return (first.charAt(0) + second.charAt(0)).toUpperCase();
}

export function UserAvatar({ user, size, brand = false }: UserAvatarProps) {
    const name = user.username || user.name || '';
    const picture = user.picture || user.additionalInformation?.picture;
    return (
        <Avatar size={size}>
            {picture && <AvatarImage src={picture} alt={name} />}
            <AvatarFallback
                aria-label={name}
                role="img"
                className={brand ? 'bg-primary/10 font-medium text-badge-default-foreground dark:bg-primary/20' : undefined}
            >
                {initialsOf(user.displayName || name)}
            </AvatarFallback>
        </Avatar>
    );
}
