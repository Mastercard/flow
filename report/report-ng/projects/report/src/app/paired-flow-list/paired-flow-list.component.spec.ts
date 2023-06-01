import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { PairedFlowListComponent } from './paired-flow-list.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowPairingService } from '../flow-pairing.service';
import { FlowFilterService } from '../flow-filter.service';
import { Entry } from '../types';
import { MatListModule } from '@angular/material/list';

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
      declarations: [
        PairedFlowListComponent,
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
    fixture = TestBed.createComponent(PairedFlowListComponent);
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
