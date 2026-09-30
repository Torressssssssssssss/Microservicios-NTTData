package tacos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class TacoOrder implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  private Date placedAt = new Date();

  private User user;

  // Las ordenes previas sin estado se consideran CREATED en el servicio.
  private Status status = Status.CREATED;

  public enum Status { CREATED, ACCEPTED, PREPARING, READY, OUT_FOR_DELIVERY, DELIVERED, CANCELLED }


  private String deliveryName;

  private String deliveryStreet;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryZip;

  private String paymentMethodId;
  private String paymentBrand;
  private String paymentLast4;
  private java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
  private java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
  private java.math.BigDecimal total = java.math.BigDecimal.ZERO;
  private String currency = "MXN";
  private String couponCode;
  private String cookId;
  private String stationId;
  private int estimatedPrepMinutes;
  @org.springframework.data.annotation.Version private Long version;
  private List<OrderLine> items = new ArrayList<>();
  private List<StatusChange> history = new ArrayList<>();


  private List<Taco> tacos = new ArrayList<>();

  public void addTaco(Taco design) {
    this.tacos.add(design);
}

}
