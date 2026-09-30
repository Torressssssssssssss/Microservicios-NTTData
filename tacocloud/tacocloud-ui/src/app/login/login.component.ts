import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
  selector: 'login-tacocloud',
  templateUrl: 'login.component.html',
  styleUrls: ['./login.component.css']
})

export class LoginComponent implements OnInit {
  loginModel = {username: '', password: ''};
  registrationModel = {username: '', password: '', verifyPassword: '', fullname: '', street: '', city: '', state: '', zip: '', phone: '', email: ''};
  loginError = '';
  registrationError = '';
  message = '';
  busy = false;

  constructor(private http: HttpClient, private router: Router) { }

  ngOnInit() { this.http.get('/api/v1/auth/csrf').subscribe(() => {}, () => this.loginError = 'Unable to initialize session.'); }

  signIn() {
    this.loginError = '';this.message = '';
    if (!this.loginModel.username || !this.loginModel.password) {this.loginError = 'Enter username and password.';return;}
    this.busy = true;
    this.http.post('/api/v1/auth/login', this.loginModel).subscribe(
      () => {this.busy = false;this.router.navigate(['/home']);},
      () => {this.busy = false;this.loginError = 'Invalid username or password.';}
    );
  }

  register() {
    this.registrationError = '';this.message = '';
    if (this.registrationModel.password !== this.registrationModel.verifyPassword) {
      this.registrationError = 'Passwords do not match.';return;
    }
    if (this.registrationModel.password.length < 10) {
      this.registrationError = 'Password must contain at least 10 characters.';return;
    }
    const body: any = Object.assign({}, this.registrationModel);delete body.verifyPassword;
    this.busy = true;
    this.http.post('/api/v1/auth/register', body).subscribe(
      () => {
        this.loginModel = {username: this.registrationModel.username, password: this.registrationModel.password};
        this.message = 'Account created. Signing in...';this.signIn();
      },
      error => {
        this.busy = false;
        this.registrationError = error.status === 409 ? 'Username or email already exists.' : 'Check the required fields.';
      }
    );
  }
}
