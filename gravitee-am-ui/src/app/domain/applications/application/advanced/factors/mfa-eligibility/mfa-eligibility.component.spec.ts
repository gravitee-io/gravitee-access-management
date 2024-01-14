import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MfaEligibilityComponent } from './mfa-eligibility.component';

describe('MfaEligibilityComponent', () => {
  let component: MfaEligibilityComponent;
  let fixture: ComponentFixture<MfaEligibilityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MfaEligibilityComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(MfaEligibilityComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
