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
import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

import { AgentJwk } from '../../../../../../services/application-agent-keys.service';

@Component({
  selector: 'app-agent-key-add-dialog',
  templateUrl: './agent-key-add-dialog.component.html',
  standalone: false,
})
export class AgentKeyAddDialogComponent {
  jwkText = '';
  errorMessage: string | null = null;

  constructor(private dialogRef: MatDialogRef<AgentKeyAddDialogComponent>) {}

  submit(): void {
    this.errorMessage = null;
    let parsed: AgentJwk;
    try {
      parsed = JSON.parse(this.jwkText);
    } catch {
      this.errorMessage = 'Invalid JSON';
      return;
    }
    if (!parsed || typeof parsed !== 'object') {
      this.errorMessage = 'JWK must be a JSON object';
      return;
    }
    if (!parsed.kid) {
      this.errorMessage = "JWK must include a 'kid' field";
      return;
    }
    this.dialogRef.close(parsed);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
