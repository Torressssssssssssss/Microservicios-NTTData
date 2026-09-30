package tacos.business;
import java.util.*;
import java.util.stream.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import tacos.Ingredient;
@Configuration
public class DesignRules {
  private static List<DesignRule.Violation> result(boolean invalid,String code) { return invalid?Collections.singletonList(new DesignRule.Violation(code,DesignValidationException.message(code))):Collections.emptyList(); }
  @Bean public DesignRule ingredientCount() { return (d,i)->result(i.size()<2 || i.size()>12,"INGREDIENT_COUNT"); }
  @Bean public DesignRule oneWrap() { return (d,i)->result(i.stream().filter(x->x.getType()==Ingredient.Type.WRAP).count()!=1,"EXACTLY_ONE_WRAP"); }
  @Bean public DesignRule noDuplicates() { return (d,i)->result(new HashSet<>(d.getIngredientIds()).size()!=d.getIngredientIds().size(),"DUPLICATE_INGREDIENT"); }
  @Bean public DesignRule availableIngredients() { return (d,i)->result(i.stream().anyMatch(x->!x.isAvailable() || x.getStockOnHand()<1),"INGREDIENT_UNAVAILABLE"); }
  @Bean public DesignRule hotNeedsDrink(@Value("${tacocloud.rules.hot-drink-enabled:true}") boolean enabled,
      @Value("${tacocloud.rules.hot-threshold:4}") int threshold) {
    return (d,i)->result(enabled && !d.isBeverage() && i.stream().anyMatch(x->x.getSpiceLevel()>=threshold),"HOT_REQUIRES_BEVERAGE");
  }
  @Bean public DesignRule veganPromise(@Value("${tacocloud.rules.vegan-enabled:true}") boolean enabled) {
    return (d,i)->result(enabled && d.isVegan() && i.stream().anyMatch(x->!x.getDietaryTags().contains(Ingredient.DietaryTag.VEGAN)),"VEGAN_PROMISE");
  }
}
