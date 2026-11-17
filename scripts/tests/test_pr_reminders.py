"""Tests of the reminder plan of scripts/pr-reminders.py.

Run from the repository root: python3 -m unittest discover -s scripts/tests
"""

import importlib.util
import json
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
REVIEW_MARKER = getattr(reminders, "REVIEW_MARKER", reminders.MARKER)


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


def reminder(days_ago, marker=REVIEW_MARKER):
    return comment(days_ago, who=reminders.WORKFLOW_BOT, body="Hi\n" + marker, association="NONE")


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


def coderabbit_thread(days_ago):
    return {"isResolved": False, "isOutdated": False, "comments": {"nodes": [
        {"url": "https://github.com/o/r/pull/1#discussion_r3", "createdAt": at(days_ago),
         "author": {"login": reminders.CODERABBIT}, "body": "**Check the input**"},
    ]}}


def plan(*prs, maintainers=frozenset({MAINTAINER})):
    return reminders.plan(list(prs), NOW, maintainers)


def kinds(actions):
    return [(number, kind) for number, kind, _ in actions]


class ChangesRequestedTest(unittest.TestCase):

    def test_reminds_the_author_30_days_after_a_change_request(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 30)]))
        self.assertEqual([(1, "comment")], kinds(actions))
        body = actions[0][2]
        self.assertIn(f"@{AUTHOR}", body)
        self.assertIn("#pullrequestreview-30", body)
        self.assertIn(REVIEW_MARKER, body)

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
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_a_late_reminder_gives_the_author_30_days(self):
        # A review older than 60 days with no reminder yet (for example when the rule starts) gets the reminder first.
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 90)]))
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_a_cla_reminder_after_the_review_is_no_review_reminder(self):
        # The CLA was signed after its reminder: the change request still needs its own reminder.
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], comments=[reminder(37, reminders.MARKER)]))
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_a_later_approval_replaces_the_change_request(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40), review("APPROVED", 35)])))

    def test_a_later_comment_review_keeps_the_change_request(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40), review("COMMENTED", 35)]))
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_a_dismissed_review_is_no_change_request(self):
        self.assertEqual([], plan(pr(reviews=[review("DISMISSED", 40)])))

    def test_a_change_request_of_a_person_without_write_access_does_not_count(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40, reviewer="other",
                                                     association="CONTRIBUTOR")])))

    def test_an_org_member_without_write_access_does_not_count(self):
        self.assertEqual([], plan(pr(reviews=[review("CHANGES_REQUESTED", 40, reviewer="reader")])))
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40), review("APPROVED", 35, reviewer="reader")]))
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_a_maintainer_with_a_private_membership_counts(self):
        # GITHUB_TOKEN reports a private member as CONTRIBUTOR; the permission check finds the maintainer.
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40, association="CONTRIBUTOR")]))
        self.assertEqual([(1, "comment")], kinds(actions))

    def test_an_unsigned_cla_keeps_the_cla_reminder(self):
        actions = plan(pr(reviews=[review("CHANGES_REQUESTED", 40)], cla="PENDING"))
        self.assertEqual([(1, "label"), (1, "comment")], kinds(actions))
        self.assertIn("CLA", actions[1][2])
        self.assertNotIn("#pullrequestreview-", actions[1][2])


class ReminderBudgetTest(unittest.TestCase):

    def test_a_review_reminder_does_not_use_the_cla_and_coderabbit_budget(self):
        # One review reminder and one CodeRabbit reminder: the CodeRabbit rule may still send its second one.
        pull = pr(reviews=[review("COMMENTED", 16, reviewer=reminders.CODERABBIT, association="NONE")],
                  threads=[coderabbit_thread(16)], comments=[reminder(20), reminder(15, reminders.MARKER)])
        self.assertEqual([(1, "comment")], kinds(plan(pull)))


class CutListsTest(unittest.TestCase):
    """The query reads at most 50 reviews, 100 review threads, and 50 comments in each thread."""

    def fetch(self, pull, rest):
        # One GraphQL page with the PR, and the REST lists for the reviews and the review comments.
        page = {"data": {"repository": {"pullRequests": {
            "pageInfo": {"hasNextPage": False, "endCursor": None}, "nodes": [pull]}}}}

        def fake_gh(*args, input=None):
            if args[:2] == ("api", "graphql"):
                return json.dumps(page)
            path = args[2]
            return "\n".join(json.dumps(item) for item in rest[path.rsplit("/", 1)[1]])

        original = reminders.gh
        reminders.gh = fake_gh
        try:
            return reminders.fetch_open_prs("o/r")
        finally:
            reminders.gh = original

    def test_reads_all_reviews_when_the_query_cut_them(self):
        later = [review("COMMENTED", 39 - n / 10) for n in range(50)]
        pull = pr(reviews=later)
        pull["reviews"]["totalCount"] = 51
        prs = self.fetch(pull, {"reviews": [rest_review(r) for r in [review("CHANGES_REQUESTED", 40)] + later]})
        self.assertEqual([(1, "comment")], kinds(plan(*prs)))

    def test_reads_all_review_comments_when_the_query_cut_a_thread(self):
        pull = pr(reviews=[review("CHANGES_REQUESTED", 70)], comments=[reminder(40)], threads=[coderabbit_thread(80)])
        pull["reviewThreads"]["nodes"][0]["comments"]["totalCount"] = 51
        prs = self.fetch(pull, {"comments": [{"created_at": at(35), "user": {"login": AUTHOR}}]})
        self.assertEqual([], plan(*prs))


def rest_review(node):
    """A review of the GraphQL fixture as the REST API gives it."""
    return {"state": node["state"], "html_url": node["url"], "submitted_at": node["submittedAt"],
            "author_association": node["authorAssociation"], "commit_id": node["commit"]["oid"],
            "user": {"login": node["author"]["login"], "type": node["author"]["__typename"]}}


class MaintainerLoginsTest(unittest.TestCase):

    def test_reviewers_with_a_decision_are_checked_for_write_access(self):
        pull = pr(reviews=[review("CHANGES_REQUESTED", 40, association="CONTRIBUTOR"),
                           review("COMMENTED", 30, reviewer="commenter", association="CONTRIBUTOR")])
        self.assertEqual({AUTHOR, MAINTAINER}, reminders.logins_to_check([pull]))


if __name__ == "__main__":
    unittest.main()
