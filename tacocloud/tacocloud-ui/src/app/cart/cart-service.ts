import { CartItem } from './cart-item';

export class CartService {

  items$: CartItem[] = [];

  constructor() {
    this.items$ = [];
  }

  addToCart(taco: any) {
    this.items$.push(new CartItem(taco));
  }

  getItemsInCart() {
    return this.items$;
  }

  getCartTotal() {
    let total = 0;
    this.items$.forEach(item => {
      total += item.lineTotal;
    });
    return total;
  }

  orderItems() {
    return this.items$.filter(item => Number(item.quantity) > 0).map(item => ({
      taco: {name: item.taco.name, ingredientIds: item.taco.ingredients.map(i => i.id), beverage: !!item.taco.beverage},
      quantity: Number(item.quantity)
    }));
  }

  emptyCart() {
    this.items$ = [];
  }

}
