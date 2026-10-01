package zw.insurehub.lendhub.products;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.products.schedule.Schedule;
import zw.insurehub.lendhub.products.schedule.ScheduleCalculator;
import zw.insurehub.lendhub.products.schedule.ScheduleTerms;
import zw.insurehub.lendhub.shared.fx.FxRates;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.web.DomainException;

/** Public API of the products module: product lookup, limit checks and schedule quotes. */
@Service
@Transactional(readOnly = true)
public class ProductCatalog {

    private final LoanProductRepository products;
    private final ScheduleCalculator calculator;
    private final FxRates fx;

    ProductCatalog(LoanProductRepository products, HolidayCalendar calendar, FxRates fx) {
        this.products = products;
        this.calculator = new ScheduleCalculator(calendar);
        this.fx = fx;
    }

    public List<LoanProduct> all() {
        return products.findAll(org.springframework.data.domain.Sort.by("code"));
    }

    public LoanProduct byCode(String code) {
        return products.findByCode(code).orElseThrow(() -> DomainException.notFound("Product", code));
    }

    public LoanProduct byId(UUID id) {
        return products.findById(id).orElseThrow(() -> DomainException.notFound("Product", id));
    }

    /** Enforces amount, term and frequency limits (LH-10). Amount limits are in USD; ZWG is converted. */
    public void checkLimits(LoanProduct product, Money amount, int instalments, Frequency frequency) {
        if (!product.getFrequencies().contains(frequency)) {
            throw DomainException.rule("frequency-not-allowed", product.getName() + " does not allow " + frequency + " repayments");
        }
        BigDecimal usd = fx.toUsd(amount);
        if (usd.compareTo(product.getMinAmount()) < 0 || usd.compareTo(product.getMaxAmount()) > 0) {
            throw DomainException.rule("amount-out-of-range", "Amount must be between US$" + product.getMinAmount()
                    + " and US$" + product.getMaxAmount() + " (or the ZWG equivalent)");
        }
        BigDecimal months = frequency.termInMonths(instalments);
        if (months.compareTo(BigDecimal.valueOf(product.getMinTermMonths())) < 0
                || months.compareTo(BigDecimal.valueOf(product.getMaxTermMonths())) > 0) {
            throw DomainException.rule("term-out-of-range", "Term must be between " + product.getMinTermMonths()
                    + " and " + product.getMaxTermMonths() + " months");
        }
    }

    public Schedule quote(LoanProduct product, Money amount, int instalments, Frequency frequency, LocalDate start) {
        checkLimits(product, amount, instalments, frequency);
        return schedule(product, amount, instalments, frequency, start);
    }

    /** Builds a schedule without limit checks (used for disbursement and restructures). */
    public Schedule schedule(LoanProduct product, Money amount, int instalments, Frequency frequency, LocalDate start) {
        return calculator.calculate(new ScheduleTerms(amount, product.getMethod(), frequency, instalments,
                product.getMonthlyInterestRate(), product.getEstablishmentFeeRate(), product.getCreditLifeMonthlyRate(), start));
    }

    /** Restructured schedules carry no new establishment fee. */
    public Schedule reschedule(LoanProduct product, Money outstanding, int instalments, Frequency frequency, LocalDate start) {
        return calculator.calculate(new ScheduleTerms(outstanding, product.getMethod(), frequency, instalments,
                product.getMonthlyInterestRate(), BigDecimal.ZERO, product.getCreditLifeMonthlyRate(), start));
    }
}
