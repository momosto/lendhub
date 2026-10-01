package zw.insurehub.lendhub.integration;

import zw.insurehub.lendhub.repayments.Repayment;

/** Maps the Payments hub's method names ("EcoCash", "OneMoney") to repayment channels. */
final class PaymentChannels {

    static Repayment.Channel of(String method) {
        return method != null && method.toLowerCase().contains("onemoney") ? Repayment.Channel.ONEMONEY : Repayment.Channel.ECOCASH;
    }

    private PaymentChannels() {
    }
}
