package tacos;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class UiRoutes implements WebMvcConfigurer {
  @Override public void addViewControllers(ViewControllerRegistry registry) {
    for(String path:new String[]{"/","/ui","/ui/","/ui/{page:[a-z-]+}","/login"})registry.addViewController(path).setViewName("forward:/index.html");
  }
}
