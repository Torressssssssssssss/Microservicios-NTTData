package tacos.business;
import java.util.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import tacos.*;
import tacos.api.dto.Requests.Design;
import tacos.data.IngredientRepository;
import tacos.web.api.ApiException;
@Service
public class DesignService {
  private final IngredientRepository ingredients; private final List<DesignRule> rules;
  public DesignService(IngredientRepository ingredients,List<DesignRule> rules) { this.ingredients=ingredients;this.rules=rules; }
  public List<String> violations(Design request,List<Ingredient> values) {
    return validate(request,values).stream().map(DesignRule.Violation::getCode).collect(java.util.stream.Collectors.toList());
  }
  public List<DesignRule.Violation> validate(Design request,List<Ingredient> values) {
    TreeMap<String,DesignRule.Violation> result=new TreeMap<>();rules.forEach(rule->rule.violations(request,values).forEach(v->result.put(v.getCode(),v)));return new ArrayList<>(result.values());
  }
  public Mono<Taco> resolve(Design request) {
    return Mono.defer(()-> {
      if(request==null || request.getIngredientIds()==null || request.getName()==null) return Mono.error(ApiException.invalid("INVALID_DESIGN"));
      return Flux.fromIterable(new java.util.LinkedHashSet<>(request.getIngredientIds())).concatMap(id->ingredients.findById(id)
          .switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT_"+id)))).collectList().map(values->{
        List<DesignRule.Violation> errors=validate(request,values);
        if(!errors.isEmpty()) throw new DesignValidationException(errors);
        Taco taco=new Taco(); taco.setName(request.getName()); taco.setIngredients(values);taco.setBeverage(request.isBeverage());taco.setVeganRequested(request.isVegan()); classify(taco); return taco;
      });
    });
  }
  public static void classify(Taco taco) {
    Set<Ingredient.DietaryTag> tags=EnumSet.allOf(Ingredient.DietaryTag.class);
    Set<Ingredient.Allergen> allergens=EnumSet.noneOf(Ingredient.Allergen.class); int spice=0;
    for(Ingredient i:taco.getIngredients()) { tags.retainAll(i.getDietaryTags()); allergens.addAll(i.getAllergens()); spice=Math.max(spice,i.getSpiceLevel()); }
    if(taco.getIngredients().isEmpty()) tags.clear();
    taco.setDietaryTags(tags);taco.setAllergens(allergens);taco.setSpiceLevel(spice);
  }
}
