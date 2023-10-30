import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DuctIndexItemComponent } from './duct-index-item.component';

describe('DuctIndexItemComponent', () => {
  let component: DuctIndexItemComponent;
  let fixture: ComponentFixture<DuctIndexItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DuctIndexItemComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DuctIndexItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
