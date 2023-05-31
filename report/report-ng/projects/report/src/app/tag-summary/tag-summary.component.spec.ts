import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TagSummaryComponent } from './tag-summary.component';
import { FlowFilterService } from '../flow-filter.service';

describe('TagSummaryComponent', () => {
  let component: TagSummaryComponent;
  let fixture: ComponentFixture<TagSummaryComponent>;
  let mockFilters;

  beforeEach(async () => {
    mockFilters = jasmine.createSpyObj(['all']);
    await TestBed.configureTestingModule({
      declarations: [TagSummaryComponent],
      providers: [
        { provide: FlowFilterService, useValue: mockFilters },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TagSummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
