import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelDiffDataSourceComponent } from './model-diff-data-source.component';
import { ModelDiffDataService } from '../model-diff-data.service';
import { RouterTestingModule } from '@angular/router/testing';

describe('ModelDiffDataSourceComponent', () => {
  let component: ModelDiffDataSourceComponent;
  let fixture: ComponentFixture<ModelDiffDataSourceComponent>;
  let mockMdds;

  beforeEach(async () => {
    mockMdds = jasmine.createSpyObj(['onFlow', 'onIndex']);
    await TestBed.configureTestingModule({
      declarations: [ModelDiffDataSourceComponent],
      providers: [
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [RouterTestingModule]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ModelDiffDataSourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
