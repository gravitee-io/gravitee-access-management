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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';

import { OrganizationService } from '../../../../../../../../services/organization.service';
import { ProviderService } from '../../../../../../../../services/provider.service';
import { SnackbarService } from '../../../../../../../../services/snackbar.service';
import { applyDynamicSources, applyPasswordInputToSensitiveFields } from '../../../dynamic-sources';

@Component({
  selector: 'device-notifier-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class DeviceNotifierCreationStep2Component implements OnInit {
  @Input() deviceNotifier: any;
  @Input() domainId: string;
  @Input() configurationIsValid: boolean;
  @Output() configurationIsValidChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  formChanged = false;
  configuration: any;
  deviceNotifierSchema: any = {};

  constructor(
    private organizationService: OrganizationService,
    private providerService: ProviderService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    forkJoin({
      schema: this.organizationService.deviceNotifierSchema(this.deviceNotifier.type),
      idps: this.providerService.findByDomain(this.domainId).pipe(
        catchError((err: unknown) => {
          // An IdP fetch failure is distinct from a domain that genuinely has no IdPs: surface it and
          // log, then fall back so the rest of the form still renders (the picker becomes readonly).
          console.error('Failed to load identity providers for the device-notifier form', err);
          this.snackbarService.open('Unable to load identity providers');
          return of([]);
        }),
      ),
    }).subscribe({
      next: ({ schema, idps }) => {
        if (schema?.id) {
          const cloned = JSON.parse(JSON.stringify(schema));
          applyPasswordInputToSensitiveFields(cloned);
          applyDynamicSources(cloned, { graviteeIdentityProvider: idps });
          this.deviceNotifierSchema = cloned;
        } else {
          this.deviceNotifierSchema = schema;
        }
      },
      error: (err: unknown) => {
        // Schema load failed: without this the step would render blank with no specific feedback.
        console.error('Failed to load the device-notifier configuration schema', err);
        this.snackbarService.open('Unable to load the device notifier configuration');
      },
    });
  }

  enableDeviceNotifierCreation(configurationWrapper) {
    this.configurationIsValid = configurationWrapper.isValid;
    this.configurationIsValidChange.emit(this.configurationIsValid);
    this.deviceNotifier.configuration = configurationWrapper.configuration;
  }
}
