package zw.insurehub.lendhub.provisioning;

import java.time.LocalDate;
import java.util.UUID;

import zw.insurehub.lendhub.shared.money.Money;

/** Provision movement for one currency (positive = charge, negative = release); the ledger posts it. */
public record ProvisionCalculated(UUID runId, LocalDate asOf, Money totalEcl, Money movement) {
}
