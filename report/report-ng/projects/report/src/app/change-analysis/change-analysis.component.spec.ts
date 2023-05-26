import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangeAnalysisComponent } from './change-analysis.component';
import { FlowDiffService } from '../flow-diff.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { RouterTestingModule } from '@angular/router/testing';

describe('ChangeAnalysisComponent', () => {
  let component: ChangeAnalysisComponent;
  let fixture: ComponentFixture<ChangeAnalysisComponent>;
  let mockFds;
  let mockMdds;

  beforeEach(async () => {
    mockFds = jasmine.createSpyObj(['onPairing', 'onFlowData']);
    mockFds.sourceData = [];
    mockFds.collated = [];
    mockMdds = jasmine.createSpyObj(['index', 'onIndex']);
    await TestBed.configureTestingModule({
      declarations: [ChangeAnalysisComponent],
      providers: [
        { provide: FlowDiffService, useValue: mockFds },
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [RouterTestingModule]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChangeAnalysisComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    // Need to mock/inject a bunch of stuff
    // expect(component).toBeTruthy();
  });
});
