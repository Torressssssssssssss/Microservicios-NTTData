import { Injectable } from '@angular/core';
import { Http } from '@angular/http';

@Injectable()
export class ApiService {

  constructor(private http: Http) {
  }

  get(path: String) {
    return this.http.get('' + path);
  }

}
