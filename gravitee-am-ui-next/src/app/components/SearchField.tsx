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
import { InputGroup, InputGroupAddon, InputGroupInput } from '@gravitee/graphene-core';
import { SearchIcon } from '@gravitee/graphene-core/icons';
import { useEffect, useState } from 'react';

interface SearchFieldProps {
    readonly label: string;
    readonly onSearch: (query: string) => void;
}

/** A search box for a DataTable toolbar. It calls `onSearch` 300 ms after the last key press. */
export function SearchField({ label, onSearch }: SearchFieldProps) {
    const [value, setValue] = useState('');

    useEffect(() => {
        const id = setTimeout(() => onSearch(value.trim()), 300);
        return () => clearTimeout(id);
    }, [value, onSearch]);

    return (
        <div className="w-64 max-w-full">
            <InputGroup>
                <InputGroupAddon align="inline-start">
                    <SearchIcon size={16} aria-hidden />
                </InputGroupAddon>
                <InputGroupInput
                    placeholder={`Search ${label}...`}
                    aria-label={`Search ${label}`}
                    value={value}
                    onChange={e => setValue(e.target.value)}
                />
            </InputGroup>
        </div>
    );
}
