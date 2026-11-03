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
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface AddAuthorizationDetailTypeData {
  /** The domain's types not yet enabled on the application. */
  types: any[];
  loadFailed: boolean;
  domainHasTypes: boolean;
  /** Null when the user may not create types. */
  createLink: any[] | null;
}

@Component({
  selector: 'add-authorization-detail-type',
  templateUrl: './add-authorization-detail-type.component.html',
  standalone: false,
})
export class AddAuthorizationDetailTypeComponent {
  @ViewChild('typeInput') typeInput: ElementRef<HTMLInputElement>;
  typeCtrl = new UntypedFormControl();
  filteredTypes: any[];
  selectedTypes: any[] = [];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: AddAuthorizationDetailTypeData,
    public dialogRef: MatDialogRef<AddAuthorizationDetailTypeComponent>,
  ) {
    this.filteredTypes = this.filter();
    this.typeCtrl.valueChanges.subscribe((searchTerm) => {
      if (typeof searchTerm === 'string') {
        this.filteredTypes = this.filter(searchTerm);
      }
    });
  }

  onSelectionChanged(event: MatAutocompleteSelectedEvent) {
    this.selectedTypes.push(event.option.value);
    this.typeInput.nativeElement.value = '';
    this.typeCtrl.setValue(null);
    this.filteredTypes = this.filter();
  }

  remove(type: any) {
    this.selectedTypes = this.selectedTypes.filter((t) => t !== type);
    this.filteredTypes = this.filter();
  }

  private filter(searchTerm = ''): any[] {
    const term = searchTerm.toLowerCase();
    return this.data.types.filter((t) => !this.selectedTypes.includes(t) && t.type.toLowerCase().includes(term));
  }
}
