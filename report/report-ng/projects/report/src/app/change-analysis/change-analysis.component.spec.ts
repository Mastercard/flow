import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeAnalysisComponent } from './change-analysis.component';
import { FlowDiffService } from '../flow-diff.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { RouterTestingModule } from '@angular/router/testing';
import { Entry } from '../types';
import { MatExpansionModule } from '@angular/material/expansion';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PortalModule } from '@angular/cdk/portal';

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
      declarations: [
        ChangeAnalysisComponent,

        StubFlowNavList,
      ],
      providers: [
        { provide: FlowDiffService, useValue: mockFds },
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [
        RouterTestingModule,
        BrowserAnimationsModule,
        PortalModule,
        MatExpansionModule,
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChangeAnalysisComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

@Component({
  selector: 'app-flow-nav-list',
  template: ''
})
class StubFlowNavList {
  @Input() basePath: string = "";
  @Input() showResult: boolean = true;
  @Input() draggable: boolean = false;
  @Input() entries: Entry[] = [];
}
