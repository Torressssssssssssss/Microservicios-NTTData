import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'Taco Cloud';
  constructor(http: HttpClient) { http.get('/api/v1/auth/csrf').subscribe(() => {}, () => {}); }
}
