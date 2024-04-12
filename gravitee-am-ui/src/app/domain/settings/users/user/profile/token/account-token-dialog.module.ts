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
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';
import { ClipboardModule } from 'ngx-clipboard';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDivider } from '@angular/material/divider';
import { FlexModule } from '@angular/flex-layout';
import { GioBannerModule } from '@gravitee/ui-particles-angular';

import { AccountTokenCopyDialogComponent } from './account-token-copy-dialog.component';
import { AccountTokenCreationDialogComponent } from './account-token-creation-dialog.component';

@NgModule({
  declarations: [AccountTokenCreationDialogComponent, AccountTokenCopyDialogComponent],
  exports: [AccountTokenCreationDialogComponent, AccountTokenCopyDialogComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,

    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ClipboardModule,
    MatIconModule,
    MatTooltipModule,
    MatDivider,
    FlexModule,
    GioBannerModule,
  ],
})
export class AccountTokenDialogModule {}
