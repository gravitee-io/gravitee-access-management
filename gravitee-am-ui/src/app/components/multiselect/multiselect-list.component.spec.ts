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
import { NO_ERRORS_SCHEMA, SimpleChange } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';

import { MultiselectListComponent } from './multiselect-list.component';

describe('MultiselectListComponent', () => {
  let component: MultiselectListComponent;
  let fixture: ComponentFixture<MultiselectListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MultiselectListComponent],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MultiselectListComponent);
    component = fixture.componentInstance;
  });

  function setItems(items: string[]) {
    component.items = items;
    component.ngOnChanges({ items: new SimpleChange(null, items, true) });
  }

  it('should refresh the visible items when the items arrive', () => {
    setItems(['A', 'B', 'C']);

    expect(component.visibleItems).toEqual(['A', 'B', 'C']);
  });

  it('should remove an item on onUnselect()', () => {
    component.selected = ['A', 'B'];

    component.onUnselect('A');

    expect(component.selected).toEqual(['B']);
  });

  it('should emit the new selection on onUnselect()', () => {
    const emitted: string[][] = [];
    component.selectedChange.subscribe((selected) => emitted.push(selected));
    component.selected = ['A', 'B'];

    component.onUnselect('A');

    expect(emitted).toEqual([['B']]);
  });

  it('should update selected from MatSelectChange', () => {
    component.onSelectionChange({ value: ['X', 'Y'] } as MatSelectChange<string[]>);

    expect(component.selected).toEqual(['X', 'Y']);
  });

  it('should emit the new selection on onSelectionChange()', () => {
    const emitted: string[][] = [];
    component.selectedChange.subscribe((selected) => emitted.push(selected));

    component.onSelectionChange({ value: ['X'] } as MatSelectChange<string[]>);

    expect(emitted).toEqual([['X']]);
  });

  it('should update searchText on onSearch()', () => {
    const inputEvent = {
      target: { value: 'abc' },
    } as any;

    component.onSearch(inputEvent);

    expect(component.searchText).toBe('abc');
  });

  it('should update visibleItems based on searchText', () => {
    setItems(['Apple', 'Banana', 'Cherry']);

    component.onSearch({ target: { value: 'ap' } } as any);

    expect(component.visibleItems).toEqual(['Apple']);
    expect(component.selectAllLabel).toBe('Select visible');
  });

  it('should show all items and label "Select all" when searchText is empty', () => {
    setItems(['A', 'B', 'C']);

    expect(component.visibleItems).toEqual(['A', 'B', 'C']);
    expect(component.selectAllLabel).toBe('Select all');
  });

  it('should set selectAllState to unchecked when no visible items are selected', () => {
    component.selected = [];
    setItems(['A', 'B', 'C']);

    expect(component.selectAllState).toBe('unchecked');
  });

  it('should set selectAllState to checked when all visible items are selected', () => {
    component.selected = ['A', 'B', 'C'];
    setItems(['A', 'B', 'C']);

    expect(component.selectAllState).toBe('checked');
  });

  it('should set selectAllState to indeterminate when some visible items are selected', () => {
    component.selected = ['A'];
    setItems(['A', 'B', 'C']);

    expect(component.selectAllState).toBe('indeterminate');
  });

  it('should select all visible items when none selected', () => {
    component.selected = [];
    setItems(['A', 'B', 'C']);

    component.toggleSelectVisible();

    expect([...component.selected].sort()).toEqual(['A', 'B', 'C']);
  });

  it('should deselect all visible items when all visible selected', () => {
    component.selected = ['A', 'B', 'C'];
    setItems(['A', 'B', 'C']);

    component.toggleSelectVisible();

    expect(component.selected).toEqual([]);
  });

  it('should select only visible items when some visible items selected', () => {
    component.selected = ['A', 'D'];
    setItems(['A', 'B', 'C', 'D']);
    component.onSearch({ target: { value: 'A' } } as any);

    component.toggleSelectVisible();

    expect(component.selected).toEqual(['D']);
  });

  it('should build a data-testid from the prefix', () => {
    component.testIdPrefix = 'attributeMappingEventTypes';

    expect(component.testId('Select')).toBe('attributeMappingEventTypesSelect');
  });

  it('should omit the data-testid when no prefix is given', () => {
    expect(component.testId('Select')).toBeNull();
  });
});
