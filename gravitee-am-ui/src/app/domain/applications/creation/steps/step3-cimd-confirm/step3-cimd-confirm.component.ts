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
import { Component, Input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { CimdMetadataDialogComponent, CimdMetadataDialogData } from './cimd-metadata-dialog.component';

interface JwkDescriptor {
  kid?: string;
  kty?: string;
  use?: string;
  alg?: string;
}

@Component({
  selector: 'application-creation-step3-cimd-confirm',
  templateUrl: './step3-cimd-confirm.component.html',
  styleUrls: ['./step3-cimd-confirm.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep3CimdConfirmComponent {
  @Input() application: any;

  constructor(private readonly dialog: MatDialog) {}

  get preview(): any {
    return this.application?.cimdPreview ?? {};
  }

  get meta(): any {
    return this.preview?.metadata ?? {};
  }

  get missing(): any {
    return this.preview?.missing ?? {};
  }

  get scopes(): string[] {
    const scope = this.meta?.scope;
    if (!scope || typeof scope !== 'string') {
      return [];
    }
    return scope.split(/\s+/).filter(Boolean);
  }

  get inlineJwks(): JwkDescriptor[] {
    const keys = this.meta?.jwks?.keys;
    if (!Array.isArray(keys)) {
      return [];
    }
    return keys.filter((k) => k && typeof k === 'object').map((k) => ({ kid: k.kid, kty: k.kty, use: k.use, alg: k.alg }));
  }

  hasMetadata(): boolean {
    return !!this.preview?.metadata && Object.keys(this.preview.metadata).length > 0;
  }

  openMetadataDialog(): void {
    if (!this.hasMetadata()) {
      return;
    }
    let json: string;
    try {
      json = JSON.stringify(this.preview.metadata, null, 2);
    } catch (_) {
      return;
    }
    this.dialog.open<CimdMetadataDialogComponent, CimdMetadataDialogData>(CimdMetadataDialogComponent, {
      data: { url: this.preview?.url, json },
      maxWidth: '900px',
      width: '90vw',
      autoFocus: 'first-tabbable',
    });
  }
}
