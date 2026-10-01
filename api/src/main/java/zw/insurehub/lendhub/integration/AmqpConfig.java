package zw.insurehub.lendhub.integration;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * RabbitMQ wiring, only when {@code lendhub.integration.amqp-enabled=true}. LendHub's queue receives
 * {@code PaymentSucceeded} from the Payments hub (routing key = event type name, as Integrations publishes it) and a
 * dead-letter queue catches poison messages.
 */
@Configuration
@ConditionalOnProperty(name = "lendhub.integration.amqp-enabled", havingValue = "true")
class AmqpConfig {

    static final String QUEUE = "lendhub";

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    Declarables topology(IntegrationProperties props) {
        TopicExchange exchange = new TopicExchange(props.exchange(), true, false);
        Queue dlq = QueueBuilder.durable(QUEUE + ".dlq").build();
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(QUEUE + ".dlq").build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("PaymentSucceeded");
        return new Declarables(exchange, queue, dlq, binding);
    }

    /** Consumes the Payments hub's PaymentSucceeded; idempotent because repayments are unique on provider reference. */
    @Component
    @ConditionalOnProperty(name = "lendhub.integration.amqp-enabled", havingValue = "true")
    static class PaymentSucceededListener {

        private static final Logger log = LoggerFactory.getLogger(PaymentSucceededListener.class);

        private final RepaymentService repayments;
        private final ObjectMapper json;

        PaymentSucceededListener(RepaymentService repayments, ObjectMapper json) {
            this.repayments = repayments;
            this.json = json;
        }

        @RabbitListener(queues = QUEUE)
        void on(Map<String, Object> envelope) throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = json.readValue(String.valueOf(envelope.get("payload")), Map.class);
            String reference = String.valueOf(payload.get("policyNumber"));
            if (!reference.startsWith("LN-")) return; // an insurance premium, not ours
            var receipt = repayments.receive(new RepaymentService.ReceivePayment(reference,
                    Money.of(new BigDecimal(String.valueOf(payload.get("amount"))), CurrencyCode.valueOf(String.valueOf(payload.get("currency")))),
                    PaymentChannels.of(String.valueOf(payload.get("method"))),
                    String.valueOf(payload.get("providerReference"))));
            log.info("PaymentSucceeded {} for {} (duplicate={})", payload.get("providerReference"), reference, receipt.duplicate());
        }
    }
}
