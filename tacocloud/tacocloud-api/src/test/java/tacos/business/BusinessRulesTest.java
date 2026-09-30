package tacos.business;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.math.*;
import java.time.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import tacos.*;
import tacos.data.IngredientRepository;
import tacos.api.dto.*;
import tacos.web.api.ApiException;
class BusinessRulesTest {
  IngredientRepository repo;DesignService designs;Ingredient wrap,veg;Requests.Design design;
  Clock clock=Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"),ZoneId.of("America/Mexico_City"));
  @BeforeEach void setup() {
    repo=mock(IngredientRepository.class);wrap=new Ingredient("W","Wrap",Ingredient.Type.WRAP);veg=new Ingredient("V","Vegetable",Ingredient.Type.VEGGIES);
    wrap.setUnitPrice(new BigDecimal("1.15"));veg.setUnitPrice(new BigDecimal("2.35"));
    wrap.setDietaryTags(EnumSet.allOf(Ingredient.DietaryTag.class));veg.setDietaryTags(EnumSet.allOf(Ingredient.DietaryTag.class));
    when(repo.findById("W")).thenReturn(Mono.just(wrap));when(repo.findById("V")).thenReturn(Mono.just(veg));
    DesignRules rules=new DesignRules();designs=new DesignService(repo,Arrays.asList(rules.ingredientCount(),rules.oneWrap(),rules.noDuplicates(),rules.availableIngredients(),rules.hotNeedsDrink(true,4),rules.veganPromise(true)));
    design=new Requests.Design();design.setName("Test taco");design.setIngredientIds(Arrays.asList("W","V"));
  }
  Requests.OrderCreateRequest request(int quantity) { Requests.Item item=new Requests.Item();item.setTaco(design);item.setQuantity(quantity);Requests.OrderCreateRequest r=new Requests.OrderCreateRequest();r.setItems(Arrays.asList(item));return r; }
  @Test void pricesUseDecimalQuantitiesAndHistoricalSnapshot() {
    PricingService pricing=new PricingService(designs,new CouponService(new BusinessConfig.Coupons(),clock),50);
    StepVerifier.create(pricing.quote(request(2))).assertNext(order->{assertEquals(new BigDecimal("7.00"),order.getTotal());wrap.setUnitPrice(new BigDecimal("99.00"));assertEquals(new BigDecimal("3.50"),order.getItems().get(0).getUnitPriceAtPurchase());}).verifyComplete();
  }
  @Test void quantityBoundsFailBeforeIngredientLookup() {
    PricingService pricing=new PricingService(designs,new CouponService(new BusinessConfig.Coupons(),clock),10);
    for(int n:new int[]{0,-1,11})StepVerifier.create(pricing.quote(request(n))).expectError(ApiException.class).verify();verify(repo,never()).findById(anyString());
  }
  @Test void classificationIntersectsTagsUnionsAllergensAndUsesMaximumSpice() {
    veg.setDietaryTags(EnumSet.of(Ingredient.DietaryTag.VEGETARIAN));wrap.setAllergens(EnumSet.of(Ingredient.Allergen.GLUTEN));veg.setAllergens(EnumSet.of(Ingredient.Allergen.MILK));veg.setSpiceLevel(3);
    StepVerifier.create(designs.resolve(design)).assertNext(t->{assertFalse(t.getDietaryTags().contains(Ingredient.DietaryTag.VEGAN));assertEquals(2,t.getAllergens().size());assertEquals(3,t.getSpiceLevel());}).verifyComplete();
  }
  @Test void rulesAccumulateAndCanBeExtended() {
    design.setIngredientIds(Arrays.asList("V","V"));veg.setAvailable(false);
    assertTrue(designs.violations(design,Arrays.asList(veg,veg)).containsAll(Arrays.asList("EXACTLY_ONE_WRAP","DUPLICATE_INGREDIENT","INGREDIENT_UNAVAILABLE")));
    DesignService extended=new DesignService(repo,Arrays.asList((d,i)->Arrays.asList(new DesignRule.Violation("EXTRA_RULE","Extra rule rejected"))));
    StepVerifier.create(extended.resolve(design)).expectErrorMatches(e->e.getMessage().contains("EXTRA_RULE")).verify();
  }
  @Test void configuredHotAndVeganRulesApply() {
    veg.setSpiceLevel(5);design.setVegan(true);veg.setDietaryTags(Collections.emptySet());
    assertTrue(designs.violations(design,Arrays.asList(wrap,veg)).containsAll(Arrays.asList("HOT_REQUIRES_BEVERAGE","VEGAN_PROMISE")));
  }
  @Test void couponsHaveExclusiveExpiryAndCapAtZero() {
    BusinessConfig.Coupons p=new BusinessConfig.Coupons();BusinessConfig.Coupon c=new BusinessConfig.Coupon();c.setType(BusinessConfig.Coupon.Type.PERCENTAGE);c.setValue(new BigDecimal("50"));
    c.setStartsAt(clock.instant());c.setExpiresAt(clock.instant().plusSeconds(60));c.setMaximum(new BigDecimal("3.00"));p.getCodes().put("CLASS",c);
    CouponService service=new CouponService(p,clock);assertEquals(new BigDecimal("3.00"),service.discount(" class ",new BigDecimal("20")));
    c.setType(BusinessConfig.Coupon.Type.FIXED);c.setValue(new BigDecimal("100"));c.setMaximum(null);assertEquals(new BigDecimal("20.00"),service.discount("CLASS",new BigDecimal("20")));
    c.setExpiresAt(clock.instant());assertThrows(ApiException.class,()->service.discount("CLASS",new BigDecimal("20")));
    assertThrows(ApiException.class,()->service.discount("UNKNOWN",new BigDecimal("20")));
  }
  @Test void couponMinimumAndStartAreEnforced() {
    BusinessConfig.Coupons p=new BusinessConfig.Coupons();BusinessConfig.Coupon c=new BusinessConfig.Coupon();c.setType(BusinessConfig.Coupon.Type.FIXED);c.setValue(BigDecimal.ONE);
    c.setStartsAt(clock.instant().plusSeconds(1));c.setExpiresAt(clock.instant().plusSeconds(60));p.getCodes().put("X",c);CouponService service=new CouponService(p,clock);
    assertThrows(ApiException.class,()->service.discount("X",BigDecimal.TEN));c.setStartsAt(clock.instant());c.setMinimum(new BigDecimal("11"));assertThrows(ApiException.class,()->service.discount("X",BigDecimal.TEN));
  }
  @Test void fakePaymentAcceptsSyntheticDataOnlyAndHidesToken() throws Exception {
    FakePaymentGateway gateway=new FakePaymentGateway();StepVerifier.create(gateway.tokenize("test_4242","u")).assertNext(p->{
      assertEquals("4242",p.getLast4());assertNotNull(p.getPaymentToken());
      try{assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(p).contains("test_token_"));}catch(Exception e){throw new RuntimeException(e);}
    }).verifyComplete();StepVerifier.create(gateway.tokenize("4111111111111111","u")).expectError(ApiException.class).verify();
  }
  @Test void entireWorkflowMatrixIsExplicit() {
    Set<String> allowed=new HashSet<>(Arrays.asList("CREATED:ACCEPTED","CREATED:CANCELLED","ACCEPTED:PREPARING","ACCEPTED:CANCELLED","PREPARING:READY","READY:OUT_FOR_DELIVERY","OUT_FOR_DELIVERY:DELIVERED"));
    for(TacoOrder.Status from:TacoOrder.Status.values())for(TacoOrder.Status to:TacoOrder.Status.values())assertEquals(allowed.contains(from+":"+to),WorkflowService.allowed(from,to),from+":"+to);
  }
  @Test void moneyFieldsAndIdentityCannotBeAssigned() throws Exception {
    com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    for(String field:Arrays.asList("total","subtotal","userId","status","placedAt","id"))assertThrows(Exception.class,()->json.readValue("{\""+field+"\":0}",Requests.OrderCreateRequest.class));
  }
}
