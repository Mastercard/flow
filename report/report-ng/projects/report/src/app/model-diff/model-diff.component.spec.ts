import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelDiffComponent } from './model-diff.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataSourceComponent } from '../model-diff-data-source/model-diff-data-source.component';

describe('ModelDiffComponent', () => {
  let component: ModelDiffComponent;
  let fixture: ComponentFixture<ModelDiffComponent>;
  let mockFps;
  const mockFromMdds = jasmine.createSpyObj('ModelDiffDataSourceComponent',
    ['getValue', 'setValue', 'valueChanges']);
  const mockToMdds = jasmine.createSpyObj('ModelDiffDataSourceComponent',
    ['getValue', 'setValue', 'valueChanges']);

  beforeEach(async () => {
    mockFps = jasmine.createSpyObj(['onUnpair', 'onPair', 'buildQuery', 'paired',
      'unpairedLeftEntries', 'unpairedRightEntries']);
    mockFps.paired.and.returnValue([]);
    mockFps.unpairedLeftEntries.and.returnValue([]);
    mockFps.unpairedRightEntries.and.returnValue([]);

    await TestBed.configureTestingModule({
      declarations: [ModelDiffComponent],
      providers: [
        { provide: FlowPairingService, useValue: mockFps },
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
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
