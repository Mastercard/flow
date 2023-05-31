import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PairedFlowListComponent } from './paired-flow-list.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowPairingService } from '../flow-pairing.service';
import { FlowFilterService } from '../flow-filter.service';

describe('PairedFlowListComponent', () => {
  let component: PairedFlowListComponent;
  let fixture: ComponentFixture<PairedFlowListComponent>;
  let mockMdds;
  let mockFps;
  let mockFilter;

  beforeEach(async () => {
    mockMdds = jasmine.createSpyObj(['index']);
    mockFps = jasmine.createSpyObj(['paired']);
    mockFps.paired.and.returnValue([]);
    mockFilter = jasmine.createSpyObj(['passes']);

    await TestBed.configureTestingModule({
      declarations: [PairedFlowListComponent],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
        { provide: FlowPairingService, useValue: mockFps },
        { provide: FlowFilterService, useValue: mockFilter },
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PairedFlowListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
