package musical;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import musical.config.kafka.KafkaProcessor;

@Service
public class MypageViewHandler {


    @Autowired
    private MypageRepository mypageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1 (@Payload Ordered ordered) {
        try {
            if (ordered.isMe()) {
                // view 객체 생성
                  Mypage mypage = new Mypage();
                // view 객체에 이벤트의 Value 를 set 함
                mypage.setOrderId(ordered.getId());
                mypage.setName(ordered.getName());
                mypage.setGuest(ordered.getGuest());
                mypage.setStatus(ordered.getStatus());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentApproved_then_UPDATE_1(@Payload PaymentApproved paymentApproved) {
        try {
            if (paymentApproved.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(paymentApproved.getOrderId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함                    
                	mypage.setStatus(paymentApproved.getStatus());
                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserveAccepted_then_UPDATE_2(@Payload ReserveAccepted reserveAccepted) {
        try {
            if (reserveAccepted.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(reserveAccepted.getOrderId());
                for(Mypage mypage  : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                    mypage.setReservationId(reserveAccepted.getId());
                	mypage.setStatus(reserveAccepted.getStatus());
                	mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderCanceled_then_UPDATE_3(@Payload OrderCanceled orderCanceled) {
        try {
            if (orderCanceled.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(orderCanceled.getId());
                for(Mypage mypage  : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	mypage.setStatus(orderCanceled.getStatus());
                	mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserveCanceled_then_UPDATE_4(@Payload ReserveCanceled reserveCanceled) {
        try {
            if (reserveCanceled.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(reserveCanceled.getOrderId());
                for(Mypage mypage  : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	mypage.setStatus(reserveCanceled.getStatus());
                	mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentCanceled_then_UPDATE_5(@Payload PaymentCanceled paymentCanceled) {
        try {
            if (paymentCanceled.isMe()) {
                // view 객체 조회
            	List<Mypage> mypageList = mypageRepository.findByOrderId(paymentCanceled.getOrderId());
                for(Mypage mypage  : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	mypage.setStatus(paymentCanceled.getStatus());
                	mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
