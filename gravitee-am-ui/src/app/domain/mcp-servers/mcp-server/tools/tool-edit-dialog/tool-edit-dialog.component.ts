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
import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormControl } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';

interface EditTool {
  key: string;
  description?: string;
  scopes?: string[];
}

export interface EditToolDialogData {
  tool: EditTool;
  scopes: {
    key: string;
    name: string;
    description: string;
  }[];
}

@Component({
  templateUrl: './tool-edit-dialog.component.html',
  styleUrl: './tool-edit-dialog.component.scss',
  standalone: false,
})
export class DomainMcpServerToolEditDialogComponent {
  @ViewChild('scopeInput', { static: true }) scopeInput: ElementRef<HTMLInputElement>;
  matChipInputSeparatorKeyCodes = [ENTER, COMMA];
  tool: EditTool;
  filteredScopes: any[];
  scopeCtrl = new UntypedFormControl();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: EditToolDialogData,
    public dialogRef: MatDialogRef<DomainMcpServerToolEditDialogComponent, EditTool>,
  ) {
    // Clone the tool to avoid mutating the original
    this.tool = {
      key: data.tool.key,
      description: data.tool.description,
      scopes: [...(data.tool.scopes || [])],
    };

    this.scopeCtrl.valueChanges.subscribe((searchTerm: string) => {
      if (typeof searchTerm === 'string') {
        this.filteredScopes = data.scopes.filter((scope) => {
          return scope.key.includes(searchTerm) && this.tool.scopes.indexOf(scope.key) === -1;
        });
      }
    });
    this.filteredScopes = this.loadFilteredScopes();
  }

  accept(): void {
    this.dialogRef.close(this.tool);
  }

  close(): void {
    this.dialogRef.close();
  }

  remove(scope: string): void {
    const index = this.tool.scopes.indexOf(scope);
    if (index !== -1) {
      this.tool.scopes.splice(index, 1);
    }
  }

  onSelectionChanged(event): void {
    this.tool.scopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scopeInput.nativeElement.blur();
    this.scopeCtrl.setValue(null);
    this.filteredScopes = this.loadFilteredScopes();
  }

  private loadFilteredScopes(): any[] {
    return this.data.scopes.filter((domainScope) => {
      return this.tool.scopes.indexOf(domainScope.key) === -1;
    });
  }
}
