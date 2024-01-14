import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MfaChallengeComponent } from './mfa-challenge.component';

describe('MfaChallengeComponent', () => {
  let component: MfaChallengeComponent;
  let fixture: ComponentFixture<MfaChallengeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MfaChallengeComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(MfaChallengeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
