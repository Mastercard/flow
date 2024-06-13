import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DuctIndexComponent } from './duct-index.component';
import { DuctService } from '../duct.service';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';

describe('DuctIndexComponent', () => {
  let component: DuctIndexComponent;
  let fixture: ComponentFixture<DuctIndexComponent>;

  let mockDuctService;
  beforeEach(async () => {
    mockDuctService = jasmine.createSpyObj(['loadIndex']);
    await TestBed.configureTestingModule({
      declarations: [DuctIndexComponent],
      providers: [
        { provide: DuctService, useValue: mockDuctService },
      ],
      imports: [
        MatToolbarModule,
        MatListModule
      ],
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
