@aggregation
Feature: Aggregating a stream into state

  As a service that decides what may happen next,
  I want to fold a stream's events into current state, or into state as of an
  earlier version or point in time,
  so that decisions and history views both come from the events alone.

  Scenario Outline: Aggregating up to a stream version
    Given a bank account with these events:
      | event                | amount |
      | BankAccountOpened    |        |
      | DepositRecorded      | 100    |
      | CashWithdrawnFromATM | 50     |
    When the bank account is aggregated at version <at version>
    Then the bank account is:
      | status | balance | version   |
      | Opened | <balance> | <version> |

    Examples:
      | at version | balance | version |
      | 0          | 0       | 0       |
      | 1          | 100     | 1       |
      | 2          | 50      | 2       |
      | latest     | 50      | 2       |

  Scenario: Aggregating as of a point in time ignores later events
    Given a bank account with these events:
      | event             | amount |
      | BankAccountOpened |        |
      | DepositRecorded   | 100    |
    And the time of the last event is noted
    And later these events are appended:
      | event                | amount |
      | CashWithdrawnFromATM | 50     |
    When the bank account is aggregated as of the noted time
    Then the bank account is:
      | status | balance | version |
      | Opened | 100     | 1       |

  Scenario: A stream without events aggregates to nothing
    When an unknown bank account is aggregated
    Then there is no bank account
