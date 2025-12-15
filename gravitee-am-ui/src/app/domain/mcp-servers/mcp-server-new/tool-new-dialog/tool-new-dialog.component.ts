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
import { AbstractControl, ValidationErrors, ValidatorFn, Validators, FormGroup, FormBuilder, FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
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

  newTool: NewTool = { scopes: [] };
  filteredScopes: any[];
  form: FormGroup;
  isEditMode: boolean;
  scopeFieldFocused = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
    public dialogRef: MatDialogRef<DomainNewMcpServerToolDialogComponent, NewTool>,
    private fb: FormBuilder,
  ) {
    this.isEditMode = !!data.tool;

    if (data.tool) {
      this.newTool = {
        name: data.tool.key,
        description: data.tool.description,
        scopes: [...(data.tool.scopes || [])],
      };
    }

    this.form = this.fb.group({
      toolName: [this.newTool.name || '', [Validators.required, Validators.pattern(/^[a-zA-Z0-9_-]+$/), Validators.maxLength(64)]],
      toolDescription: [this.newTool.description || ''],
      scope: [null, [this.uncommittedScopeValidator()]],
    });

    this.form.get('toolName')!.valueChanges.subscribe((value) => (this.newTool.name = value || ''));
    this.form.get('toolDescription')!.valueChanges.subscribe((value) => (this.newTool.description = value || ''));

    this.form.get('scope')!.valueChanges.subscribe((searchTerm: string) => {
      if (typeof searchTerm === 'string') {
        this.filteredScopes = data.scopes.filter((scope) => scope.key.includes(searchTerm) && !this.newTool.scopes!.includes(scope.key));
      }
    });

    this.filteredScopes = this.loadFilteredScopes();
  }

  get scope(): FormControl {
    return this.form.get('scope') as FormControl;
  }

  get showUncommittedScopeWarning(): boolean {
    return !!(this.scope?.hasError('uncommitted') && this.scope.touched && !this.scopeFieldFocused);
  }

  onScopeFocus(): void {
    this.scopeFieldFocused = true;
  }

  onScopeBlur(): void {
    this.scopeFieldFocused = false;
    this.scope.markAsTouched();
  }

  accept(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.newTool);
  }

  isFormValid(): boolean {
    return this.form.valid;
  }

  close(): void {
    this.dialogRef.close();
  }

  remove(scope: string): void {
    const index = this.newTool.scopes!.indexOf(scope);
    if (index !== -1) {
      this.newTool.scopes!.splice(index, 1);
    }
  }

  onSelectionChanged(event: MatAutocompleteSelectedEvent): void {
    this.newTool.scopes!.push(event.option.value);

    this.scopeInput.nativeElement.value = '';
    this.scope.setValue(null);
    this.scope.markAsTouched();
    this.scope.markAsPristine();

    this.filteredScopes = this.loadFilteredScopes();
  }

  private uncommittedScopeValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (value !== null && value !== '' && typeof value === 'string') {
        return { uncommitted: true };
      }
      return null;
    };
  }

  private loadFilteredScopes(): any[] {
    return this.data.scopes.filter((domainScope) => !this.newTool.scopes!.includes(domainScope.key));
  }
}

@Injectable()
export class DomainNewMcpServerToolDialogFactory {
  constructor(private dialog: MatDialog) {}

  public openDialog(data: DialogData, callback: DialogCallback): void {
    const cfg = { width: '540px', data };
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
