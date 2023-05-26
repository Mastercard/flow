import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IndexComponent } from './index.component';
import { RouterTestingModule } from '@angular/router/testing';
import { IndexDataService } from '../index-data.service';

describe('IndexComponent', () => {
  let component: IndexComponent;
  let fixture: ComponentFixture<IndexComponent>;
  let mockIndexData;

  beforeEach(async () => {
    mockIndexData = jasmine.createSpyObj(['get', 'isValid', 'raw']);
    mockIndexData.get.and.returnValue({
      meta: {
        timestamp: 12345,
        modelTitle: "model title",
        testTitle: "test title",
      }
    });
    await TestBed.configureTestingModule({
      declarations: [IndexComponent],
      providers: [
        { provide: IndexDataService, useValue: mockIndexData },
      ],
      imports: [RouterTestingModule],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
