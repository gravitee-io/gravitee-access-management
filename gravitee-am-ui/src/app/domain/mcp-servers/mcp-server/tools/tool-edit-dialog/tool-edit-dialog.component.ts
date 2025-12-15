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
import { AbstractControl, FormBuilder, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
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
  originalTool: EditTool;
  filteredScopes: any[];
  form: FormGroup;
  scopeFieldFocused = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: EditToolDialogData,
    public dialogRef: MatDialogRef<DomainMcpServerToolEditDialogComponent, EditTool>,
    private fb: FormBuilder,
  ) {
    this.tool = {
      key: data.tool.key,
      description: data.tool.description,
      scopes: [...(data.tool.scopes || [])],
    };

    this.originalTool = {
      key: data.tool.key,
      description: data.tool.description,
      scopes: [...(data.tool.scopes || [])],
    };

    this.form = this.fb.group({
      key: [data.tool.key, [Validators.required, Validators.pattern(/^[a-zA-Z0-9_-]+$/), Validators.maxLength(64)]],
      description: [data.tool.description || ''],
      scope: [null, [this.uncommittedScopeValidator()]],
    });

    this.form.get('key').valueChanges.subscribe((value) => {
      this.tool.key = value || '';
    });

    this.form.get('description').valueChanges.subscribe((value) => {
      this.tool.description = value || '';
    });

    this.form.get('scope').valueChanges.subscribe((searchTerm: string) => {
      if (typeof searchTerm === 'string') {
        this.filteredScopes = data.scopes.filter((scope) => {
          return scope.key.includes(searchTerm) && this.tool.scopes.indexOf(scope.key) === -1;
        });
      }
    });

    this.filteredScopes = this.loadFilteredScopes();
  }

  accept(): void {
    // Validate form before accepting
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // Check if anything has changed
    if (this.hasChanges()) {
      this.dialogRef.close(this.tool);
    } else {
      // No changes, close without returning data
      this.dialogRef.close();
    }
  }

  isFormValid(): boolean {
    return this.form.valid;
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

  private uncommittedScopeValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (value !== null && value !== '' && typeof value === 'string') {
        return { uncommitted: true };
      }
      return null;
    };
  }

  private hasChanges(): boolean {
    const formValue = this.form.value;

    const currentKey = formValue.key?.trim() || '';
    const originalKey = this.originalTool.key?.trim() || '';
    const currentDescription = formValue.description?.trim() || '';
    const originalDescription = this.originalTool.description?.trim() || '';

    // Check if key or description changed
    if (currentKey !== originalKey || currentDescription !== originalDescription) {
      return true;
    }

    // Check if scopes changed
    const currentScopes = [...(this.tool.scopes || [])].sort();
    const originalScopes = [...(this.originalTool.scopes || [])].sort();

    if (currentScopes.length !== originalScopes.length) {
      return true;
    }

    for (let i = 0; i < currentScopes.length; i++) {
      if (currentScopes[i] !== originalScopes[i]) {
        return true;
      }
    }

    return false;
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

  onSelectionChanged(event: MatAutocompleteSelectedEvent): void {
    this.tool.scopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scope.setValue(null);
    this.scope.markAsTouched();
    this.scope.markAsPristine();
    this.filteredScopes = this.loadFilteredScopes();
  }

  private loadFilteredScopes(): any[] {
    return this.data.scopes.filter((domainScope) => {
      return this.tool.scopes.indexOf(domainScope.key) === -1;
    });
  }
}
