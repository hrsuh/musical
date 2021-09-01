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
    PaymentHistoryRepository paymentRepository;
    
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserveCanceled_(@Payload ReserveCanceled reserveCanceled){

        if(reserveCanceled.isMe()){
            System.out.println("##### listener  : " + reserveCanceled.toJson());
            
            PaymentHistory payment = paymentRepository.findByOrderId(reserveCanceled.getOrderId());
            
            payment.setStatus("Your reservation is canceled!");

            paymentRepository.save(payment);
        }
    }

}
