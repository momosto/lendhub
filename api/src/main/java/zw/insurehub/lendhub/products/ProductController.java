package zw.insurehub.lendhub.products;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import zw.insurehub.lendhub.products.schedule.Frequency;
import zw.insurehub.lendhub.products.schedule.RepaymentMethod;
import zw.insurehub.lendhub.products.schedule.Schedule;
import zw.insurehub.lendhub.shared.money.CurrencyCode;
import zw.insurehub.lendhub.shared.money.Money;
import zw.insurehub.lendhub.shared.time.BusinessDateProvider;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
class ProductController {

    record ProductDto(String code, String name, RepaymentMethod method, Set<Frequency> frequencies,
            BigDecimal monthlyInterestRate, BigDecimal establishmentFeeRate, BigDecimal creditLifeMonthlyRate,
            BigDecimal penaltyMonthlyRate, int graceDays, BigDecimal minAmount, BigDecimal maxAmount,
            int minTermMonths, int maxTermMonths, boolean groupLending) {

        static ProductDto from(LoanProduct p) {
            return new ProductDto(p.getCode(), p.getName(), p.getMethod(), p.getFrequencies(), p.getMonthlyInterestRate(),
                    p.getEstablishmentFeeRate(), p.getCreditLifeMonthlyRate(), p.getPenaltyMonthlyRate(), p.getGraceDays(),
                    p.getMinAmount(), p.getMaxAmount(), p.getMinTermMonths(), p.getMaxTermMonths(), p.isGroupLending());
        }
    }

    private final ProductCatalog catalog;
    private final BusinessDateProvider businessDate;

    ProductController(ProductCatalog catalog, BusinessDateProvider businessDate) {
        this.catalog = catalog;
        this.businessDate = businessDate;
    }

    @GetMapping
    @Operation(summary = "List loan products")
    List<ProductDto> all() {
        return catalog.all().stream().map(ProductDto::from).toList();
    }

    @GetMapping("/{code}/quote")
    @Operation(summary = "Quote a schedule with full cost disclosure (total interest, fees, credit life, effective annual rate)")
    Schedule quote(@PathVariable String code, @RequestParam BigDecimal amount, @RequestParam int term,
            @RequestParam(defaultValue = "MONTHLY") Frequency frequency,
            @RequestParam(defaultValue = "USD") CurrencyCode currency,
            @RequestParam(required = false) LocalDate startDate) {
        var product = catalog.byCode(code);
        return catalog.quote(product, Money.of(amount, currency), term, frequency,
                startDate != null ? startDate : businessDate.today());
    }
}
