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
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserveAccepted_(@Payload ReserveAccepted reserveAccepted){

        if(reserveAccepted.isMe()){
            System.out.println("##### Kako Send (Reservation Accepted  : " + reserveAccepted.toJson());
            System.out.println("Config Value : " + System.getenv("DATA1"));
            System.out.println("Secret Value : " + System.getenv("PASSWD"));
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCanceled_(@Payload PaymentCanceled paymentCanceled){

        if(paymentCanceled.isMe()){
            System.out.println("##### listener  : " + paymentCanceled.toJson());
        }
    }

}
