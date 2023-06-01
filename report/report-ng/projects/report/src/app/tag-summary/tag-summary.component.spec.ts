import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TagSummaryComponent } from './tag-summary.component';
import { FlowFilterService } from '../flow-filter.service';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

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
      imports: [
        MatButtonToggleModule,
        MatIconModule,
        MatExpansionModule,
        BrowserAnimationsModule,
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
