package tacos;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import tacos.data.*;
import reactor.core.publisher.*;
@Configuration @Profile("dev")
public class DevelopmentConfig {
  @Bean public ApplicationRunner seedCatalog(IngredientRepository ingredients,TacoRepository tacoRepo) {
    return args->{
      List<Ingredient> values=new ArrayList<>();
      values.add(ingredient("COTO","Corn tortilla",Ingredient.Type.WRAP,"1.00",true,false));
      values.add(ingredient("FLTO","Flour tortilla",Ingredient.Type.WRAP,"1.20",true,true));
      values.add(ingredient("WHTO","Whole wheat tortilla",Ingredient.Type.WRAP,"1.30",true,true));
      values.add(ingredient("GRBF","Ground beef",Ingredient.Type.PROTEIN,"2.50",false,false));
      values.add(ingredient("CARN","Chicken",Ingredient.Type.PROTEIN,"2.00",false,false));
      values.add(ingredient("TOFU","Tofu",Ingredient.Type.PROTEIN,"1.70",true,false));
      values.get(values.size()-1).setAllergens(EnumSet.of(Ingredient.Allergen.SOY));
      values.add(ingredient("LETC","Lettuce",Ingredient.Type.VEGGIES,"0.50",true,false));
      values.add(ingredient("TMTO","Tomato",Ingredient.Type.VEGGIES,"0.60",true,false));
      values.add(ingredient("ONIO","Onion",Ingredient.Type.VEGGIES,"0.40",true,false));
      values.add(ingredient("CHED","Cheddar",Ingredient.Type.CHEESE,"1.00",false,false));
      values.add(ingredient("JACK","Monterrey Jack",Ingredient.Type.CHEESE,"1.10",false,false));
      values.add(ingredient("VGCH","Plant cheese",Ingredient.Type.CHEESE,"1.30",true,false));
      values.add(ingredient("SLSA","Salsa",Ingredient.Type.SAUCE,"0.40",true,false));
      values.get(values.size()-1).setSpiceLevel(2);
      values.add(ingredient("GUAC","Guacamole",Ingredient.Type.SAUCE,"1.20",true,false));
      values.add(ingredient("HABA","Habanero",Ingredient.Type.SAUCE,"0.60",true,false));
      values.get(values.size()-1).setSpiceLevel(5);
      // Borde de arranque: esperar los seeds antes de recibir solicitudes.
      Flux.fromIterable(values).concatMap(i->ingredients.findById(i.getId()).switchIfEmpty(ingredients.save(i))).then()
        .then(Flux.fromIterable(Arrays.asList("COTO","TMTO","SLSA")).concatMap(ingredients::findById).collectList())
        .flatMap(list->{Taco taco=new Taco();taco.setId("TACO-DEMO");taco.setName("Taco vegetal");taco.setIngredients(list);
          tacos.business.DesignService.classify(taco);return tacoRepo.findById(taco.getId()).switchIfEmpty(tacoRepo.save(taco));})
        .then().block(java.time.Duration.ofSeconds(30));
    };
  }
  private Ingredient ingredient(String id,String name,Ingredient.Type type,String price,boolean vegan,boolean gluten) {
    Ingredient i=new Ingredient(id,name,type);i.setUnitPrice(new BigDecimal(price));
    Set<Ingredient.DietaryTag> tags=EnumSet.noneOf(Ingredient.DietaryTag.class);
    if(vegan)tags.addAll(Arrays.asList(Ingredient.DietaryTag.VEGAN,Ingredient.DietaryTag.VEGETARIAN));
    if(!gluten)tags.add(Ingredient.DietaryTag.GLUTEN_FREE);
    if(type==Ingredient.Type.CHEESE && !vegan){tags.add(Ingredient.DietaryTag.VEGETARIAN);i.setAllergens(EnumSet.of(Ingredient.Allergen.MILK));}
    if(gluten)i.setAllergens(EnumSet.of(Ingredient.Allergen.GLUTEN));i.setDietaryTags(tags);return i;
  }
  @Bean public ApplicationRunner labAccounts(UserRepository users,PasswordEncoder encoder,Environment environment) {
    return args->{String password=environment.getProperty("TACO_LAB_PASSWORD");if(password==null)return;
      if(password.length()<10)throw new IllegalArgumentException("TACO_LAB_PASSWORD must have at least 10 characters");
      Flux.fromIterable(Arrays.asList("admin","kitchen")).concatMap(name->users.findByUsername(name).switchIfEmpty(Mono.defer(()->{
        User u=new User(name,encoder.encode(password),name,"Lab street","Lab city","ST","12345","000",name+"@example.test");
        u.setRoles(Collections.singleton(name.equals("admin")?"ADMIN":"KITCHEN"));return users.save(u);
      }))).then().block(java.time.Duration.ofSeconds(30));
    };
  }
}
