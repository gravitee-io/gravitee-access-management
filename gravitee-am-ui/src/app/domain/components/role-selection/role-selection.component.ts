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
import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { MatAutocompleteSelectedEvent, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { BehaviorSubject, Subscription } from 'rxjs';
import { UntypedFormControl } from '@angular/forms';
import { map } from 'lodash';

@Component({
  selector: 'role-selection',
  templateUrl: './role-selection.component.html',
  styleUrls: ['./role-selection.component.scss'],
  standalone: false,
})
export class RoleSelectionComponent implements OnInit, OnDestroy {
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
  @Output() onRoleSelection = new EventEmitter();
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('roles') domainRoles: any[] = [];
  @Input() initialSelectedRoles: any[];
  @ViewChild('roleInput', { static: true }) roleInput: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger, { static: true }) trigger;
  filteredRoles: BehaviorSubject<any[]> = new BehaviorSubject<any[]>([]);
  assignedRoles: string[] = [];
  removable = true;
  addOnBlur = true;
  separatorKeysCodes: number[] = [ENTER, COMMA];
  roleCtrl = new UntypedFormControl();
  userInputSub: Subscription;

  ngOnDestroy(): void {
    this.userInputSub?.unsubscribe();
  }

  ngOnInit(): void {
    this.assignedRoles = this.initialSelectedRoles ? map(this.initialSelectedRoles, 'id') : [];
    this.userInputSub = this.roleCtrl.valueChanges.subscribe((value) => {
      this._filterByUserInput(value);
    });
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    this.assignedRoles.push(event.option.value);
    this.roleInput.nativeElement.value = '';
    this.roleCtrl.setValue(null);
    this.onRoleSelection.emit(this.assignedRoles);
    this._filter();
  }

  remove(role: string): void {
    const index = this.assignedRoles.indexOf(role);

    if (index >= 0) {
      this.assignedRoles.splice(index, 1);
    }
    this.onRoleSelection.emit(this.assignedRoles);
    this._filter();
  }

  onFocus() {
    this.trigger._onChange('');
    this.trigger.openPanel();
  }

  private _filter(): void {
    this.filteredRoles.next(this.domainRoles.filter((domainRole) => !this.assignedRoles.includes(domainRole.id)));
    this.initialSelectedRoles = this.domainRoles.filter((selectedRole) => this.assignedRoles.includes(selectedRole.id));
  }

  private _filterByUserInput(value: any) {
    const normalizedValue = value?.toLowerCase().trim();
    const rolesNotAssignedYet = this.domainRoles.filter((domainRole) => !this.assignedRoles.includes(domainRole.id));
    const filteredRolesFromUserInput = normalizedValue
      ? rolesNotAssignedYet.filter((role) => role.name.toLowerCase().includes(normalizedValue))
      : rolesNotAssignedYet;
    this.filteredRoles.next(filteredRolesFromUserInput);
  }
}
