package tacos.business;
import java.util.*;
import java.util.regex.Pattern;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.*;
import tacos.*;
import tacos.data.*;
import tacos.api.dto.Requests.*;
import tacos.api.dto.Responses.Page;
import tacos.api.dto.Responses.TacoResponse;
import tacos.api.dto.ApiMapper;
import tacos.web.api.ApiException;
@Service
public class CatalogService {
  private final ReactiveMongoTemplate mongo;private final IngredientRepository ingredients;private final TacoRepository tacos;
  private final DesignService designs;private final Clock clock;private final int maxPage;
  public CatalogService(ReactiveMongoTemplate mongo,IngredientRepository ingredients,TacoRepository tacos,DesignService designs,
      Clock clock,@Value("${tacocloud.max-page-size:100}") int maxPage) {
    this.mongo=mongo;this.ingredients=ingredients;this.tacos=tacos;this.designs=designs;this.clock=clock;this.maxPage=maxPage;
  }
  public Mono<Ingredient> catalog(String id,Catalog request) {
    return ingredients.findById(id).switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT"))).flatMap(i->{
      if(!Objects.equals(i.getVersion(),request.getVersion())) return Mono.error(ApiException.conflict("STALE_VERSION"));
      i.setUnitPrice(request.getUnitPrice());i.setAvailable(request.isAvailable());i.setReorderLevel(request.getReorderLevel());
      i.setDietaryTags(request.getDietaryTags());i.setAllergens(request.getAllergens());i.setSpiceLevel(request.getSpiceLevel());
      return ingredients.save(i).flatMap(saved->refreshClassifications(saved).thenReturn(saved));
    });
  }
  private Mono<Void> refreshClassifications(Ingredient ingredient) {
    return mongo.find(Query.query(Criteria.where("ingredients.id").is(ingredient.getId())),Taco.class).concatMap(taco->{
      taco.getIngredients().replaceAll(old->old.getId().equals(ingredient.getId())?ingredient:old);DesignService.classify(taco);
      return tacos.save(taco);
    }).then();
  }
  public Mono<Ingredient> adjust(String id,long delta) {
    if(delta==Long.MIN_VALUE || Math.abs(delta)>1000000) return Mono.error(ApiException.invalid("INVALID_STOCK_ADJUSTMENT"));
    Criteria criteria=Criteria.where("_id").is(id);if(delta<0) criteria.and("stockOnHand").gte(-delta);
    return mongo.findAndModify(Query.query(criteria),new Update().inc("stockOnHand",delta).inc("version",1),
        FindAndModifyOptions.options().returnNew(true),Ingredient.class).switchIfEmpty(Mono.error(ApiException.conflict("STOCK_ADJUSTMENT_REJECTED")));
  }
  public void pageBounds(int page,int size) { if(page<0 || size<1 || size>maxPage || page>100000) throw new IllegalArgumentException("Invalid page"); }
  public Query searchQuery(String name,String ingredientId,Ingredient.DietaryTag diet,Ingredient.Allergen exclude,Integer spice,String sort) {
    Criteria c=Criteria.where("published").is(true);
    if(name!=null && !name.isEmpty()) { if(name.length()>80) throw new IllegalArgumentException("Search too long");c.and("name").regex(Pattern.quote(name),"i"); }
    if(ingredientId!=null)c.and("ingredients.id").is(ingredientId);
    if(diet!=null)c.and("dietaryTags").is(diet);
    if(exclude!=null)c.and("allergens").ne(exclude);
    if(spice!=null) { if(spice<0 || spice>5)throw new IllegalArgumentException("Invalid spice");c.and("spiceLevel").lte(spice); }
    String[] parts=sort.split(",");
    if(parts.length!=2 || !Arrays.asList("createdAt","name","spiceLevel").contains(parts[0]) || !Arrays.asList("asc","desc").contains(parts[1])) throw new IllegalArgumentException("Invalid sort");
    return Query.query(c).with(Sort.by(Sort.Direction.fromString(parts[1]),parts[0]).and(Sort.by("_id")));
  }
  public Mono<Page<TacoResponse>> search(String name,String ingredientId,Ingredient.DietaryTag diet,Ingredient.Allergen exclude,Integer spice,int page,int size,String sort) {
    pageBounds(page,size);Query q=searchQuery(name,ingredientId,diet,exclude,spice,sort);
    return Mono.zip(mongo.count(Query.of(q),Taco.class),mongo.find(q.skip((long)page*size).limit(size),Taco.class).map(ApiMapper::taco).collectList())
      .map(pair->new Page<>(pair.getT2(),page,size,pair.getT1()));
  }
  public Mono<Taco> current(Taco taco) {
    Design request=new Design();request.setName(taco.getName());request.setBeverage(taco.isBeverage());request.setVegan(taco.isVeganRequested());
    request.setIngredientIds(taco.getIngredients().stream().map(Ingredient::getId).collect(java.util.stream.Collectors.toList()));
    return designs.resolve(request).map(value->{value.setId(taco.getId());value.setCreatedAt(taco.getCreatedAt());return value;});
  }
  public Mono<Map<String,Object>> today() {
    return mongo.find(Query.query(Criteria.where("published").is(true)).with(Sort.by("_id")),Taco.class)
      .concatMap(taco->current(taco).onErrorResume(ApiException.class,e->Mono.empty())).collectList().flatMap(list->{
        if(list.isEmpty())return Mono.error(ApiException.missing("DAILY_TACO"));
        LocalDate date=LocalDate.now(clock);int index=Math.floorMod(date.toEpochDay(),list.size());
        Map<String,Object> response=new LinkedHashMap<>();response.put("taco",ApiMapper.taco(list.get(index)));response.put("date",date.toString());
        response.put("reason","Seleccion estable para la fecha y el catalogo disponible");return Mono.just(response);
      });
  }
}
