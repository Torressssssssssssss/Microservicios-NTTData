package tacos.api.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import tacos.Ingredient;
import tacos.TacoOrder;

public final class Requests {
  public static class Strict {
    @JsonAnySetter public void reject(String name, Object value) {
      throw new IllegalArgumentException("Unexpected field");
    }
  }
  @Data public static class IngredientRequest extends Strict {
    private String id;
    @NotBlank @Size(max=100) private String name;
    @NotNull private Ingredient.Type type;
  }
  @Data public static class Catalog extends Strict {
    @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) private BigDecimal unitPrice;
    private boolean available;
    @Min(0) private long reorderLevel;
    @NotNull private Long version;
    @NotNull private Set<Ingredient.DietaryTag> dietaryTags = new HashSet<>();
    @NotNull private Set<Ingredient.Allergen> allergens = new HashSet<>();
    @Min(0) @Max(5) private int spiceLevel;
  }
  @Data public static class Stock extends Strict { private long delta; }
  @Data public static class Design extends Strict {
    @NotBlank @Size(min=5,max=100) private String name;
    @NotNull @Size(min=2,max=12) private List<@NotBlank String> ingredientIds;
    private boolean beverage;
    private boolean vegan;
  }
  @Data public static class Item extends Strict {
    @NotNull @Valid private Design taco;
    @Min(1) @Max(50) private int quantity;
  }
  @Data public static class Delivery extends Strict {
    @NotBlank @Size(max=100) private String deliveryName;
    @NotBlank @Size(max=200) private String deliveryStreet;
    @NotBlank @Size(max=100) private String deliveryCity;
    @NotBlank @Size(max=100) private String deliveryState;
    @NotBlank @Pattern(regexp="[A-Za-z0-9 -]{3,12}") private String deliveryZip;
  }
  @Data public static class OrderCreateRequest extends Delivery {
    @NotEmpty @Size(max=50) @Valid private List<@NotNull Item> items;
    @NotBlank @Size(max=100) private String paymentMethodId;
    @Size(max=40) private String couponCode;
  }
  @Data public static class Replacement extends OrderCreateRequest { private String id; }
  @Data public static class Tokenize extends Strict {
    @NotBlank @Pattern(regexp="test_[0-9]{4}") private String syntheticCard;
  }
  @Data public static class Reorder extends Strict {
    @NotBlank @Size(max=100) private String paymentMethodId;
    private boolean confirmPriceChange;
  }
  @Data public static class Transition extends Strict {
    @NotNull private TacoOrder.Status status;
    @NotNull private Long version;
    @NotBlank @Size(max=200) private String reason;
  }
  @Data public static class Rating extends Strict { @Min(1) @Max(5) private int score; }
}
