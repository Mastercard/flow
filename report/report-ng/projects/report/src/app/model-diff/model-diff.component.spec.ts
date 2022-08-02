import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ModelDiffComponent } from './model-diff.component';

describe('ModelDiffComponent', () => {
  let component: ModelDiffComponent;
  let fixture: ComponentFixture<ModelDiffComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ModelDiffComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ModelDiffComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
