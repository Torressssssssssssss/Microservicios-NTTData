package tacos.web.api;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class ApiProblemHandler {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<tacos.security.ApiProblem> handle(Exception e, HttpServletRequest request) {
    int status=500; String code="INTERNAL_ERROR";
    List<Map<String,String>> violations=new ArrayList<>();
    if(e instanceof ResponseStatusException) { status=((ResponseStatusException)e).getRawStatusCode(); code=e instanceof ApiException ? ((ApiException)e).getCode() : "HTTP_"+status; }
    else if(e instanceof DuplicateKeyException) { status=409; code="DUPLICATE_RESOURCE"; }
    else if(e instanceof org.springframework.dao.TransientDataAccessException) { status=409; code="CONCURRENT_UPDATE"; }
    else if(e instanceof org.springframework.web.bind.MissingRequestHeaderException) { status=400; code="MISSING_HEADER"; }
    else if(e instanceof OptimisticLockingFailureException) { status=409; code="STALE_VERSION"; }
    else if(e instanceof AccessDeniedException) { status=403; code="ACCESS_DENIED"; }
    else if(e instanceof MethodArgumentNotValidException || e instanceof BindException) {
      status=400; code="VALIDATION_ERROR";
      org.springframework.validation.BindingResult result=e instanceof MethodArgumentNotValidException ? ((MethodArgumentNotValidException)e).getBindingResult() : ((BindException)e).getBindingResult();
      result.getFieldErrors().forEach(error -> { Map<String,String> v=new LinkedHashMap<>(); v.put("field",error.getField()); v.put("reason",error.getDefaultMessage()); violations.add(v); });
    } else if(e instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {status=405;code="METHOD_NOT_ALLOWED";}
    else if(e instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {status=415;code="UNSUPPORTED_MEDIA_TYPE";}
    else if(e instanceof org.springframework.web.bind.MissingServletRequestParameterException) {status=400;code="MISSING_PARAMETER";}
    else if(e instanceof HttpMessageNotReadableException || e instanceof IllegalArgumentException
        || e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) { status=400; code="INVALID_REQUEST"; }
    if(e instanceof tacos.business.DesignValidationException) {
      ((tacos.business.DesignValidationException)e).getViolations().forEach(rule->{Map<String,String> v=new LinkedHashMap<>();v.put("field","ingredientIds");v.put("code",rule.getCode());v.put("reason",rule.getMessage());violations.add(v);});
    }
    Map<String,Object> body=new LinkedHashMap<>(); body.put("type","urn:tacocloud:problem:"+code);
    body.put("title",HttpStatus.valueOf(status).getReasonPhrase()); body.put("status",status);
    body.put("detail",code); body.put("instance",request.getRequestURI()); body.put("code",code); body.put("correlationId",request.getAttribute(tacos.security.Correlation.KEY)); body.put("violations",violations);
    if(e instanceof tacos.business.PriceChangedException) {
      body.put("previousTotal",((tacos.business.PriceChangedException)e).previousTotal);body.put("quote",((tacos.business.PriceChangedException)e).quote);
    }
    return ResponseEntity.status(status).contentType(MediaType.valueOf("application/problem+json")).body(new tacos.security.ApiProblem(body));
  }
}
