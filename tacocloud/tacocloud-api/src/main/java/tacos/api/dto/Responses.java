package tacos.api.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;
import tacos.Ingredient;
import tacos.TacoOrder;

public final class Responses {
  @Data public static class IngredientResponse {
    private String id; private String name; private Ingredient.Type type;
    private BigDecimal unitPrice; private boolean available;
    private Set<Ingredient.DietaryTag> dietaryTags; private Set<Ingredient.Allergen> allergens;
    private int spiceLevel;
  }
  @Data public static class IngredientAdminResponse extends IngredientResponse {
    private long stockOnHand;private long reorderLevel;private Long version;
  }
  @Data public static class TacoResponse {
    private String id; private String name; private List<IngredientResponse> ingredients; private boolean beverage;
    private Set<Ingredient.DietaryTag> dietaryTags; private Set<Ingredient.Allergen> allergens;
    private int spiceLevel;
  }
  @Data public static class Line {
    private TacoResponse taco; private int quantity; private BigDecimal unitPriceAtPurchase; private BigDecimal subtotal;
  }
  @Data public static class OrderResponse {
    private String id; private Date placedAt; private TacoOrder.Status status;
    private String deliveryName; private String deliveryStreet; private String deliveryCity;
    private String deliveryState; private String deliveryZip;
    private List<Line> items; private BigDecimal subtotal; private BigDecimal discount;
    private BigDecimal total; private String currency; private Long version;
    private String paymentBrand; private String paymentLast4;
  }
  @Data public static class KitchenOrder {
    private String id; private Date placedAt; private TacoOrder.Status status;
    private List<Line> items; private int estimatedPrepMinutes; private String stationId; private Long version;
  }
  @Data public static class Page<T> {
    private final List<T> content; private final int page; private final int size; private final long totalElements;
  }
}
