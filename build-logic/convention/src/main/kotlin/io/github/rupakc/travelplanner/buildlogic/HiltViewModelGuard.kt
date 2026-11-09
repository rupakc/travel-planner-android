package io.github.rupakc.travelplanner.buildlogic

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure checks behind `verifyHiltViewModels<Variant>`: every `@HiltViewModel` declared in the sources must be bound in
 * the variant's generated Hilt component and survive R8 as a key in Hilt's `LazyClassKey` map.
 *
 * Background: a stale incremental KSP output once dropped two ViewModels from the release component; the APK built,
 * then crashed on the home screen (`NoSuchMethodException: <init> []` from the default ViewModel factory).
 */
object HiltViewModelGuard {
    private val blockComment = Regex("""/\*[\s\S]*?\*/""")
    private val lineComment = Regex("""//[^\n]*""")
    private val packageDecl = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

    // The annotation, optional arguments (e.g. assistedFactory = X::class), then the next class declaration.
    private val hiltViewModel = Regex("""@(?:dagger\.hilt\.android\.lifecycle\.)?HiltViewModel\b(?:\s*\([^)]*\))?[\s\S]*?(?<!::)\bclass\s+(\w+)""")
    private val mappingClass = Regex("""^(\S+) -> (\S+):$""")

    /** Fully qualified names of the `@HiltViewModel` classes declared in one Kotlin or Java [source] file. */
    fun findHiltViewModels(source: String): List<String> {
        val code = source.replace(blockComment, "").replace(lineComment, "")
        val pkg = packageDecl.find(code)?.groupValues?.get(1)
        return hiltViewModel.findAll(code).map { m ->
            val name = m.groupValues[1]
            if (pkg.isNullOrEmpty()) name else "$pkg.$name"
        }.toList()
    }

    /**
     * ViewModels that the generated `*_HiltComponents_SingletonC.java` ([componentSource]) does not bind. Dagger writes
     * `<fqn>_HiltModules_KeyModule_Provide_LazyMapKey` as an import, or inline when the simple name clashes; a ViewModel in
     * the component's own package is referenced by its simple name only.
     */
    fun missingFromComponent(viewModels: Collection<String>, componentSource: String): List<String> {
        val componentPackage = packageDecl.find(componentSource)?.groupValues?.get(1).orEmpty()
        return viewModels.filterNot { vm ->
            val key = "_HiltModules_KeyModule_Provide_LazyMapKey"
            val samePackage = vm.substringBeforeLast('.', "") == componentPackage
            componentSource.contains("$vm$key") ||
                (samePackage && Regex("""(?<![\w.])${Regex.escape(vm.substringAfterLast('.') + key)}\b""").containsMatchIn(componentSource))
        }.sorted()
    }

    /** Original-to-obfuscated class names from an R8 `mapping.txt`. */
    fun parseMapping(mapping: Sequence<String>): Map<String, String> =
        mapping.mapNotNull { mappingClass.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * ViewModels missing from the shrunk app: removed by R8, or whose (obfuscated) name is not a dex string. Hilt looks
     * ViewModels up by that string (R8 rewrites `lazyClassKeyName` through `-identifiernamestring`), so a missing string
     * means the factory cannot find the class at runtime.
     */
    fun missingFromDex(viewModels: Collection<String>, mapping: Map<String, String>, dexStrings: Set<String>): List<String> =
        viewModels.mapNotNull { vm ->
            val obfuscated = mapping[vm]
            when {
                obfuscated == null -> "$vm (removed by R8)"
                obfuscated !in dexStrings -> "$vm (R8 name $obfuscated is not a dex string)"
                else -> null
            }
        }.sorted()

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
                appendLine("Not bound in the generated Hilt component (stale KSP/Hilt output):")
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
