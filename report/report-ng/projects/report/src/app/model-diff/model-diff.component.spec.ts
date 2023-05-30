import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelDiffComponent } from './model-diff.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataSourceComponent } from '../model-diff-data-source/model-diff-data-source.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { ChangeViewComponent } from '../change-view/change-view.component';
import { ChangeAnalysisComponent } from '../change-analysis/change-analysis.component';

describe('ModelDiffComponent', () => {
  let component: ModelDiffComponent;
  let fixture: ComponentFixture<ModelDiffComponent>;
  let mockFps;
  let mockMdds;
  const mockFromMdds = jasmine.createSpyObj('ModelDiffDataSourceComponent',
    ['getValue', 'setValue', 'valueChanges', 'path',]);
  const mockToMdds = jasmine.createSpyObj('ModelDiffDataSourceComponent',
    ['getValue', 'setValue', 'valueChanges']);
  const mockChangeView = jasmine.createSpyObj('ChangeViewComponent',
    ['onSelection', 'view', 'buildQuery']);
  const mockChangeAnalysis = jasmine.createSpyObj('ChangeAnalysisComponent',
    ['toChangeView']);

  beforeEach(async () => {
    mockFps = jasmine.createSpyObj([
      'onUnpair', 'onPair', 'onRebuild', 'buildQuery',
      'paired', 'unpairedLeftEntries', 'unpairedRightEntries']);
    mockFps.paired.and.returnValue([]);
    mockFps.unpairedLeftEntries.and.returnValue([]);
    mockFps.unpairedRightEntries.and.returnValue([]);

    mockMdds = jasmine.createSpyObj(['onFlow', 'onIndex', 'index']);

    await TestBed.configureTestingModule({
      declarations: [
        ModelDiffComponent,
        ModelDiffDataSourceComponent,
        ChangeViewComponent,
        ChangeAnalysisComponent,
      ],
      providers: [
        { provide: FlowPairingService, useValue: mockFps },
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [RouterTestingModule],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ModelDiffComponent);
    component = fixture.componentInstance;
    component.from = mockFromMdds;
    component.to = mockToMdds;
    component.changeView = mockChangeView;
    component.changeAnalysis = mockChangeAnalysis;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
