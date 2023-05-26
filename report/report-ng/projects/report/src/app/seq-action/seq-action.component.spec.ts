import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeqActionComponent } from './seq-action.component';
import { BasisFetchService } from '../basis-fetch.service';

describe('SeqActionComponent', () => {
  let component: SeqActionComponent;
  let fixture: ComponentFixture<SeqActionComponent>;
  let mockBfs;

  beforeEach(async () => {
    mockBfs = jasmine.createSpyObj(['onLoad', 'message']);
    await TestBed.configureTestingModule({
      declarations: [SeqActionComponent],
      providers: [
        { provide: BasisFetchService, useValue: mockBfs },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SeqActionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
