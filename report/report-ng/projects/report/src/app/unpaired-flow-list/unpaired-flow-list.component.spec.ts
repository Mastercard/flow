import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UnpairedFlowListComponent } from './unpaired-flow-list.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowPairingService } from '../flow-pairing.service';
import { FlowFilterService } from '../flow-filter.service';

describe('UnpairedFlowListComponent', () => {
  let component: UnpairedFlowListComponent;
  let fixture: ComponentFixture<UnpairedFlowListComponent>;
  let mockMdds;
  let mockFps;
  let mockFilter;

  beforeEach(async () => {
    mockMdds = jasmine.createSpyObj(['index']);
    mockFps = jasmine.createSpyObj(['onRebuild', 'onUnpair', 'onPair',
      'unpairedLeftEntries', 'unpairedRightEntries']);
    mockFilter = jasmine.createSpyObj(['onUpdate']);

    await TestBed.configureTestingModule({
      declarations: [UnpairedFlowListComponent],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
        { provide: FlowPairingService, useValue: mockFps },
        { provide: FlowFilterService, useValue: mockFilter },
      ],

    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(UnpairedFlowListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
