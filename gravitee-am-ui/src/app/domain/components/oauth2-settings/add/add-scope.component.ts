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
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { COMMA, ENTER } from '@angular/cdk/keycodes';

@Component({
  selector: 'add-scope',
  templateUrl: './add-scope.component.html',
  standalone: false,
})
export class AddScopeComponent {
  @ViewChild('scopeInput', { static: true }) scopeInput: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger, { static: true }) trigger;
  scopeCtrl = new UntypedFormControl();
  filteredScopes: any[];
  selectedScopes: any[] = [];
  removable = true;
  addOnBlur = true;
  separatorKeysCodes: number[] = [ENTER, COMMA];
  private applicationScopes: string[] = [];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<AddScopeComponent>,
  ) {
    this.applicationScopes = data.applicationScopes || [];
    this.filteredScopes = this.loadFilteredScopes();
    this.scopeCtrl.valueChanges.subscribe((searchTerm) => {
      if (typeof searchTerm === 'string' || searchTerm instanceof String) {
        this.filteredScopes = data.domainScopes.filter((domainScope) => {
          return (
            domainScope.key.includes(searchTerm) &&
            this.selectedScopes.indexOf(domainScope.key) === -1 &&
            this.applicationScopes.indexOf(domainScope.key) === -1
          );
        });
      }
    });
  }

  onSelectionChanged(event) {
    this.selectedScopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scopeInput.nativeElement.blur();
    this.scopeCtrl.setValue(null);
    this.filteredScopes = this.loadFilteredScopes();
  }

  remove(scope: string): void {
    const index = this.selectedScopes.indexOf(scope);

    if (index >= 0) {
      this.selectedScopes.splice(index, 1);
    }
  }

  private loadFilteredScopes(): any[] {
    return this.data.domainScopes.filter((domainScope) => {
      return this.selectedScopes.indexOf(domainScope.key) === -1 && this.applicationScopes.indexOf(domainScope.key) === -1;
    });
  }
}
