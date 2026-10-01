package zw.insurehub.lendhub.repayments;

import zw.insurehub.lendhub.shared.money.Money;

/** Port to the Payments hub (I1: POST /payments with Idempotency-Key). Adapter in the integration module. */
public interface PaymentCollector {

    record Started(String paymentId, String status) {
    }

    Started requestPayment(String loanNumber, String msisdn, Money amount, String idempotencyKey);
}
