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
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogModule, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatFormFieldModule, MatHint, MatLabel } from '@angular/material/form-field';
import { MatOption } from '@angular/material/autocomplete';
import { MatSelect } from '@angular/material/select';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon, MatIconModule } from '@angular/material/icon';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatInput, MatInputModule } from '@angular/material/input';
import { MatButton, MatButtonModule, MatIconButton } from '@angular/material/button';
import { ClipboardModule } from 'ngx-clipboard';
import { MatTooltip, MatTooltipModule } from '@angular/material/tooltip';

import { ClientSecretsManagementModule } from '../../../../../components/client-secrets-management/client-secrets-management.module';

@NgModule({
  declarations: [],
  exports: [],
  imports: [
    CommonModule,
    MatDialogActions,
    MatHint,
    MatLabel,
    MatFormField,
    MatOption,
    MatSelect,
    MatSlideToggle,
    MatIcon,
    MatDialogContent,
    ReactiveFormsModule,
    MatInput,
    MatButton,
    MatDialogTitle,
    ClipboardModule,
    MatDialogClose,
    FormsModule,
    MatIconButton,
    MatTooltip,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
    ClientSecretsManagementModule,
  ],
})
export class SecretsCertificatesModule {}
