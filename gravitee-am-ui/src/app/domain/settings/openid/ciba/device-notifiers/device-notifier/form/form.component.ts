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
import { Component, Input, EventEmitter, Output, OnChanges, SimpleChanges } from '@angular/core';

import { applyDynamicSources, applyPasswordInputToSensitiveFields, DynamicSourceMap } from '../../dynamic-sources';

@Component({
  selector: 'device-notifier-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
  standalone: false,
})
export class DeviceNotifierFormComponent implements OnChanges {
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('deviceNotifierConfiguration') configuration: any = {};
  @Input() deviceNotifierSchema: any;
  /** Dynamic source map — populated by the parent component on init. */
  @Input() dynamicSources: DynamicSourceMap = {};
  @Output() configurationCompleted = new EventEmitter<any>();
  displayForm = false;
  data: any = {};
  /** Pristine server-supplied schema — never mutated; always clone+transform from here. */
  private _rawSchema: any;

  ngOnChanges(changes: SimpleChanges) {
    if (changes.deviceNotifierSchema || changes.dynamicSources) {
      if (changes.deviceNotifierSchema) {
        this._rawSchema = changes.deviceNotifierSchema.currentValue;
      }
      if (this._rawSchema?.id) {
        const cloned = structuredClone(this._rawSchema);
        applyPasswordInputToSensitiveFields(cloned);
        applyDynamicSources(cloned, this.dynamicSources ?? {});
        this.deviceNotifierSchema = cloned;
        this.displayForm = true;
      }
    }

    if (changes.configuration) {
      const _deviceNotifierConfiguration = changes.configuration.currentValue;
      if (_deviceNotifierConfiguration) {
        this.data = _deviceNotifierConfiguration;
      }
    }
  }

  /**
   * Delegates to the shared util so that tests can call it via the component
   * instance (consistent with the provider-form pattern).
   */
  applyDynamicSources(schema: any, sources: DynamicSourceMap): any {
    return applyDynamicSources(schema, sources);
  }

  /**
   * Delegates to the shared util so that tests can call it via the component
   * instance (consistent with the provider-form pattern).
   */
  applyPasswordInputToSensitiveFields(schema: any): any {
    return applyPasswordInputToSensitiveFields(schema);
  }

  onChanges(deviceNotifierConfiguration) {
    this.configuration = deviceNotifierConfiguration;
  }

  isValid(isValid: boolean) {
    const configurationWrapper = { isValid: isValid, configuration: this.configuration };
    this.configurationCompleted.emit(configurationWrapper);
  }
}
