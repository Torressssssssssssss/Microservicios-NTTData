import { Component, OnInit, Injectable } from '@angular/core';
import { CartService } from './cart-service';
import { HttpClient } from '@angular/common/http';
@Component({selector: 'taco-cart', templateUrl: 'cart.component.html', styleUrls: ['./cart.component.css']})
@Injectable()
export class CartComponent implements OnInit {
  model = {deliveryName: '', deliveryStreet: '', deliveryCity: '', deliveryState: '', deliveryZip: '', paymentMethodId: '', couponCode: ''};
  syntheticCard = 'test_4242';
  quote: any;
  error = '';
  checkoutKey = 'checkout_' + Date.now() + '_' + Math.random().toString(36).slice(2);
  constructor(private cart: CartService, private httpClient: HttpClient) {}
  ngOnInit() {}
  get cartItems() { return this.cart.getItemsInCart(); }
  get cartTotal() { return this.cart.getCartTotal(); }
  payload() { return Object.assign({}, this.model, {items: this.cart.orderItems()}); }
  tokenize() {
    this.httpClient.post('/api/v1/payment-methods/tokenize', {syntheticCard: this.syntheticCard})
      .subscribe((payment: any) => this.model.paymentMethodId = payment.id, e => this.error = e.error.code);
  }
  getQuote() { this.httpClient.post('/api/v1/orders/quote', this.payload()).subscribe(q => this.quote = q, e => this.error = e.error.code); }
  onSubmit() {
    this.httpClient.post('/api/v1/orders', this.payload(), {headers: {'Idempotency-Key': this.checkoutKey}}).subscribe(r => {this.quote = r; this.cart.emptyCart();this.checkoutKey = 'checkout_' + Date.now() + '_' + Math.random().toString(36).slice(2);}, e => this.error = e.error.code);
  }
}
