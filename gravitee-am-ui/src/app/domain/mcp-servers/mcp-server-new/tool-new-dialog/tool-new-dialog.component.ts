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
import { Component, ElementRef, Inject, Injectable, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormControl, FormControl, Validators } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';

interface NewTool {
  name?: string;
  description?: string;
  scopes?: string[];
}

export interface DialogResult extends NewTool {
  cancel: boolean;
}

export interface DialogData {
  scopes: {
    key: string;
    name: string;
    description: string;
  }[];
  tool?: {
    key: string;
    description?: string;
    scopes?: string[];
  };
}

export type DialogCallback = (data: DialogResult) => void;

@Component({
  templateUrl: './tool-new-dialog.component.html',
  styleUrl: './tool-new-dialog.component.scss',

  standalone: false,
})
export class DomainNewMcpServerToolDialogComponent {
  @ViewChild('scopeInput', { static: true }) scopeInput: ElementRef<HTMLInputElement>;
  matChipInputSeparatorKeyCodes = [ENTER, COMMA];
  newTool: NewTool = {
    scopes: [],
  };
  filteredScopes: any[];
  scopeCtrl = new UntypedFormControl();
  toolNameCtrl: FormControl<string>;
  toolDescriptionCtrl: FormControl<string>;
  isEditMode: boolean;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
    public dialogRef: MatDialogRef<DomainNewMcpServerToolDialogComponent, NewTool>,
  ) {
    // Pre-populate form if editing existing tool
    this.isEditMode = !!data.tool;
    if (data.tool) {
      this.newTool = {
        name: data.tool.key,
        description: data.tool.description,
        scopes: [...(data.tool.scopes || [])],
      };
    }

    // Initialize form controls with validation
    this.toolNameCtrl = new FormControl(this.newTool.name || '', [Validators.required, Validators.pattern(/^[a-zA-Z0-9_-]+$/)]);

    this.toolDescriptionCtrl = new FormControl(this.newTool.description || '');

    // Sync form controls with tool object
    this.toolNameCtrl.valueChanges.subscribe((value) => {
      this.newTool.name = value || '';
    });

    this.toolDescriptionCtrl.valueChanges.subscribe((value) => {
      this.newTool.description = value || '';
    });

    this.scopeCtrl.valueChanges.subscribe((searchTerm: string) => {
      if (typeof searchTerm === 'string') {
        this.filteredScopes = data.scopes.filter((scope) => {
          return scope.key.includes(searchTerm) && this.newTool.scopes.indexOf(scope.key) === -1;
        });
      }
    });
    this.filteredScopes = this.loadFilteredScopes();
  }

  accept(): void {
    // Validate form before accepting
    if (this.toolNameCtrl.invalid) {
      this.toolNameCtrl.markAsTouched();
      return;
    }

    this.dialogRef.close(this.newTool);
  }

  isFormValid(): boolean {
    return this.toolNameCtrl.valid;
  }

  close(): void {
    this.dialogRef.close();
  }

  remove(scope: string): void {
    const index = this.newTool.scopes.indexOf(scope);
    if (index !== -1) {
      this.newTool.scopes.splice(index, 1);
    }
  }

  onSelectionChanged(event): void {
    this.newTool.scopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scopeInput.nativeElement.blur();
    this.scopeCtrl.setValue(null);
    this.filteredScopes = this.loadFilteredScopes();
  }

  private loadFilteredScopes(): any[] {
    return this.data.scopes.filter((domainScope) => {
      return this.newTool.scopes.indexOf(domainScope.key) === -1;
    });
  }
}

@Injectable()
export class DomainNewMcpServerToolDialogFactory {
  constructor(private dialog: MatDialog) {}

  public openDialog(data: DialogData, callback: DialogCallback): void {
    const cfg = {
      width: '540px',
      data: data,
    };
    const dialogRef = this.dialog.open(DomainNewMcpServerToolDialogComponent, cfg);

    dialogRef.afterClosed().subscribe((result?: NewTool) =>
      callback({
        cancel: result?.name === undefined,
        name: result?.name,
        description: result?.description,
        scopes: result?.scopes,
      }),
    );
  }
}
