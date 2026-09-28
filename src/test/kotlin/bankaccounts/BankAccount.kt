package bankaccounts

import java.time.LocalDateTime
import java.util.*

/** Test domain from the workshop: state rebuilt by folding [evolve] over the events. */
data class BankAccount(
    val id: UUID,
    val status: BankAccountStatus,
    val balance: Double,
    val version: Long
) {
    enum class BankAccountStatus { Opened, Closed }

    sealed interface Event {
        data class BankAccountOpened(
            val bankAccountId: UUID,
            val accountNumber: String,
            val clientId: UUID,
            val currencyISOCode: String,
            val createdAt: LocalDateTime,
            val version: Long
        ) : Event

        data class DepositRecorded(
            val bankAccountId: UUID,
            val amount: Double,
            val cashierId: UUID,
            val recordedAt: LocalDateTime,
            val version: Long
        ) : Event

        data class CashWithdrawnFromATM(
            val bankAccountId: UUID,
            val amount: Double,
            val atmId: UUID,
            val recordedAt: LocalDateTime,
            val version: Long
        ) : Event

        data class BankAccountClosed(
            val bankAccountId: UUID,
            val reason: String,
            val closedAt: LocalDateTime,
            val version: Long
        ) : Event
    }

    companion object {
        fun evolve(bankAccount: BankAccount?, event: Event): BankAccount? =
            when (event) {
                is Event.BankAccountOpened ->
                    BankAccount(event.bankAccountId, BankAccountStatus.Opened, 0.0, event.version)
                is Event.DepositRecorded ->
                    bankAccount?.copy(balance = bankAccount.balance + event.amount, version = event.version)
                is Event.CashWithdrawnFromATM ->
                    bankAccount?.copy(balance = bankAccount.balance - event.amount, version = event.version)
                is Event.BankAccountClosed ->
                    bankAccount?.copy(status = BankAccountStatus.Closed, version = event.version)
            }
    }
}
