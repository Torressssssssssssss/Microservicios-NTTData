package tacos.api.dto;
import tacos.*;
import tacos.api.dto.Responses.*;
import java.util.stream.Collectors;
public final class ApiMapper {
  private ApiMapper() { }
  public static IngredientResponse ingredient(Ingredient value) {
    IngredientResponse r = new IngredientResponse(); r.setId(value.getId()); r.setName(value.getName());
    r.setType(value.getType()); r.setUnitPrice(value.getUnitPrice());
    r.setAvailable(value.isAvailable() && value.getStockOnHand()>0);
    r.setDietaryTags(value.getDietaryTags()); r.setAllergens(value.getAllergens()); r.setSpiceLevel(value.getSpiceLevel()); return r;
  }
  public static IngredientAdminResponse ingredientAdmin(Ingredient value) {
    IngredientAdminResponse r=new IngredientAdminResponse();r.setId(value.getId());r.setName(value.getName());r.setType(value.getType());r.setUnitPrice(value.getUnitPrice());
    r.setAvailable(value.isAvailable());r.setDietaryTags(value.getDietaryTags());r.setAllergens(value.getAllergens());r.setSpiceLevel(value.getSpiceLevel());
    r.setStockOnHand(value.getStockOnHand());r.setReorderLevel(value.getReorderLevel());r.setVersion(value.getVersion());return r;
  }
  public static TacoResponse taco(Taco value) {
    TacoResponse r = new TacoResponse(); r.setId(value.getId()); r.setName(value.getName());
    r.setBeverage(value.isBeverage());r.setIngredients(value.getIngredients().stream().map(ApiMapper::ingredient).collect(Collectors.toList()));
    r.setDietaryTags(value.getDietaryTags()); r.setAllergens(value.getAllergens()); r.setSpiceLevel(value.getSpiceLevel()); return r;
  }
  public static Line line(OrderLine value) {
    Line r = new Line(); r.setTaco(taco(value.getTaco())); r.setQuantity(value.getQuantity());
    r.setUnitPriceAtPurchase(value.getUnitPriceAtPurchase()); r.setSubtotal(value.getSubtotal()); return r;
  }
  public static OrderResponse order(TacoOrder value) {
    OrderResponse r = new OrderResponse(); r.setId(value.getId()); r.setPlacedAt(value.getPlacedAt());
    r.setStatus(value.getStatus()); r.setVersion(value.getVersion());
    r.setDeliveryName(value.getDeliveryName()); r.setDeliveryStreet(value.getDeliveryStreet());
    r.setDeliveryCity(value.getDeliveryCity()); r.setDeliveryState(value.getDeliveryState()); r.setDeliveryZip(value.getDeliveryZip());
    r.setItems(value.getItems().stream().map(ApiMapper::line).collect(Collectors.toList()));
    r.setSubtotal(value.getSubtotal()); r.setDiscount(value.getDiscount()); r.setTotal(value.getTotal()); r.setCurrency(value.getCurrency());
    r.setPaymentBrand(value.getPaymentBrand()); r.setPaymentLast4(value.getPaymentLast4()); return r;
  }
  public static KitchenOrder kitchen(TacoOrder value) {
    KitchenOrder r = new KitchenOrder(); r.setId(value.getId()); r.setPlacedAt(value.getPlacedAt()); r.setStatus(value.getStatus());
    r.setItems(value.getItems().stream().map(ApiMapper::line).collect(Collectors.toList())); r.setVersion(value.getVersion());
    r.setEstimatedPrepMinutes(value.getEstimatedPrepMinutes()); r.setStationId(value.getStationId()); return r;
  }
}
