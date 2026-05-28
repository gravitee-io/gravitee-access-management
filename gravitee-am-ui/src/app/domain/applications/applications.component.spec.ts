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
import { NO_ERRORS_SCHEMA, Pipe, PipeTransform } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject } from 'rxjs';

import { ApplicationService } from '../../services/application.service';

import { ApplicationsComponent } from './applications.component';

@Pipe({
  name: 'humanDate',
  standalone: false,
})
class HumanDatePipeStub implements PipeTransform {
  transform(value: unknown): unknown {
    return value;
  }
}

describe('ApplicationsComponent', () => {
  let component: ApplicationsComponent;
  let fixture: ComponentFixture<ApplicationsComponent>;
  let applicationService: {
    cursorSearch: jest.Mock;
    cursorNext: jest.Mock;
  };

  beforeEach(async () => {
    applicationService = {
      cursorSearch: jest.fn().mockReturnValue(of({ totalCount: 0, data: [], nextCursor: undefined })),
      cursorNext: jest.fn().mockReturnValue(of({ totalCount: 0, data: [], nextCursor: undefined })),
    };

    const activatedRouteStub = {
      snapshot: { data: { domain: { id: 'domain-id' } } },
    } as unknown as ActivatedRoute;

    TestBed.configureTestingModule({
      declarations: [ApplicationsComponent, HumanDatePipeStub],
      providers: [
        { provide: ApplicationService, useValue: applicationService },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ApplicationsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture?.destroy();
  });

  it('should cancel the previous request when a new search starts', fakeAsync(() => {
    const initialRequest = new Subject<any>();
    const searchRequest = new Subject<any>();

    applicationService.cursorSearch.mockReturnValueOnce(initialRequest.asObservable()).mockReturnValueOnce(searchRequest.asObservable());

    fixture.detectChanges();

    component.onSearch({ target: { value: 'new-app' } });
    tick(400);

    searchRequest.next({ totalCount: 1, data: [{ id: 'new-id', name: 'new-app' }], nextCursor: 'new-cursor' });
    fixture.detectChanges();

    expect(component.applications).toEqual([{ id: 'new-id', name: 'new-app' }]);

    initialRequest.next({ totalCount: 1, data: [{ id: 'old-id', name: 'old-app' }], nextCursor: 'old-cursor' });
    fixture.detectChanges();

    expect(component.applications).toEqual([{ id: 'new-id', name: 'new-app' }]);

    initialRequest.complete();
    searchRequest.complete();
  }));

  it('should show an overlay spinner while the table request is pending', () => {
    const pendingRequest = new Subject<any>();
    applicationService.cursorSearch.mockReturnValueOnce(pendingRequest.asObservable());

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.applications-table-overlay')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[matInput]').disabled).toBe(true);

    pendingRequest.next({ totalCount: 1, data: [{ id: 'app-id', name: 'app-name' }], nextCursor: undefined, page: 0 });
    pendingRequest.complete();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.applications-table-overlay')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[matInput]').disabled).toBe(false);
  });
});
