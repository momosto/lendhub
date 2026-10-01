package zw.insurehub.lendhub.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import zw.insurehub.lendhub.repayments.Repayment;
import zw.insurehub.lendhub.repayments.RepaymentService;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;

/**
 * Demo only: plays the customer approving the EcoCash prompt, then delivers the result the way the Payments hub
 * would (a payment with a provider reference), a couple of seconds after the request commits.
 */
@Component
class SimulatedPaymentCompleter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentCompleter.class);

    private final RepaymentService repayments;
    private final IntegrationProperties props;

    SimulatedPaymentCompleter(RepaymentService repayments, IntegrationProperties props) {
        this.repayments = repayments;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    void on(OutboundAdapters.SimulatedPaymentApproved e) throws InterruptedException {
        Thread.sleep(props.simulatedPaymentDelay().toMillis());
        String providerRef = "MP" + e.paymentId().replace("-", "").substring(0, 10).toUpperCase();
        var receipt = repayments.receive(new RepaymentService.ReceivePayment(e.loanNumber(),
                Money.of(e.amount(), CurrencyCode.valueOf(e.currency())), Repayment.Channel.ECOCASH, providerRef));
        log.info("Simulated EcoCash payment {} posted to {} (duplicate={})", providerRef, e.loanNumber(), receipt.duplicate());
    }
}
