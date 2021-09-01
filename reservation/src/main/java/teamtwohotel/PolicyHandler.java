package musical;

import musical.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
	@Autowired
	private CancellationRepository cancellationRepository;
	
	@Autowired
	private ReservationRepository reservationRepository;
	
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_(@Payload PaymentApproved paymentApproved){


        if(paymentApproved.isMe()){
            System.out.println("##### listener  : " + paymentApproved.toJson());
            Reservation reservation = new Reservation();
            reservation.setStatus("Reservation Complete");
            reservation.setOrderId(paymentApproved.getOrderId());
            reservationRepository.save(reservation);
            
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCanceled_(@Payload OrderCanceled orderCanceled){

        if(orderCanceled.isMe()){
            System.out.println("##### reservation listener  : " + orderCanceled.toJson());
            
//            System.out.println("ID VALUE : " + orderCanceled.getId());
//		    Cancellation cancellation = cancellationRepository.findByOrderId(orderCanceled.getId());
		    Cancellation cancellation = new Cancellation();
		    
	        cancellation.setOrderId(orderCanceled.getId());    	    
		    cancellation.setStatus("Reservation Cancelled!");            
		    cancellationRepository.save(cancellation);
        }
    }

}
