@read
Feature: Reading streams

  As a service rebuilding state or answering queries,
  I want to read a stream's events in order, with their versions,
  so that I can replay exactly what happened.

  Background:
    Given a bank account with these events:
      | event                | amount |
      | BankAccountOpened    |        |
      | DepositRecorded      | 100    |
      | CashWithdrawnFromATM | 50     |

  Scenario: Events come back in append order with 0-based versions
    When the stream is read
    Then the stream contains:
      | version | event                | amount |
      | 0       | BankAccountOpened    |        |
      | 1       | DepositRecorded      | 100    |
      | 2       | CashWithdrawnFromATM | 50     |
    And every event equals the one that was appended

  Scenario: The stream state reports its type and last version
    Then the stream state is:
      | type                     | version |
      | bankaccounts.BankAccount | 2       |

  Scenario: An unknown stream has no state and no events
    When an unknown stream is read
    Then there is no stream state
    And the stream contains no events
