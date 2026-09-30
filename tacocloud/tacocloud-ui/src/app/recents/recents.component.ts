import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CartService } from '../cart/cart-service';
@Component({selector: 'recent-tacos', templateUrl: 'recents.component.html', styleUrls: ['./recents.component.css']})
export class RecentTacosComponent implements OnInit {
  recentTacos: any[] = []; favorites: string[] = []; ranking: any[] = []; history: any[] = [];
  name = ''; diet = ''; page = 0; total = 0; error = ''; paymentMethodId = ''; confirmPriceChange = false;
  constructor(private httpClient: HttpClient, private cart: CartService) {}
  ngOnInit() { this.search(); this.loadFavorites(); }
  search() {
    this.httpClient.get('/api/v1/tacos?page=' + this.page + '&size=12&name=' + encodeURIComponent(this.name) + (this.diet ? '&diet=' + this.diet : ''))
      .subscribe((data: any) => {this.recentTacos = data.content; this.total = data.totalElements;}, e => this.error = e.error.code);
  }
  next(delta: number) { this.page = Math.max(0, this.page + delta); this.search(); }
  loadFavorites() { this.httpClient.get('/api/v1/users/me/favorites?size=100').subscribe((r: any) => this.favorites = r.content.map(t => t.id), () => {}); }
  favorite(taco: any) {
    const url = '/api/v1/users/me/favorites/' + taco.id;
    const action = this.favorites.indexOf(taco.id) >= 0 ? this.httpClient.delete(url) : this.httpClient.put(url, {});
    action.subscribe(() => this.loadFavorites(), e => this.error = e.error.code);
  }
  rate(taco: any, score: string) { this.httpClient.put('/api/v1/tacos/' + taco.id + '/rating', {score: Number(score)}).subscribe(() => this.top(), e => this.error = e.error.code); }
  top() { this.httpClient.get('/api/v1/tacos/top').subscribe((r: any[]) => this.ranking = r); }
  orders() { this.httpClient.get('/api/v1/users/me/orders?size=20').subscribe((r: any) => this.history = r.content, e => this.error = e.error.code); }
  reorder(order: any) {
    if (!order.reorderKey) { order.reorderKey = 'reorder_' + Date.now() + '_' + Math.random().toString(36).slice(2); }
    this.httpClient.post('/api/v1/orders/' + order.id + '/reorder', {paymentMethodId: this.paymentMethodId, confirmPriceChange: this.confirmPriceChange},
      {headers: {'Idempotency-Key': order.reorderKey}}).subscribe(() => this.orders(), e => {this.error = e.error.code; if (e.error.code === 'PRICE_CHANGED_CONFIRM_REQUIRED') { order.reorderKey = null; }});
  }
  add(taco: any) { this.cart.addToCart(taco); }
}
