"""Exercise real Git recovery from a backup, including history and file modes."""
from pathlib import Path
import tempfile
import unittest
import zipfile
from backup_checkpoint import backup, git


class CheckpointBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repo = self.base / "repo"
        self.repo.mkdir()
        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "test@local.invalid")
        git(self.repo, "config", "user.name", "Checkpoint test")
        (self.repo / "gradlew").write_text("#!/bin/sh\nexit 0\n")
        (self.repo / "gradlew").chmod(0o755)
        git(self.repo, "add", "gradlew")
        git(self.repo, "commit", "-qm", "initial")
        git(self.repo, "tag", "stage1-apk")
        git(self.repo, "commit", "--allow-empty", "-qm", "checkpoint")
        self.output = self.base / "backup.zip"

    def test_archive_recovers_history_tag_and_executable_mode(self):
        result = backup(self.repo, self.output)
        destination = self.base / "restored"
        with zipfile.ZipFile(self.output) as archive:
            archive.extractall(destination)
            for info in archive.infolist():
                (destination / info.filename).chmod((info.external_attr >> 16) & 0o777)
        restored = destination / "AutoSkolaCZ"
        self.assertEqual(git(restored, "rev-parse", "HEAD").decode().strip(), result["head"])
        self.assertEqual(git(restored, "rev-list", "--all", "--count").strip(), b"2")
        self.assertEqual(git(restored, "tag", "--list").strip(), b"stage1-apk")
        self.assertEqual(git(restored, "status", "--porcelain"), b"")
        self.assertEqual(git(restored, "fsck", "--full"), b"")

    def test_refuses_uncommitted_or_untracked_work(self):
        (self.repo / "new-source.kt").write_text("unfinished")
        with self.assertRaisesRegex(ValueError, "Commit useful changes"):
            backup(self.repo, self.output)
        self.assertFalse(self.output.exists())

    def test_preserves_existing_backup(self):
        self.output.write_bytes(b"existing backup")
        with self.assertRaisesRegex(ValueError, "already exists"):
            backup(self.repo, self.output)
        self.assertEqual(self.output.read_bytes(), b"existing backup")

    def test_refuses_link_to_external_private_file(self):
        external = self.base / "private-fixture"
        external.write_text("DO_NOT_ARCHIVE")
        (self.repo / "external").symlink_to(external)
        git(self.repo, "add", "external")
        git(self.repo, "commit", "-qm", "link fixture")
        with self.assertRaisesRegex(ValueError, "Symlinked or external"):
            backup(self.repo, self.output)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
