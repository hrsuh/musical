# musical
# 공연 좌석 예약

# Table of contents

- [좌석 예약](#---)
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
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [무정지 재배포](#무정지-배포)
    - [ConfigMap / Secret](#Configmap)
    - [Self Healing](#Self-Healing)

## 서비스 시나리오

좌석 예약 시스템에서 요구하는 기능/비기능 요구사항은 다음과 같습니다. 사용자가 예매과 함께 결제를 진행하고 난 후 공연 좌석 예약이 완료되는 프로세스입니다. 이 과정에 대해서 고객은 진행 상황을 MyPage를 통해 확인할 수 있습니다.

#### 기능적 요구사항

1. 고객이 원하는 좌석을 선택 하여 예매한다.
1. 고객이 결제 한다.
1. 결제가 완료되면 예약을 확정 한다.
1. 고객이 예매를 취소할 수 있다.
1. 예매가 취소 되면 공연 좌석 예약이 취소 된다.
1. 고객이 예약 진행 상황을 조회 한다.
1. 고객이 예매 취소를 하면 예약 정보는 삭제 상태로 업데이트 된다.

#### 비기능적 요구사항

1. 트랜잭션
   1. 결제가 되지 않은 예매 건은 아예 좌석 예약 신청이 되지 않아야 한다. Sync 호출
1. 장애격리
   1. 좌석 시스템 기능이 수행 되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다. Async (event-driven), Eventual Consistency
   1. 결제 시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도 한다. Circuit breaker, fallback
1. 성능
   1. 고객이 예약 확인 상태를 마이페이지에서 확인할 수 있어야 한다. CQRS
   
# 분석 설계

## Event Storming

### MSAEz 로 모델링한 이벤트스토밍 결과:

![image](https://user-images.githubusercontent.com/87048550/131690768-6f4025f4-c3c9-48d6-aca1-d779db07970a.png)

1. order의 예매, reservation의 예약과 취소, payment의 결제, customer의 mypage 등은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌(바운디드 컨텍스트)
1. 도메인 서열 분리 
   - Core Domain:  order, reservation
   - Supporting Domain: customer
   - General Domain : payment

### 기능 요구사항을 커버하는지 검증
1. 고객이 원하는 좌석을 선택 하여 예매한다.(OK)
1. 고객이 결제 한다.(OK)
1. 결제가 완료되면 예약을 확정 한다.(OK)
1. 고객이 예매를 취소할 수 있다.(OK)
1. 예매가 취소 되면 공연 좌석 예약이 취소 된다.(OK)
1. 고객이 예약 진행 상황을 조회 한다.(OK)
1. 고객이 예매 취소를 하면 예약 정보는 삭제 상태로 업데이트 된다.(OK)

### 비기능 요구사항을 커버하는지 검증
1. 트랜잭션 
   - 결제가 되지 않은 예매 건은 아예 좌석 예약 신청이 되지 않아야 한다. Sync 호출(OK)
   - 예매 완료 시 결제 처리에 대해서는 Request-Response 방식 처리
1. 장애격리
   - 좌석 관리 기능이 수행 되지 않더라도 예매는 365일 24시간 받을 수 있어야 한다.(OK)
   - Eventual Consistency 방식으로 트랜잭션 처리함. (PUB/Sub)

# 구현
분석/설계 단계에서 도출된 모델링에 맞춰 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084 이다)

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

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다.

```
package musical;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import musical.external.PaymentHistory;

import java.util.List;

@Entity
@Table(name="Order_table")
public class Order {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String seatType;
    private Long cardNo;
    private Integer guest;
    private String name;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        PaymentHistory payment = new PaymentHistory();
        System.out.println("this.id() : " + this.id);
        payment.setOrderId(this.id);
        payment.setStatus("Reservation OK");
        OrderApplication.applicationContext.getBean(musical.external.PaymentHistoryService.class)
            .pay(payment);

    }

    @PostUpdate
    public void onPostUpdate(){
    	System.out.println("Order Cancel  !!");
        OrderCanceled orderCanceled = new OrderCanceled();
        BeanUtils.copyProperties(this, orderCanceled);
        orderCanceled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getSeatType() {
        return seatType;
    }

    public void setSeatType(String seatType) {
        this.seatType = seatType;
    }
    public Long getCardNo() {
        return cardNo;
    }

    public void setCardNo(Long cardNo) {
        this.cardNo = cardNo;
    }
    public Integer getGuest() {
        return guest;
    }

    public void setGuest(Integer guest) {
        this.guest = guest;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다.

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
http localhost:8084/mypages/7
```

![image](https://user-images.githubusercontent.com/87048550/131806598-c15597d7-3472-44cf-b267-6c9c61ec0f89.png)

## 폴리글랏 퍼시스턴스

폴리그랏 퍼시스턴스 요건을 만족하기 위해 customer 서비스의 DB를 기존 h2를 hsqldb로 변경

![image](https://user-images.githubusercontent.com/87048550/131937320-999ab6ce-e984-4816-ad5d-4266f57e2619.png)


```
# 변경/재기동 후 주문 처리
http localhost:8081/orders name=Hyun seatType=aclass cardNo=456 guest=1

# 저장이 잘 되었는지 조회
http localhost:8084/mypages/1

```

![image](https://user-images.githubusercontent.com/87048550/131807519-2969f81d-649a-4ae2-ad7f-52d3550b6464.png)
![image](https://user-images.githubusercontent.com/87048550/131807647-c361bcc1-74f1-48b4-9860-524e57616404.png)


## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 예매(order)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
- 
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

        PaymentHistory payment = new PaymentHistory();
        System.out.println("this.id() : " + this.id);
        payment.setOrderId(this.id);
        payment.setStatus("Reservation OK");
 
        OrderApplication.applicationContext.getBean(musical.external.PaymentHistoryService.class)
            .pay(payment);

```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인.

```
# 결제 (payment) 서비스를 잠시 내려놓음

#주문처리
http localhost:8081/orders name=Ho seatType=bclass cardNo=789 guest=4

```

![image](https://user-images.githubusercontent.com/87048550/131830721-0c3b16ec-378a-44bd-a59d-fd5421441cdd.png)

```
#결제서비스 재기동
cd musical/payment
mvn spring-boot:run

#주문처리 
http localhost:8081/orders name=Ho seatType=bclass cardNo=789 guest=4
```

![image](https://user-images.githubusercontent.com/87048550/131830915-0298d335-6db2-48fa-8b18-d3503f0f9f16.png)

## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 좌석 시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 결제주문이 블로킹 되지 않도록 처리한다.
 
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

- reservation 서비스에서는 결제 승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다.

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

reservation 시스템은 order/payment와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 좌석 예약 시스템이 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다.

```
# 예약 서비스 (reservation) 를 잠시 내려놓음

# 주문 처리
http localhost:8081/orders name=Jin seatType=cclass cardNo=135 guest=1
```

![image](https://user-images.githubusercontent.com/87048550/131832179-cc2b1bb7-6cd4-40c0-8554-8dd2408f0609.png)

```
# 예약상태 확인
http localhost:8084/mypages/2    
```
![image](https://user-images.githubusercontent.com/87048550/131938688-47d2b363-3f50-4030-8e42-79f399456532.png)

```
# reservation 서비스 기동
cd musical/reservation
mvn spring-boot:run 

# 예약상태 확인
http localhost:8084/mypages/2  
```
![image](https://user-images.githubusercontent.com/87048550/131938758-5baa606f-59ce-4561-98db-5d981771b47a.png)

## API 게이트웨이(gateway)

API gateway 를 통해 MSA 진입점을 통일 시킨다.

```
# gateway 기동(8080 포트)
cd gateway
mvn spring-boot:run

# API gateway를 통한 예약 주문
http localhost:8080/orders name=Ryung seatType=vvip cardNo=987 guest=2 
```

![image](https://user-images.githubusercontent.com/87048550/131833504-55db412d-601a-4926-b842-e25891afb9f1.png)

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

각 구현체들은 각자의 source repository 에 구성되었고, Deploy 방식으로 Docker 빌드 및 이미지 Push, deployment.yml, service.yml 통해 배포한다.

```
# ECR 생성 및 이미지 Push
docker build -t 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-gateway:v1 .
docker push 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-gateway:v1
```

```
# (gateway) deployment.yml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  labels:
    app: gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
        - name: gateway
          image: 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-gateway:v1
          imagePullPolicy: Always
          ports:
          - containerPort: 8080
```
```
# (gateway) service.yml

apiVersion: v1
kind: Service
metadata:
  name: gateway
  labels:
    app: gateway 
spec:
  type: LoadBalancer
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: gateway 
    
```

```
# deploymeny.yml, service.yml 파일 실행
kubectl apply -f deployment.yml
kubectl apply -f service.yml

# 배포 상태 확인
kubectl get all

```
![image](https://user-images.githubusercontent.com/87048550/131838248-41cadb3f-55a2-4b83-9bc6-1cb13676920a.png)

## 동기식 호출 / 서킷 브레이킹 / 장애격리

```
# 서킷 브레이킹 적용 전 siege 부하테스트
siege -c10 -t10s -v --content-type "application/json" 'http://order:8080/orders POST {"name": "Tom", "seatType": "vip"}'

```
![image](https://user-images.githubusercontent.com/87048550/131883301-fb9cadce-f40b-45ab-a6a5-9cae311005d1.png)

```
# 서킷 브레이킹 적용 
# (order) destinationRule.yml 생성

apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: order
spec:
  host: order
  trafficPolicy:
    connectionPool:
      http:
        http1MaxPendingRequests: 1
        maxRequestsPerConnection: 1

```

```
# destinationRule.yml 배포
kubectl apply -f destinationRule.yml

# 서킷 브레이킹 적용 후 siege 부하테스트
siege -c10 -t10s -v --content-type "application/json" 'http://order:8080/orders POST {"name": "Tom", "seatType": "vip"}'

```
![image](https://user-images.githubusercontent.com/87048550/131883782-0e4651a8-c523-435b-aa06-220879f79288.png)


## ConfigMap

Customer 서비스의 configMap 설정

```
# (customer) configmap.yml

apiVersion: v1
kind: ConfigMap
metadata:
  name: musicalcm
data:
  text1: musical_Customer
  text2: Welcomes You
  company: musical_Customer Technology Pct. Ltd.

```

```
# (customer) deployment.yml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: customer
  labels:
    app: customer
spec:
  replicas: 1
  selector:
    matchLabels:
      app: customer
  template:
    metadata:
      labels:
        app: customer
    spec:
      containers:
        - name: customer
          image: 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-customer:v1
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: DATA1
              valueFrom:
                configMapKeyRef:
                  name: musicalcm
                  key: text1

```

시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.

```
kubectl describe pod/customer-8498ff5687-5tt7t
```

![image](https://user-images.githubusercontent.com/87048550/131842676-597e763d-1a12-4e6a-8452-2b4bc3c3b4d9.png)

## 무정지 재배포 (READINESS)

```
# seige 로 배포작업 직전에 워크로드를 모니터링 함.

siege -c30 -t30s -v http://customer:8080/mypages

# customer (mypages) 재배포
kubectl aoply -f delployment.yml

```

![image](https://user-images.githubusercontent.com/87048550/131870905-2eb28f75-6b9c-4aff-811c-775db76ef0d4.png)

```
# (customer) deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: customer
  labels:
    app: customer
spec:
  replicas: 1
  selector:
    matchLabels: 
      app: customer
  template:
    metadata:
      labels:
        app: customer
    spec:
      containers:
        - name: customer
          image: 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-customer:v1
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: DATA1
              valueFrom:
                configMapKeyRef:
                  name: musicalcm
                  key: text2
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10

```

```
# seige 로 배포작업 직전에 워크로드를 모니터링 함.

siege -c30 -t30s -v http://customer:8080/mypages

# customer (mypages) 재배포
kubectl aoply -f delployment.yml

```

- 배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

![image](https://user-images.githubusercontent.com/87048550/131874156-6377cd54-db09-4dc2-a3c4-2ae3ef3f70dc.png)

# Liveness

(Order) deployment.yml 파일 내 Liveness 설정되어 있음.

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  labels:
    app: order
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
        - name: order
          image: 052937454741.dkr.ecr.ap-northeast-1.amazonaws.com/user07-order:v1
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          args:
          - /bin/sh
          - -c
          - touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600
          livenessProbe:
            exec:
              command:
              - cat
              - /tmp/healthy
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

가동 중인 Pod 확인

```
kubectl get pod
```
![image](https://user-images.githubusercontent.com/87048550/131852472-34329577-63d2-4965-9871-1a06f955ee49.png)

정상 작동 여부 확인을 위해 deployment2.yml 재배포.

```
kubectl apply -f deployment2.yml
```

![image](https://user-images.githubusercontent.com/87048550/131852666-f1e9300a-a5f5-4cfd-9184-a8298df62f40.png)

30 초 후 pod 상태 Unhealthy > Killing 확인

```
kubectl describe pod/order-748c8666bf-t6ts7
```

![image](https://user-images.githubusercontent.com/87048550/131853447-760f37e8-aeb7-4bdd-8a5f-ef1edffd9b0e.png)

가동 중인 Pod 재 확인 : Restart 1 확인

```
kubectl get pod
```
![image](https://user-images.githubusercontent.com/87048550/131853659-3b233361-7ac8-4715-824c-4ff01eaab72f.png)
