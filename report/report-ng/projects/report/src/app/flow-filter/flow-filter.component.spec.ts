import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowFilterComponent } from './flow-filter.component';
import { RouterTestingModule } from '@angular/router/testing';

describe('FlowFilterComponent', () => {
  let component: FlowFilterComponent;
  let fixture: ComponentFixture<FlowFilterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [FlowFilterComponent],
      imports: [RouterTestingModule]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowFilterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
