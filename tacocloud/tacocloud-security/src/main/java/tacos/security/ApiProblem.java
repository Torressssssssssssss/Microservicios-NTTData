package tacos.security;
import lombok.Data;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
@Data @JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiProblem {
  private String type,title,detail,instance,code,correlationId;
  private int status;
  private List<Map<String,String>> violations=new ArrayList<>();
  private Object previousTotal,quote;
  public ApiProblem(Map<String,Object> values) {
    type=(String)values.get("type");title=(String)values.get("title");detail=(String)values.get("detail");instance=(String)values.get("instance");code=(String)values.get("code");
    correlationId=Correlation.validOrNew((String)values.get("correlationId"));status=((Number)values.get("status")).intValue();
    violations=(List<Map<String,String>>)values.getOrDefault("violations",Collections.emptyList());previousTotal=values.get("previousTotal");quote=values.get("quote");
  }
}
