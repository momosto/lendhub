#!/usr/bin/env python3
"""Post-deployment smoke test: walks the demo journey against a running LendHub API.

    python scripts/smoke_test.py [--api http://localhost:8080] [--days 45]

Officer registers a trader and captures a weekly loan → manager approves → borrower accepts with the OTP →
finance disburses → EcoCash repayment request → EOD fast-forward → PAR, collections and trial balance checks.
Uses only the standard library so it runs anywhere (CI job, laptop, k8s Job).
"""
import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
import uuid


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.tokens = {}

    def call(self, method, path, user=None, body=None, headers=None, expect=(200, 201, 202)):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if user:
            req.add_header("Authorization", "Bearer " + self.token(user))
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                status, text = r.status, r.read().decode()
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode()
        if status not in expect:
            raise SystemExit(f"FAIL {method} {path} → {status}: {text[:400]}")
        return json.loads(text) if text and text[0] in "[{" else text

    def token(self, user):
        if user not in self.tokens:
            r = self.call("POST", "/api/v1/auth/login", body={"username": f"{user}@lendhub.demo", "password": "Demo123!"})
            self.tokens[user] = r["accessToken"]
        return self.tokens[user]


def step(msg):
    print(f"✔ {msg}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--api", default="http://localhost:8080")
    p.add_argument("--days", type=int, default=45)
    a = p.parse_args()
    api = Api(a.api)

    health = api.call("GET", "/actuator/health")
    assert health["status"] == "UP", health
    step("API healthy")

    serial = str(random.randint(100000, 899999)).replace("99", "12")
    borrower = api.call("POST", "/api/v1/borrowers", "officer", {
        "firstName": "Chipo", "lastName": "Mlambo", "nationalId": f"63-{serial}A42", "msisdn": f"077{random.randint(1000000, 9999999)}",
        "dateOfBirth": "1987-03-14", "address": "Mbare Musika", "employmentType": "SELF_EMPLOYED", "employer": "Vegetable stall",
        "yearsInEmployment": 5, "bureauConsent": True})
    step(f"borrower {borrower['customerRef']} registered (national ID shown as {borrower['nationalIdMasked']})")

    app = api.call("POST", "/api/v1/applications", "officer", {
        "borrowerId": borrower["id"], "productCode": "TRADER", "amount": 600, "currency": "USD", "instalments": 12,
        "frequency": "WEEKLY", "purpose": "Tomatoes and onions for the festive season", "monthlyIncome": 1100,
        "monthlyExpenses": 400, "otherDebtRepayments": 0})
    app = api.call("POST", f"/api/v1/applications/{app['id']}/submit", "officer")
    step(f"{app['applicationNumber']} scored grade {app['grade']} ({app['scorePoints']} pts), affordable={app['affordable']}")

    offered = api.call("POST", f"/api/v1/applications/{app['id']}/decisions", "manager", {"approve": True, "comment": "smoke"})
    o = offered["offer"]
    step(f"approved; offer EIR {o['effectiveAnnualRatePercent']}%, total repayable {o['totalRepayable']}")
    api.call("POST", f"/api/v1/applications/{app['id']}/offer/accept", "officer", {"otp": offered["demoOtp"]})

    loan = None
    for _ in range(50):
        loan = next((l for l in api.call("GET", "/api/v1/loans", "finance") if l["applicationNumber"] == app["applicationNumber"]), None)
        if loan and loan["status"] == "COVERED":
            break
        time.sleep(0.3)
    assert loan and loan["status"] == "COVERED", loan
    step(f"{loan['loanNumber']} booked with credit life {loan['creditLifePolicyNumber']}")

    loan = api.call("POST", f"/api/v1/loans/{loan['id']}/disburse", "finance")
    step(f"disbursed {loan['netDisbursed']} to EcoCash; {len(loan['schedule'])} weekly instalments")

    api.call("POST", f"/api/v1/loans/{loan['id']}/repayment-requests", "channel", {"amount": 30},
             headers={"Idempotency-Key": str(uuid.uuid4())})
    for _ in range(40):
        if api.call("GET", f"/api/v1/loans/{loan['id']}/repayments", "finance"):
            break
        time.sleep(0.25)
    step("EcoCash repayment requested and posted")

    runs = api.call("POST", "/api/v1/eod/runs", "finance", {"days": a.days})
    failed = [r for r in runs if r["status"] != "COMPLETED"]
    assert not failed, failed
    step(f"EOD ran {len(runs)} days; business date now {api.call('GET', '/api/v1/eod/business-date', 'finance')['businessDate']}")

    aged = api.call("GET", f"/api/v1/loans/{loan['id']}", "collections")
    step(f"loan is {aged['status']} at {aged['daysPastDue']} DPD ({aged['arrearsBucket']})")
    tasks = api.call("GET", f"/api/v1/collections/loans/{loan['id']}/tasks", "collections")
    step("collections tasks: " + ", ".join(t["type"] for t in tasks))
    par = api.call("GET", "/api/v1/reports/par?currency=USD", "manager")
    step(f"PAR30 {par['total']['par30Percent']}% of {par['total']['portfolio']}")
    tb = api.call("GET", "/api/v1/reports/trial-balance", "finance")
    assert tb["balanced"], tb
    step("trial balance balances")
    print("SMOKE TEST PASSED")


if __name__ == "__main__":
    sys.exit(main())
