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
import { JsonSchemaFormService } from '@ajsf/core';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatCheckboxChange } from '@angular/material/checkbox';

import { MaterialCheckboxHintComponent } from './material-checkbox-hint.component';

describe('MaterialCheckboxHintComponent', () => {
  let component: MaterialCheckboxHintComponent;
  let fixture: ComponentFixture<MaterialCheckboxHintComponent>;
  let jsf: { initializeControl: jest.Mock; updateValue: jest.Mock };

  beforeEach(async () => {
    jsf = {
      initializeControl: jest.fn(),
      updateValue: jest.fn(),
    };

    await TestBed.configureTestingModule({
      declarations: [MaterialCheckboxHintComponent],
      providers: [{ provide: JsonSchemaFormService, useValue: jsf }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(MaterialCheckboxHintComponent);
    component = fixture.componentInstance;
  });

  function render(options: any) {
    component.layoutNode = { options };
    fixture.detectChanges();
  }

  it('should initialize JSF control on init', () => {
    render({ title: 'Use ETag' });

    expect(jsf.initializeControl).toHaveBeenCalledWith(component);
  });

  it('should render the description as a hint', () => {
    render({ title: 'Use ETag', description: 'Keep a copy in the HTTP cache' });

    const hint = fixture.nativeElement.querySelector('.gv-form-hint');
    expect(hint.textContent.trim()).toBe('Keep a copy in the HTTP cache');
  });

  it('should not render a hint without description', () => {
    render({ title: 'Use ETag' });

    expect(fixture.nativeElement.querySelector('.gv-form-hint')).toBeNull();
  });

  it('should update the JSF value when the checkbox changes', () => {
    render({ title: 'Use ETag' });

    component.updateValue({ checked: true } as MatCheckboxChange);

    expect(component.controlValue).toBe(true);
    expect(jsf.updateValue).toHaveBeenCalledWith(component, true);
  });
});
