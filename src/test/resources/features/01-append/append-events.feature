@append
Feature: Appending events to a stream

  As a service that records what happened to a bed or an account,
  I want to append events to a stream, optionally stating the version I last saw,
  so that concurrent writers are rejected instead of silently overwriting each other.

  Stream versions are 0-based: the first event of a stream is version 0.
  "no stream" means the stream must not exist yet; "any" skips the check.

  Scenario: Appending to a new stream creates the stream
    When 1 event is appended to a new bank account stream
    Then the stream state is:
      | type                     | version |
      | bankaccounts.BankAccount | 0       |

  Scenario Outline: The expected version decides whether an append is accepted
    Given a bank account stream with <existing> events
    When <appending> events are appended expecting version <expected>
    Then the append is <outcome>
    And the stream version is <final version>

    Examples:
      | existing | appending | expected  | outcome  | final version |
      | 0        | 1         | no stream | accepted | 0             |
      | 0        | 3         | no stream | accepted | 2             |
      | 1        | 1         | 0         | accepted | 1             |
      | 1        | 2         | 0         | accepted | 2             |
      | 3        | 1         | any       | accepted | 3             |
      | 2        | 1         | 0         | rejected | 1             |
      | 1        | 1         | no stream | rejected | 0             |
      | 1        | 2         | 5         | rejected | 0             |

  Scenario Outline: Only one of several concurrent writers wins
    Given a bank account stream with <existing> events
    When <writers> writers each append 1 event expecting version <expected> at the same time
    Then exactly 1 writer is accepted and the rest are rejected
    And the stream version is <final version>

    Examples:
      | existing | writers | expected  | final version |
      | 0        | 5       | no stream | 0             |
      | 1        | 5       | 0         | 1             |
