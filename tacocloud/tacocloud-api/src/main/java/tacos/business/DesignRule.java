package tacos.business;
import java.util.List;
import tacos.Ingredient;
import tacos.api.dto.Requests.Design;
public interface DesignRule {
  @lombok.Value class Violation { String code; String message; }
  List<Violation> violations(Design design,List<Ingredient> ingredients);
}
