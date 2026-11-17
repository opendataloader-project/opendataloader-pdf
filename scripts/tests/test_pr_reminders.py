"""Tests of the reminder plan of scripts/pr-reminders.py.

Run from the repository root: python3 -m unittest discover -s scripts/tests
"""

import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "pr-reminders.py"
SPEC = importlib.util.spec_from_file_location("pr_reminders", SCRIPT)
reminders = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reminders)

NOW = datetime(2026, 6, 1, tzinfo=timezone.utc)
AUTHOR = "contributor"
MAINTAINER = "maintainer"
HEAD = "head-commit"


def at(days_ago):
    return (NOW - timedelta(days=days_ago)).isoformat().replace("+00:00", "Z")


def review(state, days_ago, reviewer=MAINTAINER, commit=HEAD, association="MEMBER"):
    return {
        "state": state,
        "url": f"https://github.com/o/r/pull/1#pullrequestreview-{days_ago}",
        "submittedAt": at(days_ago),
        "authorAssociation": association,
        "author": {"__typename": "User", "login": reviewer},
        "commit": {"oid": commit},
    }


def comment(days_ago, who=AUTHOR, body="text", association="CONTRIBUTOR"):
    return {"createdAt": at(days_ago), "body": body, "authorAssociation": association, "author": {"login": who}}


def reminder(days_ago):
    return comment(days_ago, who=reminders.WORKFLOW_BOT, body="Hi\n" + reminders.MARKER, association="NONE")


def pr(reviews=(), comments=(), threads=(), labels=(), cla="SUCCESS", head=HEAD, created=120):
    return {
        "number": 1,
        "isDraft": False,
        "createdAt": at(created),
        "authorAssociation": "CONTRIBUTOR",
        "author": {"__typename": "User", "login": AUTHOR},
        "labels": {"nodes": [{"name": name} for name in labels]},
        "commits": {"nodes": [{"commit": {
            "oid": head,
            "status": {"contexts": [{"context": reminders.CLA_CONTEXT, "state": cla}]},
        }}]},
        "reviewThreads": {"nodes": list(threads)},
        "reviews": {"nodes": list(reviews)},
        "comments": {"totalCount": len(comments), "nodes": list(comments)},
    }


def thread(days_ago, who=AUTHOR):
    return {"isResolved": False, "isOutdated": False, "comments": {"nodes": [
        {"url": "https://github.com/o/r/pull/1#discussion_r1", "createdAt": at(days_ago + 1),
         "author": {"login": MAINTAINER}, "body": "**Rename this**"},
        {"url": "https://github.com/o/r/pull/1#discussion_r2", "createdAt": at(days_ago),
         "author": {"login": who}, "body": "Done"},
    ]}}


def plan(*prs, maintainers=frozenset()):
    return reminders.plan(list(prs), NOW, maintainers)


class ChangesRequestedTest(unittest.TestCase):

    def test_reminds_the_author_30_days_after_a_change_request(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 30)]))
        self.assertEqual([(1, "comment")], [(number, kind) for number, kind, _ in actions])
        body = actions[0][2]
        self.assertIn(f"@{AUTHOR}", body)
        self.assertIn("#pullrequestreview-30", body)
        self.assertIn(reminders.MARKER, body)

    def test_waits_before_30_days(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 29)])))

    def test_a_push_after_the_review_is_an_answer(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40, commit="old")])))

    def test_a_comment_of_the_author_after_the_review_is_an_answer(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], comments=[comment(35)])))

    def test_a_reply_of_the_author_in_a_review_thread_is_an_answer(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], threads=[thread(35)])))

    def test_labels_the_pull_request_30_days_after_the_reminder(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 60)], comments=[reminder(30)]))
        self.assertEqual([(1, "label", reminders.STALE_LABEL)], actions)

    def test_sends_no_second_reminder(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 50)], comments=[reminder(20)])))

    def test_keeps_a_stale_label(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 90)], comments=[reminder(60)],
                                     labels=[reminders.STALE_LABEL])))

    def test_a_reminder_before_the_review_does_not_count(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], comments=[reminder(50)]))
        self.assertEqual([(1, "comment")], [(number, kind) for number, kind, _ in actions])

    def test_a_late_reminder_gives_the_author_30_days(self):
        # A review older than 60 days with no reminder yet (for example when the rule starts) gets the reminder first.
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 90)]))
        self.assertEqual([(1, "comment")], [(number, kind) for number, kind, _ in actions])

    def test_a_later_approval_replaces_the_change_request(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40), review("APPROVED", 35)])))

    def test_a_later_comment_review_keeps_the_change_request(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40), review("COMMENTED", 35)]))
        self.assertEqual([(1, "comment")], [(number, kind) for number, kind, _ in actions])

    def test_a_dismissed_review_is_no_change_request(self):
        self.assertEqual([], plan(pr(reviews=[review("DISMISSED", 40)])))

    def test_a_change_request_of_a_person_without_write_access_does_not_count(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40, reviewer="other",
                                                     association="CONTRIBUTOR")])))

    def test_a_maintainer_with_a_private_membership_counts(self):
        # GITHUB_TOKEN reports a private member as CONTRIBUTOR; the permission check finds the maintainer.
        pull = pr(reviews=[review("CHANGES_REQUESTED", 40, association="CONTRIBUTOR")])
        self.assertEqual([], plan(pull))
        actions = plan(pull, maintainers=frozenset({MAINTAINER}))
        self.assertEqual([(1, "comment")], [(number, kind) for number, kind, _ in actions])

    def test_an_unsigned_cla_keeps_the_cla_reminder(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], cla="PENDING"))
        kinds = [(number, kind) for number, kind, _ in actions]
        self.assertEqual([(1, "label"), (1, "comment")], kinds)
        self.assertIn("CLA", actions[1][2])
        self.assertNotIn("#pullrequestreview-", actions[1][2])


class MaintainerLoginsTest(unittest.TestCase):

    def test_reviewers_with_a_decision_are_checked_for_write_access(self):
        pull = pr(reviews=[review("CHANGES_REQUESTED", 40, association="CONTRIBUTOR"),
                           review("COMMENTED", 30, reviewer="commenter", association="CONTRIBUTOR")])
        self.assertEqual({AUTHOR, MAINTAINER}, reminders.logins_to_check([pull]))


if __name__ == "__main__":
    unittest.main()
