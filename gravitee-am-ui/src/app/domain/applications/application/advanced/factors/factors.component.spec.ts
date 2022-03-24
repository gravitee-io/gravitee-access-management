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
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { ApplicationFactorsComponent } from './factors.component';

describe('ApplicationFactorsComponent', () => {
  let component: ApplicationFactorsComponent;
  let fixture: ComponentFixture<ApplicationFactorsComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
    declarations: [ApplicationFactorsComponent],
    teardown: { destroyAfterEach: false }
})
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ApplicationFactorsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
