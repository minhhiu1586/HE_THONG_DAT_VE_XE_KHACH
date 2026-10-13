package com.datvexe.email;

import com.datvexe.domain.EmailOutbox;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EmailDispatcherTest {
    EntityManagerFactory factory;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    @BeforeEach void setup() {
        factory = Persistence.createEntityManagerFactory("datvexe",Map.of("jakarta.persistence.jdbc.driver","org.h2.Driver","jakarta.persistence.jdbc.url","jdbc:h2:mem:email_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","jakarta.persistence.jdbc.user","sa","jakarta.persistence.jdbc.password","","hibernate.hbm2ddl.auto","create-drop"));
        try(EntityManager em=factory.createEntityManager()) { em.getTransaction().begin(); EmailOutbox mail=new EmailOutbox();mail.maDon="ORDER";mail.recipient="buyer@example.com";mail.recipientName="Buyer";mail.subject="Paid";mail.htmlContent="<p>Paid</p>";mail.textContent="Paid";mail.createdAt=LocalDateTime.of(2030,1,1,7,0);mail.nextAttemptAt=mail.createdAt;em.persist(mail);em.getTransaction().commit(); }
    }
    @AfterEach void cleanup() { factory.close(); }
    String status() { try(EntityManager em=factory.createEntityManager()) {return em.find(EmailOutbox.class,"ORDER").status;} }
    @Test void acceptedReceiptIsNotResentAfterWorkerRecreation() {
        AtomicInteger sends=new AtomicInteger();EmailDispatcher.Sender sender=m->{sends.incrementAndGet();return "provider-id";};
        new EmailDispatcher(factory,sender,CLOCK).runOnce();new EmailDispatcher(factory,sender,Clock.offset(CLOCK,Duration.ofHours(1))).runOnce();
        assertEquals("SENT",status());assertEquals(1,sends.get());
    }
    @Test void throttlingRetriesLaterAndUncertainDeliveryDoesNotResendAutomatically() {
        AtomicInteger sends=new AtomicInteger();EmailDispatcher.Sender sender=m->{if(sends.incrementAndGet()==1)throw new BrevoClient.SendFailure("BREVO_HTTP_429",true,false);return "provider-id";};
        var dispatcher=new EmailDispatcher(factory,sender,CLOCK);dispatcher.runOnce();dispatcher.runOnce();assertEquals(1,sends.get());assertEquals("PENDING",status());
        new EmailDispatcher(factory,sender,Clock.offset(CLOCK,Duration.ofMinutes(1))).runOnce();assertEquals(2,sends.get());assertEquals("SENT",status());
    }
    @Test void unknownNetworkResultIsVisibleAndNeverAutoResent() {
        AtomicInteger sends=new AtomicInteger();EmailDispatcher.Sender sender=m->{sends.incrementAndGet();throw new BrevoClient.SendFailure("BREVO_TIMEOUT",false,true);};
        new EmailDispatcher(factory,sender,CLOCK).runOnce();new EmailDispatcher(factory,sender,Clock.offset(CLOCK,Duration.ofHours(1))).runOnce();assertEquals(1,sends.get());assertEquals("UNKNOWN",status());
    }
}
