package musical;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="Mypage_table")
public class Mypage {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private Long orderId;        
        private Long reservationId;        
        private String name;
        private Integer guest;
        private String status;


        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
        public Long getReservationId() {
            return reservationId;
        }

        public void setReservationId(Long reservationId) {
            this.reservationId = reservationId;
        }        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
        public Integer getGuest() {
            return guest;
        }

        public void setGuest(Integer guest) {
            this.guest = guest;
        }
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

}
