import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TransmissionComponent } from './transmission.component';
import { BasisFetchService } from '../basis-fetch.service';

describe('TransmissionComponent', () => {
  let component: TransmissionComponent;
  let fixture: ComponentFixture<TransmissionComponent>;
  let mockBasis;

  beforeEach(async () => {
    mockBasis = jasmine.createSpyObj(['onLoad']);
    await TestBed.configureTestingModule({
      declarations: [TransmissionComponent],
      providers: [
        { provide: BasisFetchService, useValue: mockBasis },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TransmissionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
