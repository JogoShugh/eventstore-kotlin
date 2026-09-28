@projections
Feature: Inline projections into read models

  As a query side,
  I want read models updated from events in the same transaction as the append,
  so that a query right after a command sees its effect, and a failed append leaves no trace.

  The test read model is a user dashboard: the user's name, number of orders and total amount.

  Background:
    Given a user dashboard projection into a user_dashboards table

  Scenario: The dashboard follows a user and their orders across streams
    Given a user "John Doe" renamed 0 times
    And the user places these orders:
      | number         | amount |
      | ORD/2019/08/01 | 100.13 |
      | ORD/2019/08/02 | 2.110  |
    When the user is renamed to "Alan Smith"
    Then the user dashboard is:
      | user name  | orders | total amount |
      | Alan Smith | 2      | 102.24       |

  Scenario Outline: A command that does not append leaves the dashboard unchanged
    Given the user_dashboards table refuses the user name "Forbidden"
    And the user dashboard projection throws an error for the user name "Crash"
    And a user "John Doe" renamed 0 times
    When the user is renamed to "<new name>" expecting version <expected>
    Then the command <outcome>
    And the user loaded from the repository is:
      | name     | version |
      | John Doe | 0       |
    And the user dashboard is:
      | user name | orders | total amount |
      | John Doe  | 0      | 0            |

    # "Forbidden" fails inside PostgreSQL, which aborts the transaction on its own.
    # "Crash" fails in Kotlin after the event is written, so only an explicit rollback removes it.
    Examples:
      | new name   | expected | outcome     |
      | Alan Smith | 5        | is rejected |
      | Forbidden  | any      | fails       |
      | Crash      | any      | fails       |
