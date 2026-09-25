package cz.autoskola.data.importer

import java.io.File
import java.security.MessageDigest

/** Package path: media/name.ext; Room path: <package SHA-256>/media/name.ext. */
internal object InstalledMedia {
    private val wirePattern = Regex("media/[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")
    private val hashPattern = Regex("[a-f0-9]{64}")

    fun storedPath(packageHash: String, wirePath: String): String {
        require(hashPattern.matches(packageHash) && wirePattern.matches(wirePath)) { "Unsafe media path" }
        return "$packageHash/$wirePath"
    }

    fun wirePath(packageHash: String, storedPath: String): String {
        require(storedPath.startsWith("$packageHash/")) { "Media belongs to another package" }
        val wire = storedPath.removePrefix("$packageHash/")
        require(this.storedPath(packageHash, wire) == storedPath) { "Invalid installed media path" }
        return wire
    }

    fun file(root: File, packageHash: String, wirePath: String): File {
        val base = root.canonicalFile
        val candidate = File(base, storedPath(packageHash, wirePath)).absoluteFile
        require(candidate.canonicalFile == candidate && candidate.path.startsWith(base.path + File.separator)) {
            "Media path escapes content root or uses a symlink"
        }
        return candidate
    }

    fun matches(file: File, expectedSha256: String): Boolean = file.isFile && file.length() <= 100L * 1024 * 1024 &&
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        } == expectedSha256
}
