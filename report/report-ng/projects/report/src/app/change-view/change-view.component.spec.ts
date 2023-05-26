import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ChangeViewComponent } from './change-view.component';
import { FlowDiffService } from '../flow-diff.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { FlowFilterService } from '../flow-filter.service';

describe('ChangeViewComponent', () => {
  let component: ChangeViewComponent;
  let fixture: ComponentFixture<ChangeViewComponent>;
  let mockFds;
  let mockMdds;
  let mockFilter;

  beforeEach(async () => {
    mockFds = jasmine.createSpyObj(['onPairing', 'onFlowData']);
    mockMdds = jasmine.createSpyObj(['index']);
    mockFilter = jasmine.createSpyObj(['onUpdate', 'passes', 'isEmpty'])
    await TestBed.configureTestingModule({
      declarations: [ChangeViewComponent],
      providers: [
        { provide: FlowDiffService, useValue: mockFds },
        { provide: ModelDiffDataService, useValue: mockMdds },
        { provide: FlowFilterService, useValue: mockFilter },
      ],
      imports: [RouterTestingModule]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChangeViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
