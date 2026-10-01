package zw.insurehub.lendhub.scoring;

/** Port to the credit bureau (simulated in the integration module). */
public interface CreditBureau {

    enum Status { CLEAR, THIN_FILE, ADVERSE }

    record BureauReport(Status status, int activeLoans, String reference) {
    }

    BureauReport report(String nationalId);
}
