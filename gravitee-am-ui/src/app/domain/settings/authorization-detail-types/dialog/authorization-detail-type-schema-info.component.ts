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
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { schemaExample } from '../authorization-detail-type.validation';

export interface SchemaInfoDialogData {
  type?: string;
  hasSchema: boolean;
  editable: boolean;
}

@Component({
  selector: 'app-authorization-detail-type-schema-info',
  templateUrl: './authorization-detail-type-schema-info.component.html',
  standalone: false,
})
export class AuthorizationDetailTypeSchemaInfoDialogComponent {
  readonly example: string;

  constructor(
    public dialogRef: MatDialogRef<AuthorizationDetailTypeSchemaInfoDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: SchemaInfoDialogData,
  ) {
    this.example = schemaExample(data.type || 'your_type');
  }

  get unusableReason(): string | null {
    if (!this.data.type) {
      return 'Enter a type to use this example.';
    }
    return this.data.hasSchema ? 'Clear the schema to use this example.' : null;
  }
}
