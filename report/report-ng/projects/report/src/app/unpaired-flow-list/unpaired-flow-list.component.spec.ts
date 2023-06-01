import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { UnpairedFlowListComponent } from './unpaired-flow-list.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowPairingService } from '../flow-pairing.service';
import { FlowFilterService } from '../flow-filter.service';
import { Entry } from '../types';
import { MatListModule } from '@angular/material/list';

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
      declarations: [
        UnpairedFlowListComponent,
        StubFlowNavList,
      ],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
        { provide: FlowPairingService, useValue: mockFps },
        { provide: FlowFilterService, useValue: mockFilter },
      ],
      imports: [
        MatListModule,
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
