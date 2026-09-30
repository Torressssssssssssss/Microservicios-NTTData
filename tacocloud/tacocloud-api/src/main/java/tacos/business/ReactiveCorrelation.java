package tacos.business;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.*;
import reactor.util.context.Context;
import tacos.security.Correlation;
@Aspect @Component
public class ReactiveCorrelation {
  @Around("@within(org.springframework.web.bind.annotation.RestController)")
  public Object propagate(ProceedingJoinPoint invocation) throws Throwable {
    Context context=Correlation.capture();
    Object result=invocation.proceed();
    if(result instanceof Mono)return ((Mono<?>)result).contextWrite(context);
    if(result instanceof Flux)return ((Flux<?>)result).contextWrite(context);
    return result;
  }
}
