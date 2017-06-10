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
import {async, ComponentFixture, TestBed} from "@angular/core/testing";
import {CertificateCreationStep1Component} from "./step1.component";


describe('CertificateCreationStep1Component', () => {
  let component: CertificateCreationStep1Component;
  let fixture: ComponentFixture<CertificateCreationStep1Component>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CertificateCreationStep1Component ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CertificateCreationStep1Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
