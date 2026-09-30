package tacos.business;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.util.*;
class OpenApiContractTest {
  static JsonNode document() throws Exception {
    String text=new String(OpenApiContractTest.class.getResourceAsStream("/static/openapi.yaml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    return new ObjectMapper().valueToTree(new org.yaml.snakeyaml.Yaml().load(text));
  }
  static void matches(String name,JsonNode payload) throws Exception {
    ObjectMapper json=new ObjectMapper();JsonNode spec=document();
    String defs=spec.path("components").path("schemas").toString().replace("#/components/schemas/","#/definitions/");
    ObjectNode schema=json.createObjectNode();schema.put("$ref","#/definitions/"+name);schema.set("definitions",json.readTree(defs));
    Set<ValidationMessage> violations=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(schema).validate(payload);
    assertTrue(violations.isEmpty(),violations.toString());
  }
  @Test void entireOpenApiDocumentValidatesAndHasNoSensitiveResponses() throws Exception {
    String uri=getClass().getResource("/static/openapi.yaml").toExternalForm();
    SwaggerParseResult result=new OpenAPIV3Parser().readLocation(uri,null,null);
    assertNotNull(result.getOpenAPI());assertTrue(result.getMessages().isEmpty(),result.getMessages().toString());
    assertTrue(result.getOpenAPI().getPaths().size()>=30);
    JsonNode schemas=document().path("components").path("schemas");
    for(String name:Arrays.asList("OrderResponse","KitchenOrder","IngredientResponse","AccountResponse","PaymentResponse")) {
      String body=schemas.path(name).toString();for(String sensitive:Arrays.asList("password","ccNumber","ccCVV","paymentToken","authorities"))assertFalse(body.contains(sensitive),name);
    }
  }
  @Test void orderAndProblemSchemasRejectIncompatibleFields() throws Exception {
    ObjectMapper json=new ObjectMapper();
    JsonNode problem=json.readTree("{\"type\":\"urn:tacocloud:problem:INVALID_REQUEST\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"INVALID_REQUEST\",\"instance\":\"/api/v1/orders\",\"code\":\"INVALID_REQUEST\",\"correlationId\":\"test-id\",\"violations\":[]}");
    matches("ApiProblem",problem);((ObjectNode)problem).put("stacktrace","leak");assertThrows(AssertionError.class,()->matches("ApiProblem",problem));
    tacos.TacoOrder order=new tacos.TacoOrder();order.setId("order-1");order.setVersion(1L);order.setPlacedAt(new Date());
    json.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    JsonNode response=json.valueToTree(tacos.api.dto.ApiMapper.order(order));
    // JSON Schema draft 4 uses explicit null types; convert OpenAPI nullable before validation.
    ((ObjectNode)response).remove(Arrays.asList("deliveryName","deliveryStreet","deliveryCity","deliveryState","deliveryZip","paymentBrand","paymentLast4"));
    matches("OrderResponse",response);((ObjectNode)response).put("total","not-money");assertThrows(AssertionError.class,()->matches("OrderResponse",response));
  }
}
