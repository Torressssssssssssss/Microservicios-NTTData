package tacos.business;
import java.math.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.*;
import tacos.*;
import tacos.api.dto.Requests.*;
import tacos.web.api.ApiException;
@Service
public class PricingService {
  private final DesignService designs; private final CouponService coupons; private final int maximum;
  public PricingService(DesignService designs,CouponService coupons,@Value("${tacocloud.max-quantity:50}") int maximum) {
    this.designs=designs;this.coupons=coupons;this.maximum=maximum;
  }
  public Mono<TacoOrder> quote(OrderCreateRequest request) {
    return Mono.defer(()-> {
      if(request.getItems()==null || request.getItems().isEmpty()) return Mono.error(ApiException.invalid("ITEMS_REQUIRED"));
      return Flux.fromIterable(request.getItems()).concatMap(item->{
        if(item.getQuantity()<1 || item.getQuantity()>maximum) return Mono.error(ApiException.invalid("QUANTITY_OUT_OF_RANGE"));
        return designs.resolve(item.getTaco()).map(taco->{
          OrderLine line=new OrderLine();line.setTaco(taco);line.setQuantity(item.getQuantity());
          BigDecimal unit=taco.getIngredients().stream().map(Ingredient::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
          line.setUnitPriceAtPurchase(unit);line.setSubtotal(unit.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2,RoundingMode.HALF_UP));return line;
        });
      }).collectList().map(lines->{
        TacoOrder order=new TacoOrder(); order.setItems(lines);
        order.setSubtotal(lines.stream().map(OrderLine::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP));
        order.setDiscount(coupons.discount(request.getCouponCode(),order.getSubtotal()));order.setTotal(order.getSubtotal().subtract(order.getDiscount()));
        order.setCouponCode(coupons.normalize(request.getCouponCode()));
        order.setDeliveryName(request.getDeliveryName());order.setDeliveryStreet(request.getDeliveryStreet());order.setDeliveryCity(request.getDeliveryCity());
        order.setDeliveryState(request.getDeliveryState());order.setDeliveryZip(request.getDeliveryZip());
        return order;
      });
    });
  }
}
