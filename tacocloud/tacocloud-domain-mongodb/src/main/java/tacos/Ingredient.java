package tacos;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access=AccessLevel.PRIVATE, force=true)
@Document
public class Ingredient {

  @Id
  private String id;
  private String name;
  private Type type;

  private BigDecimal unitPrice = new BigDecimal("1.00");
  private boolean available = true;
  private long stockOnHand = 100;
  private long reorderLevel = 10;
  @Version private Long version;
  private Set<DietaryTag> dietaryTags = new HashSet<>();
  private Set<Allergen> allergens = new HashSet<>();
  private int spiceLevel;

  public Ingredient(String id, String name, Type type) {
    this.id = id; this.name = name; this.type = type;
  }
  public enum DietaryTag { VEGAN, VEGETARIAN, GLUTEN_FREE }
  public enum Allergen { GLUTEN, MILK, SOY, PEANUT, TREE_NUT, EGG, FISH, SHELLFISH }
  public enum Type {
    WRAP, PROTEIN, VEGGIES, CHEESE, SAUCE
  }

}
