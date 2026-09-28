package bankaccounts

import bankaccounts.BankAccount.Event.*
import java.time.LocalDateTime
import java.util.*

/** The three events most workshop tests append: opened (v0), deposit of 100 (v1), withdrawal of 50 (v2). */
data class BankAccountEvents(
    val bankAccountId: UUID,
    val opened: BankAccountOpened,
    val deposited: DepositRecorded,
    val withdrawn: CashWithdrawnFromATM
) {
    val all: List<BankAccount.Event> get() = listOf(opened, deposited, withdrawn)

    companion object {
        fun create(now: LocalDateTime = LocalDateTime.now()): BankAccountEvents {
            val bankAccountId = UUID.randomUUID()
            return BankAccountEvents(
                bankAccountId,
                BankAccountOpened(bankAccountId, "PL61 1090 1014 0000 0712 1981 2874", UUID.randomUUID(), "PLN", now, 0),
                DepositRecorded(bankAccountId, 100.0, UUID.randomUUID(), now, 1),
                CashWithdrawnFromATM(bankAccountId, 50.0, UUID.randomUUID(), now, 2)
            )
        }
    }
}
