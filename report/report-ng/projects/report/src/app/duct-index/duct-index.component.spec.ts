import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DuctIndexComponent } from './duct-index.component';

describe('DuctIndexComponent', () => {
  let component: DuctIndexComponent;
  let fixture: ComponentFixture<DuctIndexComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DuctIndexComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DuctIndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
