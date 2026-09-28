@repository
Feature: Handling commands through a repository

  As a command handler,
  I want to load an aggregate's state and version, decide new events from that state,
  and append them expecting that same version,
  so that every decision is based on the state it was checked against.

  The test domain is a user with a name: creating one records UserCreated,
  renaming records UserNameUpdated, and renaming to the current name records nothing.

  Scenario: A user is created, renamed and loaded back
    When a user is created with name "John Doe"
    And the user is renamed to "Adam Smith"
    Then the user loaded from the repository is:
      | name       | version |
      | Adam Smith | 1       |

  Scenario Outline: A command may carry the version its sender last saw
    Given a user "John Doe" renamed <renames> times
    When the user is renamed to "Alan Smith" expecting version <expected>
    Then the command is <outcome>
    And the user loaded from the repository is:
      | name   | version   |
      | <name> | <version> |

    Examples:
      | renames | expected | outcome  | name       | version |
      | 0       | 0        | accepted | Alan Smith | 1       |
      | 2       | 2        | accepted | Alan Smith | 3       |
      | 1       | any      | accepted | Alan Smith | 2       |
      | 2       | 1        | rejected | Rename 2   | 2       |
      | 0       | 3        | rejected | John Doe   | 0       |

  Scenario: A decision without events leaves the stream unchanged
    Given a user "John Doe" renamed 0 times
    When the user is renamed to "John Doe" expecting version any
    Then the command is accepted
    And the user loaded from the repository is:
      | name     | version |
      | John Doe | 0       |

  Scenario: An unknown user is not found
    When an unknown user is loaded
    Then no user is found
