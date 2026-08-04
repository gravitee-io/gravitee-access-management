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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { FlexLayoutModule } from '@angular/flex-layout';

import { Scope, ScopeSelectionComponent } from './scope-selection.component';

describe('ScopeSelectionComponent', () => {
  let fixture: ComponentFixture<ScopeSelectionComponent>;
  let component: ScopeSelectionComponent;

  const scopes: Scope[] = [
    { id: '2', key: 'beta', name: 'Beta', description: 'Beta scope', discovery: false },
    { id: '1', key: 'alpha', name: 'Alpha', description: 'Alpha scope', discovery: false },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        FlexLayoutModule,
        MatCheckboxModule,
        MatFormFieldModule,
        MatInputModule,
        MatPaginatorModule,
        MatSortModule,
        MatTableModule,
        NoopAnimationsModule,
      ],
      declarations: [ScopeSelectionComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { scopes } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ScopeSelectionComponent);
    component = fixture.componentInstance;
    component.initialSelectedScopes = [];
    component.readonly = false;
    fixture.detectChanges();
  });

  it('should emit the selection when a row is clicked', () => {
    const selectionListener = jest.fn();
    component.onScopeSelection.subscribe(selectionListener);

    fixture.nativeElement.querySelector('tbody tr').click();

    expect(selectionListener).toHaveBeenCalledWith([expect.objectContaining({ key: 'alpha' })]);
  });

  it('should keep rows ordered by key when the selection changes', () => {
    const getDisplayedKeys = () =>
      Array.from(fixture.nativeElement.querySelectorAll('td.mat-column-key')).map((element: HTMLElement) => element.textContent.trim());

    expect(getDisplayedKeys()).toEqual(['alpha', 'beta']);

    component.applyChange(scopes[0]);
    fixture.detectChanges();

    expect(getDisplayedKeys()).toEqual(['alpha', 'beta']);
  });
});
