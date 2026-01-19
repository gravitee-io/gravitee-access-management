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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ClipboardModule } from 'ngx-clipboard';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';

// Helper modules (assuming available or direct imports)
// If HasPermission is a directive, we might need to import the Shared module where it is declared.
// For now, assume it works if imported into the feature module that uses this one, OR we need to import it.
// Checking directives folder... I'll check if there is a HasPermissionModule.

import { ClientSecretsManagementComponent } from './client-secrets-management.component';
import { NewClientSecretComponent } from './dialog/new-client-secret/new-client-secret.component';
import { CopyClientSecretComponent } from './dialog/copy-client-secret/copy-client-secret.component';
import { RenewClientSecretComponent } from './dialog/renew-client-secret/renew-client-secret.component';
import { DeleteClientSecretComponent } from './dialog/delete-client-secret/delete-client-secret.component';
import { ClientSecretsSettingsComponent } from './dialog/client-secrets-settings/client-secrets-settings.component';

@NgModule({
  declarations: [
    ClientSecretsManagementComponent,
    NewClientSecretComponent,
    CopyClientSecretComponent,
    RenewClientSecretComponent,
    DeleteClientSecretComponent,
    ClientSecretsSettingsComponent,
  ],
  imports: [
    CommonModule,
    RouterModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatSelectModule,
    MatSlideToggleModule,
    FormsModule,
    ReactiveFormsModule,
    ClipboardModule,
    NgxDatatableModule,
  ],
  exports: [
    ClientSecretsManagementComponent,
    NewClientSecretComponent,
    CopyClientSecretComponent,
    RenewClientSecretComponent,
    DeleteClientSecretComponent,
    ClientSecretsSettingsComponent,
  ],
})
export class ClientSecretsManagementModule {}
