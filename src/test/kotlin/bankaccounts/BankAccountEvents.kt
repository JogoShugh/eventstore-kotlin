package bankaccounts

import bankaccounts.BankAccount.Event
import bankaccounts.BankAccount.Event.BankAccountClosed
import bankaccounts.BankAccount.Event.BankAccountOpened
import bankaccounts.BankAccount.Event.CashWithdrawnFromATM
import bankaccounts.BankAccount.Event.DepositRecorded
import java.time.LocalDateTime
import java.util.*

/** Builds workshop bank account events for one account, numbering their versions from 0. */
class BankAccountEvents(val bankAccountId: UUID = UUID.randomUUID()) {
    private val now: LocalDateTime = LocalDateTime.now()

    /** Creates the named event (simple class name) as the event at [version]. */
    fun event(name: String, amount: Double?, version: Long): Event =
        when (name) {
            "BankAccountOpened" ->
                BankAccountOpened(bankAccountId, ACCOUNT_NUMBER, UUID.randomUUID(), "PLN", now, version)
            "DepositRecorded" ->
                DepositRecorded(bankAccountId, amount ?: DEFAULT_AMOUNT, UUID.randomUUID(), now, version)
            "CashWithdrawnFromATM" ->
                CashWithdrawnFromATM(bankAccountId, amount ?: DEFAULT_AMOUNT, UUID.randomUUID(), now, version)
            "BankAccountClosed" ->
                BankAccountClosed(bankAccountId, "closed by test", now, version)
            else -> throw IllegalArgumentException("Unknown bank account event '$name'")
        }

    /** [count] events starting at [fromVersion]: an opening event first, deposits after it. */
    fun numbered(count: Int, fromVersion: Long = 0): List<Event> =
        (0 until count).map { offset ->
            val version = fromVersion + offset
            event(if (version == 0L) "BankAccountOpened" else "DepositRecorded", null, version)
        }

    private companion object {
        const val ACCOUNT_NUMBER = "PL61 1090 1014 0000 0712 1981 2874"
        const val DEFAULT_AMOUNT = 10.0
    }
}

/** The amount an event moved, or null for events without one. */
val Event.amount: Double?
    get() = when (this) {
        is DepositRecorded -> amount
        is CashWithdrawnFromATM -> amount
        is BankAccountOpened, is BankAccountClosed -> null
    }
