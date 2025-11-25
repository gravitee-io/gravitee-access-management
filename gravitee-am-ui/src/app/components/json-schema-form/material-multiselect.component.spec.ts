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
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { JsonSchemaFormService } from '@ajsf/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatSelectChange } from '@angular/material/select';

import { AppConfig } from '../../../config/app.config';

import { MaterialMultiselectComponent } from './material-multiselect.component';

describe('MaterialMultiselectComponent', () => {
  let component: MaterialMultiselectComponent;
  let fixture: ComponentFixture<MaterialMultiselectComponent>;
  let httpMock: HttpTestingController;

  let jsf: {
    data: any;
    initializeControl: jest.Mock;
    updateArrayCheckboxList: jest.Mock;
  };

  beforeEach(async () => {
    jsf = {
      data: {},
      initializeControl: jest.fn(),
      updateArrayCheckboxList: jest.fn(),
    };

    Object.defineProperty(AppConfig, 'settings', {
      value: { baseURL: 'http://api.test' },
      writable: false,
    });

    await TestBed.configureTestingModule({
      declarations: [MaterialMultiselectComponent],
      imports: [HttpClientTestingModule],
      providers: [{ provide: JsonSchemaFormService, useValue: jsf }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MaterialMultiselectComponent);
    component = fixture.componentInstance;

    httpMock = TestBed.inject(HttpTestingController);

    component.layoutNode = { options: {} };
  });

  function setOptions(options: any) {
    component.layoutNode = { options };
    component.controlName = 'testControl';
  }

  it('should initialize JSF control on init', () => {
    setOptions({ enum: [] });
    jsf.data['testControl'] = [];

    component.ngOnInit();

    expect(jsf.initializeControl).toHaveBeenCalledWith(component);
  });

  it('should populate selected from jsf.data on init', () => {
    setOptions({ enum: [] });
    jsf.data['testControl'] = ['A', 'B'];

    component.ngOnInit();

    expect(component.selected).toEqual(['A', 'B']);
  });

  it('should load items from enum when no dictionary endpoint is provided', () => {
    setOptions({ enum: ['A', 'B', 'C'] });

    component.ngOnInit();

    expect(component.allItems).toEqual(['A', 'B', 'C']);
  });

  it('should load items from dictionary endpoint if provided', fakeAsync(() => {
    setOptions({ itemsDictionaryEndpoint: '/dict' });

    component.ngOnInit();

    const req = httpMock.expectOne('http://api.test/dict');
    expect(req.request.method).toBe('GET');

    req.flush(['X', 'Y']);
    tick();

    expect(component.allItems).toEqual(['X', 'Y']);
  }));

  it('should call jsf.updateArrayCheckboxList when selected changes', () => {
    component.selected = ['A'];

    expect(jsf.updateArrayCheckboxList).toHaveBeenCalledWith(component, [
      {
        name: 'A',
        value: 'A',
        checked: true,
      },
    ]);
  });

  it('should remove item on onUnselect()', () => {
    component.selected = ['A', 'B'];

    component.onUnselect('A');

    expect(component.selected).toEqual(['B']);
  });

  it('should update selected from MatSelectChange', () => {
    const event = {
      value: ['X', 'Y'],
    } as MatSelectChange<string[]>;

    component.onSelectionChange(event);

    expect(component.selected).toEqual(['X', 'Y']);
  });

  it('should update searchText on onSearch()', () => {
    const inputEvent = {
      target: { value: 'abc' },
    } as any;

    component.onSearch(inputEvent);

    expect(component.searchText).toBe('abc');
  });

  it('should update visibleItems based on searchText', () => {
    component.allItems = ['Apple', 'Banana', 'Cherry'];
    component.searchText = 'ap';

    (component as any).refreshOptions();

    expect(component.visibleItems).toEqual(['Apple']);
    expect(component.selectAllLabel).toBe('Select visible');
  });

  it('should show all items and label "Select all" when searchText is empty', () => {
    component.allItems = ['A', 'B', 'C'];
    component.searchText = '';

    (component as any).refreshOptions();

    expect(component.visibleItems).toEqual(['A', 'B', 'C']);
    expect(component.selectAllLabel).toBe('Select all');
  });

  it('should set selectAllState to unchecked when no visible items are selected', () => {
    component.allItems = ['A', 'B', 'C'];
    component.selected = [];
    component.searchText = '';

    (component as any).refreshOptions();

    expect(component.selectAllState).toBe('unchecked');
  });

  it('should set selectAllState to checked when all visible items are selected', () => {
    component.allItems = ['A', 'B', 'C'];
    component.selected = ['A', 'B', 'C'];
    component.searchText = '';

    (component as any).refreshOptions();

    expect(component.selectAllState).toBe('checked');
  });

  it('should set selectAllState to indeterminate when some visible items are selected', () => {
    component.allItems = ['A', 'B', 'C'];
    component.selected = ['A'];
    component.searchText = '';

    (component as any).refreshOptions();

    expect(component.selectAllState).toBe('indeterminate');
  });

  it('should select all visible items when none selected', () => {
    component.allItems = ['A', 'B', 'C'];
    component.selected = [];
    component.searchText = '';

    (component as any).refreshOptions();
    component.toggleSelectVisible();

    expect(component.selected.sort()).toEqual(['A', 'B', 'C']);
  });

  it('should deselect all visible items when all visible selected', () => {
    component.allItems = ['A', 'B', 'C'];
    component.selected = ['A', 'B', 'C'];
    component.searchText = '';

    (component as any).refreshOptions();
    component.toggleSelectVisible();

    expect(component.selected).toEqual([]);
  });

  it('should select only visible items when some visible items selected', () => {
    component.allItems = ['A', 'B', 'C', 'D'];
    component.selected = ['A', 'D'];
    component.searchText = 'A';

    (component as any).refreshOptions();
    component.toggleSelectVisible();

    expect(component.selected).toEqual(['D']);
  });
});
