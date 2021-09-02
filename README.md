# musical
# 공연 좌석 예매


# Table of contents

- [공연 예매](#---)
  - [서비스 시나리오](#시나리오)
  - [분석/설계](#분석-설계)
  - [구현:](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [API Gateway](#API-게이트웨이-gateway)
    - [CQRS / Meterialized View](#마이페이지)
    - [Saga Pattern / 보상 트랜잭션](#SAGA-CQRS-동작-결과)
  - [운영](#운영)
    - [CI/CD 설정](#cicd-설정)
    - [Self Healing](#Self-Healing)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-배포)
    - [ConfigMap / Secret](#Configmap)

## 서비스 시나리오

공연 좌석 예약 시스템에서 요구하는 기능/비기능 요구사항은 다음과 같습니다. 사용자가 예약과 함께 결제를 진행하고 난 후 공연예약이 완료되는 프로세스입니다. 이 과정에 대해서 고객은 진행 상황을 MyPage를 통해 확인할 수 있습니다.

#### 기능적 요구사항

1. 고객이 원하는 좌석을 선택 하여 예약한다.
1. 고객이 결제 한다.
1. 예약이 신청 되면 예약 신청 내역이 극단에 전달 된다.
1. 극단이 확인 하여 예약을 확정 한다.
1. 고객이 예약 신청을 취소할 수 있다.
1. 예약이 취소 되면 공연 좌석 예약이 취소 된다.
1. 고객이 예약 진행 상황을 조회 한다.
1. 고객이 예약 취소를 하면 예약 정보는 삭제 상태로 업데이트 된다.

#### 비기능적 요구사항

1. 트랜잭션
   1. 결제가 되지 않은 예약건은 아예 공연 예약 신청이 되지 않아야 한다. Sync 호출
1. 장애격리
   1. 좌석관리 기능이 수행 되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다. Async (event-driven), Eventual Consistency
   1. 결제 시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도 한다. Circuit breaker, fallback
1. 성능
   1. 고객이 예약 확인 상태를 마이페이지에서 확인할 수 있어야 한다. CQRS
   


# 분석 설계

## Event Storming

### MSAEz 로 모델링한 이벤트스토밍 결과:


![image](https://user-images.githubusercontent.com/87048550/131690768-6f4025f4-c3c9-48d6-aca1-d779db07970a.png)


1. order의 주문, reservation의 예약과 취소, payment의 결제, customer의 mypage 등은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌(바운디드 컨텍스트)
1. 도메인 서열 분리 
   - Core Domain:  order, reservation
   - Supporting Domain: customer
   - General Domain : payment


### 기능 요구사항을 커버하는지 검증
1. 고객이 원하는 좌석을 선택 하여 예약한다.(OK)
1. 고객이 결제 한다.(OK)
1. 예약이 신청 되면 예약 신청 내역이 극단에 전달 된다.(OK)
1. 극단이 확인 하여 예약을 확정 한다.(OK)
1. 고객이 예약 신청을 취소할 수 있다.(OK)
1. 예약이 취소 되면 공연 좌석 예약이 취소 된다.(OK)
1. 고객이 예약 진행 상황을 조회 한다.(OK)
1. 고객이 예약 취소를 하면 예약 정보는 삭제 상태로 업데이트 된다.(OK)

### 비기능 요구사항을 커버하는지 검증
1. 트랜잭션 
   - 결제가 되지 않은 예약건은 아예 공연 예약 신청이 되지 않아야 한다. Sync 호출 (OK)
   - 주문 완료 시 결제 처리에 대해서는 Request-Response 방식 처리
1. 장애격리
   - 좌석관리 기능이 수행 되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다.(OK)
   - Eventual Consistency 방식으로 트랜잭션 처리함. (PUB/Sub)


## 헥사고날 아키텍처 다이어그램 도출

![image](https://user-images.githubusercontent.com/87048623/129824495-91852bae-0566-4a0c-8bf1-e864e0acc0eb.png)

    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐



# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084 이다)

```
cd musical/order
mvn spring-boot:run

cd musical/reservation
mvn spring-boot:run 

cd musical/payment
mvn spring-boot:run  

cd musical/customer
mvn spring-boot:run 
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 paymentHistory 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다.

```
package musical;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="PaymentHistory_table")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Long cardNo;
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
    public Long getCardNo() {
        return cardNo;
    }

    public void setCardNo(Long cardNo) {
        this.cardNo = cardNo;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package musical;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface PaymentHistoryRepository extends PagingAndSortingRepository<PaymentHistory, Long>{
	PaymentHistory findByOrderId(Long orderId);
}
```

- 적용 후 REST API 의 테스트
```
# order 서비스의 주문처리
http localhost:8081/orders name=Lena seatType=vip cardNo=123 guest=2

# reservation 서비스의 예약처리
http localhost:8082/reservations orderId=3 status="confirmed"

```
![image](https://user-images.githubusercontent.com/87048550/131804747-f0aeae5d-2151-4732-a6ac-9afb17a95832.png)
![image](https://user-images.githubusercontent.com/87048550/131804970-3f406d02-de41-45a5-90ef-22e4ca5897ed.png)


## CQRS

- 고객의 예약정보를 한 눈에 볼 수 있게 mypage를 구현 한다.

```
# 주문 상태 확인
http localhost:8084/mypages/1
```
![image](https://user-images.githubusercontent.com/87048623/129999240-6dafc1c9-0bca-4a43-8cb3-a98071a319fb.png)


## 폴리글랏 퍼시스턴스

폴리그랏 퍼시스턴스 요건을 만족하기 위해 customer 서비스의 DB를 기존 h2를 hsqldb로 변경

![image](https://user-images.githubusercontent.com/87048623/129825717-ba8ae72a-5fab-4f48-a55d-6b8c71e1b939.png)


```
<!--		<dependency>-->
<!--			<groupId>com.h2database</groupId>-->
<!--			<artifactId>h2</artifactId>-->
<!--			<scope>runtime</scope>-->
<!--		</dependency>-->

		<dependency>
			<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<scope>runtime</scope>
		</dependency>



# 변경/재기동 후 주문 처리
http localhost:8081/orders name=Park seatType=aclass

# 저장이 잘 되었는지 조회
http localhost:8084/mypages/1

```

![image](https://user-images.githubusercontent.com/87048623/129999342-39d60491-b7e9-4a8c-a281-4c335deb3e4d.png)
![image](https://user-images.githubusercontent.com/87048623/129999387-8f1ab984-21a1-4d0c-af5c-dd77c9f88f3c.png)



## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(order)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```
# (order) PaymentHistoryService.java

package musical.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="payment", url="${api.payment.url}")
public interface PaymentHistoryService {

    @RequestMapping(method= RequestMethod.POST, path="/paymentHistories")
    public void pay(@RequestBody PaymentHistory paymentHistory);

}
```

- 주문을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
# Order.java (Entity)
  
    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

//        musical.external.PaymentHistory paymentHistory = new musical.external.PaymentHistory();
        PaymentHistory payment = new PaymentHistory();
        System.out.println("this.id() : " + this.id);
        payment.setOrderId(this.id);
        payment.setStatus("Reservation OK");
        // mappings goes here
        OrderApplication.applicationContext.getBean(musical.external.PaymentHistoryService.class)
            .pay(payment);

```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:
```
# 결제 (payment) 서비스를 잠시 내려놓음 (ctrl+c)

#주문처리 #Fail
http localhost:8081/orders name=kim seatType=bclass  

```
![image](https://user-images.githubusercontent.com/87048623/129999620-a66e42bc-0dd6-412c-903e-7175fd590d1d.png)

```
#결제서비스 재기동
cd /home/project/team/musical/payment
mvn spring-boot:run

#주문처리 #Success
http localhost:8081/orders name=kim seatType=bclass 
```
![image](https://user-images.githubusercontent.com/87048623/129999773-53cdeb37-afd3-4e05-bd7a-9aea95d1be21.png)


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 호텔 시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 호텔 시스템의 처리를 위하여 결제주문이 블로킹 되지 않도록 처리한다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
#PaymentHistory.java

package musical;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="PaymentHistory_table")
public class PaymentHistory {

...

    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        paymentApproved.setStatus("Pay Approved!!");
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();
    }

```

- reservation 서비스에서는 결제승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다.

```
# PolicyHandler.java

package musical;

...

@Service
public class PolicyHandler{

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
    
}
```

reservation 시스템은 order/payment와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 호텔 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약 주문을 받는데 문제가 없다.

```
# 예약 서비스 (reservation) 를 잠시 내려놓음 (ctrl+c)

# 주문 처리
http localhost:8081/orders name=Yoo seatType=standard   #Success
```
![image](https://user-images.githubusercontent.com/87048623/129999950-ca9b0a5c-1b87-4eff-af3c-8b7fc4651ad0.png)

```
# 예약상태 확인
http localhost:8084/mypages/4  # 예약상태 안바뀜 확인     
```
![image](https://user-images.githubusercontent.com/87048623/130000011-0ea13f64-81c3-4ccc-bc53-94f97fb8d9ab.png)

```
# reservation 서비스 기동
cd /home/project/team/musical/reservation
mvn spring-boot:run 

# 예약상태 확인
http localhost:8084/mypages/4   # 예약상태가 "Reservation Complete"로 확인
```
![image](https://user-images.githubusercontent.com/87048623/130000098-fb46ed65-62d2-4ae5-93d5-1b92c72c0b3a.png)


## API 게이트웨이(gateway)

API gateway 를 통해 MSA 진입점을 통일 시킨다.

```
# gateway 기동(8080 포트)
cd gateway
mvn spring-boot:run

# API gateway를 통한 예약 주문
http localhost:8080/orders name=jason seatType=vip
```
![image](https://user-images.githubusercontent.com/87048623/130000185-6ecac4c7-1f74-4c2e-8c61-3e1f152f42ac.png)

```
application.yml

server:
  port: 8080

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://localhost:8081
          predicates:
            - Path=/orders/** 
        - id: reservation
          uri: http://localhost:8082
          predicates:
            - Path=/reservations/**,/cancellations/** 
        - id: payment
          uri: http://localhost:8083
          predicates:
            - Path=/paymentHistories/** 
        - id: customer
          uri: http://localhost:8084
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: reservation
          uri: http://reservation:8080
          predicates:
            - Path=/reservations/**,/cancellations/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/paymentHistories/** 
        - id: customer
          uri: http://customer:8080
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true
            
logging:
  level:
    root: debug

server:
  port: 8080

```


# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 이하에 cloudbuild.yml 에 포함되었다.

![image](https://user-images.githubusercontent.com/87048623/130062432-1e7570a7-79ff-4d79-a774-6ac6af262baa.png)



## 동기식 호출 / 서킷 브레이킹 / 장애격리



### 부하테스트

![image](https://user-images.githubusercontent.com/87048623/130191301-54b272bf-6bbc-4287-8ade-e73ae575d0ce.png)

![image](https://user-images.githubusercontent.com/87048623/130191372-33aa0803-80cd-449a-bfbb-029c1aa0585f.png)

![image](https://user-images.githubusercontent.com/87048623/130191449-a48f375a-318f-4df8-bc79-dcfc29be4e40.png)



## ConfigMap

Customer 서비스의 configMap 설정

-configmap.yml

![image](https://user-images.githubusercontent.com/87048623/130187231-4ff38fc9-6958-4a83-868c-edb4849de0f5.png)


- deployment.yml

![image](https://user-images.githubusercontent.com/87048623/130187320-156a1c4e-abb2-445f-b905-bb7bd4304e6e.png)


시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.

kubectl describe pod/customer-55bcc4b5c6-mrswl

![image](https://user-images.githubusercontent.com/87048623/130187675-48fa9866-2a3f-4c6f-8c12-7511040fa928.png)






## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://user01-order-test:8080/paymentHistories'
```
![image](https://user-images.githubusercontent.com/87048623/130171580-b34cd2e2-9166-49b5-b3fd-902a6f212a14.png)


- payment 서비스의 yaml 파일에 Readiness 설정되어 있음

![image](https://user-images.githubusercontent.com/87048623/130171760-6f06612b-2cc0-46e4-a683-e7d557019daf.png)


- CI/CD CodeBuild 를 통한 재배포 

![image](https://user-images.githubusercontent.com/87048623/130171968-d2f0ad7e-d7bd-425a-b20e-4d21b1b20e49.png)


- payment 서비스 재배포 시 새로운 서비스가 완전히 구동되기 전까지 기존 서비스가 구동된다. 

![image](https://user-images.githubusercontent.com/87048623/130170983-ca76b3db-fea8-466c-b3e4-b468d4339836.png)


- 배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

![image](https://user-images.githubusercontent.com/87048623/130171606-38c3f1b2-ec20-40d5-b0b1-92b3ffd1b425.png)


# Liveness

Payment 배포시 yaml 파일내 Liveness 설정되어 있음.

![image](https://user-images.githubusercontent.com/87048623/130187084-089333e5-dcc9-420f-98f9-8542b7b2e0c7.png)



![image](https://user-images.githubusercontent.com/87048623/130185233-aba544d2-3ad5-402b-b3ae-6166b9138c4b.png)


정상 작동 여부 확인을 위해 기존 payment pod를 삭제함.

![image](https://user-images.githubusercontent.com/87048623/130186240-c93e8174-fbe1-4c42-af75-9175fa1e1aef.png)


정상적으로 재생성 되는 것 확인

![image](https://user-images.githubusercontent.com/87048623/130186270-9930963b-c4f0-477b-9526-73b3f0969f17.png)

![image](https://user-images.githubusercontent.com/87048623/130186300-6c9934f3-70e0-41b3-82e9-85abd76135cd.png)
