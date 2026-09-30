package tacos.kitchen;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class KitchenUI {
  @GetMapping("/kitchen") public String kitchen() { return "redirect:/kitchen.html"; }
}
