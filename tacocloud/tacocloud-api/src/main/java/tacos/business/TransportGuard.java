package tacos.business;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.SmartInitializingSingleton;
import tacos.messaging.OrderMessagingService;
@Component
public class TransportGuard implements SmartInitializingSingleton {
  private final List<OrderMessagingService> adapters; private final Environment env;
  public TransportGuard(List<OrderMessagingService> adapters,Environment env) { this.adapters=adapters;this.env=env; }
  public void afterSingletonsInstantiated() {
    String transport=env.getProperty("tacocloud.messaging.transport");
    if(transport==null || !java.util.Arrays.asList("noop","jms","rabbit","kafka").contains(transport) || adapters.size()!=1)
      throw new IllegalStateException("Configure exactly one tacocloud.messaging.transport: noop|jms|rabbit|kafka");
    if("noop".equals(transport) && !env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev","test")))
      throw new IllegalStateException("noop requires dev or test profile");
  }
}
