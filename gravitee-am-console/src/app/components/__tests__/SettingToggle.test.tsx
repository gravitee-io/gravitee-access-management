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
import { renderWithGraphene, switchHarness } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import { SettingToggle } from '../SettingToggle';

function setupSettingToggleHarness({ checked = false, disabled = false } = {}) {
    const onChange = vi.fn();
    renderWithGraphene(
        <SettingToggle
            id="mfa"
            label="Enable MFA"
            description="Users must enroll a factor."
            checked={checked}
            disabled={disabled}
            onChange={onChange}
        />,
    );
    return { onChange, toggle: switchHarness({ name: 'Enable MFA' }) };
}

describe('SettingToggle', () => {
    it('names the switch with the label and describes it', () => {
        setupSettingToggleHarness({ checked: true });

        expect(screen.getByRole('switch', { name: 'Enable MFA' })).toHaveAccessibleDescription('Users must enroll a factor.');
        expect(switchHarness({ name: 'Enable MFA' }).isChecked()).toBe(true);
    });

    it('reports the new value', async () => {
        const harness = setupSettingToggleHarness();

        await harness.toggle.toggle();

        expect(harness.onChange).toHaveBeenCalledWith(true);
    });

    it('disables the switch', () => {
        const harness = setupSettingToggleHarness({ disabled: true });

        expect(harness.toggle.isDisabled()).toBe(true);
    });
});
