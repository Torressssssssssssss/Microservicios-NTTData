export class CartItem {

  quantity = 1;

  taco: any;

  constructor(taco: any) {
    this.taco = taco;
  }

  get lineTotal() {
    return Number(this.quantity) * this.taco.ingredients.reduce((sum, i) => sum + Number(i.unitPrice || 0), 0);
  }

}
