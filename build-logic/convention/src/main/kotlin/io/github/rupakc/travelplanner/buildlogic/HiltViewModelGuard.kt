package io.github.rupakc.travelplanner.buildlogic

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure checks behind `verifyHiltViewModels<Variant>`: every `@HiltViewModel` declared in the sources must be bound in
 * the variant's generated Hilt component and survive R8 as a key in Hilt's `LazyClassKey` map.
 *
 * Background: incomplete KSP output (see [configureKspSafety]) once dropped two ViewModels from the release component;
 * the APK built, then crashed on the home screen (`NoSuchMethodException: <init> []` from the default ViewModel factory).
 */
object HiltViewModelGuard {
    private val blockComment = Regex("""/\*[\s\S]*?\*/""")
    private val lineComment = Regex("""//[^\n]*""")
    private val stringLiteral = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\n])*\"|'(?:\\\\.|[^'\\\\\n])+'")
    private val packageDecl = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

    // Tokens that matter for nesting: the annotation, type declarations (with the name, or `companion object`),
    // functions (their braces are not a class body) and braces.
    private val token =
        Regex(
            """@(?:dagger\.hilt\.android\.lifecycle\.)?HiltViewModel\b|(?<![\w:])(?:class|interface|object)\s+(\w+)|""" +
                """\bcompanion\s+object\b|\bfun\b|[{}]""",
        )
    private val mappingClass = Regex("""^(\S+) -> (\S+):$""")
    private const val KEY_SUFFIX = "_HiltModules_KeyModule_Provide_LazyMapKey"

    /**
     * Binary names (`pkg.Outer${'$'}Inner`) of the `@HiltViewModel` classes declared in one Kotlin or Java [source] file,
     * nested classes included. Comments and string literals are ignored.
     */
    fun findHiltViewModels(source: String): List<String> {
        val code = source.replace(blockComment, " ").replace(lineComment, " ").replace(stringLiteral, "\"\"")
        val scanner = NestingScanner(packageDecl.find(code)?.groupValues?.get(1).orEmpty())
        token.findAll(code).forEach(scanner::accept)
        return scanner.found
    }

    /** Tracks enclosing type names through braces; records annotated classes as binary names. */
    private class NestingScanner(private val pkg: String) {
        val found = mutableListOf<String>()
        private val scopes = ArrayDeque<String?>() // enclosing brace scopes: a type name, or null for any other block
        private var pending: String? = null // declared type whose body `{` has not been seen yet
        private var annotated = false

        fun accept(m: MatchResult) {
            when (val text = m.value) {
                "{" -> scopes.addLast(pending).also { pending = null }
                "}" -> scopes.removeLastOrNull().also { pending = null }
                "fun" -> pending = null
                else -> if (text.startsWith("@")) annotated = true else declare(text, m.groupValues[1].ifEmpty { "Companion" })
            }
        }

        private fun declare(text: String, name: String) {
            if (annotated && text.startsWith("class")) {
                val path = (scopes.filterNotNull() + name).joinToString("$")
                found += if (pkg.isEmpty()) path else "$pkg.$path"
            }
            annotated = false
            pending = name
        }
    }

    /**
     * ViewModels that the generated `*_HiltComponents_SingletonC.java` ([componentSource]) does not bind. Dagger writes
     * `<pkg>.<Outer_Inner>_HiltModules_KeyModule_Provide_LazyMapKey` as an import, or inline when the simple name clashes;
     * a ViewModel in the component's own package is referenced by its simple name only.
     */
    fun missingFromComponent(viewModels: Collection<String>, componentSource: String): List<String> {
        val componentPackage = packageDecl.find(componentSource)?.groupValues?.get(1).orEmpty()
        return viewModels.filterNot { vm ->
            val pkg = vm.substringBeforeLast('.', "")
            val hiltName = vm.substringAfterLast('.').replace('$', '_')
            val qualified = if (pkg.isEmpty()) hiltName else "$pkg.$hiltName"
            componentSource.contains("$qualified$KEY_SUFFIX") ||
                (pkg == componentPackage && Regex("""(?<![\w.])${Regex.escape(hiltName + KEY_SUFFIX)}\b""").containsMatchIn(componentSource))
        }.sorted()
    }

    /** Original-to-obfuscated class names from an R8 `mapping.txt`. */
    fun parseMapping(mapping: Sequence<String>): Map<String, String> =
        mapping.mapNotNull { mappingClass.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * ViewModels missing from the shrunk app. Each must (a) be kept by R8 (in [mapping]), (b) exist as a class in the dex
     * (its type descriptor `L<r8/name>;` is a dex string) and (c) have its R8 binary name as an exact dex string: that is
     * the key Hilt's `LazyClassKey` map looks up (R8 rewrites `lazyClassKeyName` through `-identifiernamestring`). For
     * R8 names shorter than [MIN_KEY_LENGTH] characters (c) is skipped, because such a short string may exist for an
     * unrelated reason and would prove nothing; the component check still covers those ViewModels.
     */
    fun missingFromDex(viewModels: Collection<String>, mapping: Map<String, String>, dexStrings: Set<String>): List<String> =
        viewModels.mapNotNull { vm ->
            val obfuscated = mapping[vm]
            when {
                obfuscated == null -> "$vm (removed by R8)"
                "L${obfuscated.replace('.', '/')};" !in dexStrings -> "$vm (R8 class $obfuscated is not in the dex)"
                obfuscated.length >= MIN_KEY_LENGTH && obfuscated !in dexStrings -> "$vm (R8 name $obfuscated is not a dex string)"
                else -> null
            }
        }.sorted()

    /** Shortest R8 name for which the exact-string key check is meaningful. */
    const val MIN_KEY_LENGTH = 3

    /**
     * Every entry of a dex file's string table (header `string_ids_size` at 0x38, `string_ids_off` at 0x3C; each id
     * points at a ULEB128 UTF-16 length followed by MUTF-8 bytes and a NUL).
     */
    fun dexStrings(dex: ByteArray): Set<String> {
        require(dex.size >= HEADER_SIZE && String(dex, 0, DEX_MAGIC.length, Charsets.US_ASCII) == DEX_MAGIC) { "not a dex file" }
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.getInt(STRING_IDS_SIZE_OFFSET)
        val idsOffset = buf.getInt(STRING_IDS_OFF_OFFSET)
        val out = HashSet<String>(count * 2)
        for (i in 0 until count) {
            var pos = buf.getInt(idsOffset + i * Int.SIZE_BYTES)
            while (dex[pos].toInt() and ULEB128_CONTINUE != 0) pos++ // skip the ULEB128 length
            pos++
            val end = generateSequence(pos) { it + 1 }.first { dex[it].toInt() == 0 }
            out += String(dex, pos, end - pos, Charsets.UTF_8)
        }
        return out
    }

    private const val DEX_MAGIC = "dex"
    private const val HEADER_SIZE = 0x70
    private const val STRING_IDS_SIZE_OFFSET = 0x38
    private const val STRING_IDS_OFF_OFFSET = 0x3C
    private const val ULEB128_CONTINUE = 0x80

    /** The build failure text: what is missing and how to recover. */
    fun failureMessage(variant: String, missingInComponent: List<String>, missingInDex: List<String>): String =
        buildString {
            appendLine("Hilt ViewModel guard failed for variant '$variant': this build would crash with")
            appendLine("'Cannot create an instance of class ...' when a missing ViewModel is first shown.")
            if (missingInComponent.isNotEmpty()) {
                appendLine("Not bound in the generated Hilt component (missing or incomplete KSP/Hilt output):")
                missingInComponent.forEach { appendLine("  - $it") }
            }
            if (missingInDex.isNotEmpty()) {
                appendLine("Not in the shrunk dex as a Hilt LazyClassKey (R8 or stale output):")
                missingInDex.forEach { appendLine("  - $it") }
            }
            appendLine("Fix: rebuild from scratch without the build cache, e.g.")
            appendLine("  ./gradlew clean && ./gradlew --no-build-cache assemble${variant.replaceFirstChar(Char::uppercase)}")
            append("  (or scripts/build-release-apk.sh). Do not ship this APK.")
        }
}
