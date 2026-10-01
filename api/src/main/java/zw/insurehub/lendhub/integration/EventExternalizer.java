package zw.insurehub.lendhub.integration;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import zw.insurehub.lendhub.loans.LoanEvents;

/**
 * Publishes loan events to the group bus (ADR-0004). Each listener is registered in Spring Modulith's event
 * publication registry inside the business transaction and only marked complete when the send succeeds, so this is
 * a transactional outbox: events are never lost, and incomplete ones are resubmitted on restart.
 * <p>
 * Wire format matches InsureHub Integrations' {@code MessageEnvelope}: {messageId, type, payload (JSON string),
 * occurredAt, traceParent}; routing keys follow the group event catalogue.
 */
@Component
class EventExternalizer {

    record Envelope(UUID messageId, String type, String payload, Instant occurredAt, String traceParent) {
    }

    record Sent(String routingKey, Envelope envelope) {
    }

    private static final Logger log = LoggerFactory.getLogger(EventExternalizer.class);

    private final ObjectProvider<RabbitTemplate> rabbit;
    private final IntegrationProperties props;
    private final ObjectMapper json;
    private final Deque<Sent> recent = new ArrayDeque<>();

    EventExternalizer(ObjectProvider<RabbitTemplate> rabbit, IntegrationProperties props, ObjectMapper json) {
        this.rabbit = rabbit;
        this.props = props;
        this.json = json;
    }

    @ApplicationModuleListener
    void on(LoanEvents.LoanDisbursed e) {
        var data = new LinkedHashMap<String, Object>();
        data.put("loanNumber", e.loanNumber());
        data.put("customerRef", e.customerRef());
        data.put("msisdn", e.msisdn());
        data.put("currency", e.principal().currency().name());
        data.put("principal", e.principal().amount());
        data.put("netDisbursed", e.netDisbursed().amount());
        data.put("firstDueDate", e.firstDueDate().toString());
        data.put("firstInstalment", e.firstInstalment().amount());
        publish("loan.disbursed", "LoanDisbursed", data);
    }

    @ApplicationModuleListener
    void on(LoanEvents.InstalmentDue e) {
        var data = new LinkedHashMap<String, Object>();
        data.put("loanNumber", e.loanNumber());
        data.put("customerRef", e.customerRef());
        data.put("msisdn", e.msisdn());
        data.put("dueDate", e.dueDate().toString());
        data.put("currency", e.amountDue().currency().name());
        data.put("amountDue", e.amountDue().amount());
        data.put("overdue", e.overdue());
        publish("loan.instalment-due", "InstalmentDue", data);
    }

    @ApplicationModuleListener
    void on(LoanEvents.ArrearsBucketChanged e) {
        var data = new LinkedHashMap<String, Object>();
        data.put("loanNumber", e.loanNumber());
        data.put("customerRef", e.customerRef());
        data.put("msisdn", e.msisdn());
        data.put("dpd", e.daysPastDue());
        data.put("fromBucket", e.fromBucket().name());
        data.put("toBucket", e.toBucket().name());
        data.put("currency", e.arrearsAmount().currency().name());
        data.put("arrearsAmount", e.arrearsAmount().amount());
        publish("loan.arrears-changed", "LoanArrearsChanged", data);
    }

    private void publish(String routingKey, String type, Map<String, Object> data) {
        Envelope envelope;
        try {
            envelope = new Envelope(UUID.randomUUID(), type, json.writeValueAsString(data), Instant.now(), null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (props.amqpEnabled()) {
            // throws on failure, so the publication stays incomplete and is retried
            rabbit.getObject().convertAndSend(props.exchange(), routingKey, envelope);
        } else {
            log.info("[bus disabled] {} {}", routingKey, envelope.payload());
        }
        synchronized (recent) {
            recent.addFirst(new Sent(routingKey, envelope));
            while (recent.size() > 50) recent.removeLast();
        }
    }

    List<Sent> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }
}
