package tacos.business;
import java.util.List;
import tacos.web.api.ApiException;
public class DesignValidationException extends ApiException {
  private final List<DesignRule.Violation> violations;
  public DesignValidationException(List<DesignRule.Violation> violations){super(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_DESIGN");this.violations=violations;}
  @Override public String getMessage(){return super.getMessage()+": "+String.join(",",getCodes());}
  public List<String> getCodes(){return violations.stream().map(DesignRule.Violation::getCode).collect(java.util.stream.Collectors.toList());}
  public List<DesignRule.Violation> getViolations(){return violations;}
  public static String message(String code) {
    switch(code) {
      case "INGREDIENT_COUNT":return "Choose between 2 and 12 ingredients";
      case "EXACTLY_ONE_WRAP":return "Choose exactly one wrap";
      case "DUPLICATE_INGREDIENT":return "Choose each ingredient only once";
      case "INGREDIENT_UNAVAILABLE":return "Every ingredient must be available";
      case "HOT_REQUIRES_BEVERAGE":return "Add a beverage for this spice level";
      case "VEGAN_PROMISE":return "Every ingredient must be vegan";
      default:return "Design rule rejected: "+code;
    }
  }
}
