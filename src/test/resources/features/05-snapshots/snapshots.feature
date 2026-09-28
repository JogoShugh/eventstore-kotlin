@snapshots
Feature: Snapshots of aggregate state

  As a query side that needs current state without replaying events,
  I want each aggregate's latest state written to a table in the same transaction as its events,
  so that a snapshot never disagrees with its stream.

  Background:
    Given user snapshots are stored in a users table

  Scenario Outline: The snapshot follows the stream
    Given a user "John Doe" renamed <renames> times
    Then the users table contains:
      | name   | version   |
      | <name> | <version> |

    Examples:
      | renames | name     | version |
      | 0       | John Doe | 0       |
      | 2       | Rename 2 | 2       |

  Scenario: Snapshots can be queried like any table
    Given users with these names are created:
      | name       |
      | John Doe   |
      | John Smith |
      | Anna Smith |
    When the users table is queried for names containing "John"
    Then the query returns:
      | name       |
      | John Doe   |
      | John Smith |

  Scenario: A failed snapshot write rolls back the events
    Given the users table refuses the name "Forbidden"
    And a user "John Doe" renamed 1 times
    When the user is renamed to "Forbidden" expecting version any
    Then the command fails
    And the user loaded from the repository is:
      | name     | version |
      | Rename 1 | 1       |
    And the users table contains:
      | name     | version |
      | Rename 1 | 1       |
