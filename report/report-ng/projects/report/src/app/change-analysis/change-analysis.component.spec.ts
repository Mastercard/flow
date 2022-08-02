import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangeAnalysisComponent } from './change-analysis.component';

describe('ChangeAnalysisComponent', () => {
  let component: ChangeAnalysisComponent;
  let fixture: ComponentFixture<ChangeAnalysisComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChangeAnalysisComponent ]
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
