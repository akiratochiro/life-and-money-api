package com.akiratochiro.life_and_money_api.bill;

public class BillPaidAfterEndException extends RuntimeException {
    public BillPaidAfterEndException() {
        super("Há um mês pago depois desse fim. Desfaça esse pagamento antes de encerrar a conta.");
    }
}
