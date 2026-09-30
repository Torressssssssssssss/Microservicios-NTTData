package tacos.security;
import java.util.UUID;
import org.slf4j.MDC;
import reactor.util.context.Context;
public final class Correlation {
  public static final String KEY="correlationId";
  public static final String HEADER="X-Correlation-Id";
  private Correlation() { }
  public static String validOrNew(String value) {
    return value!=null && value.matches("[A-Za-z0-9_-]{1,64}") ? value : UUID.randomUUID().toString();
  }
  public static Context capture() { return Context.of(KEY,validOrNew(MDC.get(KEY))); }
}
