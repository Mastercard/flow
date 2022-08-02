import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelDiffDataSourceComponent } from './model-diff-data-source.component';

describe('ModelDiffDataSourceComponent', () => {
  let component: ModelDiffDataSourceComponent;
  let fixture: ComponentFixture<ModelDiffDataSourceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ModelDiffDataSourceComponent ]
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
