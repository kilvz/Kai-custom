package com.inspiredandroid.kai.ui.markdown.math

/**
 * LaTeX command â†’ Unicode mapping for the symbols that LLMs actually emit.
 * Kept deliberately small; unknown commands fall back to rendering the raw `\name` string.
 */
internal object MathSymbols {

    private val GREEK_LOWER = mapOf(
        "alpha" to "Î±", "beta" to "Î²", "gamma" to "Î³", "delta" to "Î´",
        "epsilon" to "Ïµ", "varepsilon" to "Îµ", "zeta" to "Î¶", "eta" to "Î·",
        "theta" to "Î¸", "vartheta" to "Ï‘", "iota" to "Î¹", "kappa" to "Îº",
        "lambda" to "Î»", "mu" to "Î¼", "nu" to "Î½", "xi" to "Î¾",
        "pi" to "Ï€", "varpi" to "Ï–", "rho" to "Ï", "varrho" to "Ï±",
        "sigma" to "Ïƒ", "varsigma" to "Ï‚", "tau" to "Ï„", "upsilon" to "Ï…",
        "phi" to "Ï•", "varphi" to "Ï†", "chi" to "Ï‡", "psi" to "Ïˆ",
        "omega" to "Ï‰",
    )

    private val GREEK_UPPER = mapOf(
        "Gamma" to "Î“", "Delta" to "Î”", "Theta" to "Î˜", "Lambda" to "Î›",
        "Xi" to "Îž", "Pi" to "Î ", "Sigma" to "Î£", "Upsilon" to "Î¥",
        "Phi" to "Î¦", "Psi" to "Î¨", "Omega" to "Î©",
    )

    private val BINARY_OPS = mapOf(
        "cdot" to "â‹…", "cdots" to "â‹¯", "ldots" to "â€¦", "dots" to "â€¦", "vdots" to "â‹®", "ddots" to "â‹±",
        "times" to "Ã—", "div" to "Ã·", "pm" to "Â±", "mp" to "âˆ“",
        "ast" to "âˆ—", "star" to "â‹†", "circ" to "âˆ˜", "bullet" to "â€¢",
        "oplus" to "âŠ•", "ominus" to "âŠ–", "otimes" to "âŠ—", "oslash" to "âŠ˜", "odot" to "âŠ™",
        "cap" to "âˆ©", "cup" to "âˆª", "wedge" to "âˆ§", "vee" to "âˆ¨",
        "setminus" to "âˆ–",
    )

    private val RELATION_OPS = mapOf(
        "leq" to "â‰¤", "le" to "â‰¤", "geq" to "â‰¥", "ge" to "â‰¥",
        "neq" to "â‰ ", "ne" to "â‰ ",
        "approx" to "â‰ˆ", "equiv" to "â‰¡", "sim" to "âˆ¼", "simeq" to "â‰ƒ", "cong" to "â‰…",
        "propto" to "âˆ",
        "ll" to "â‰ª", "gg" to "â‰«",
        "subset" to "âŠ‚", "supset" to "âŠƒ", "subseteq" to "âŠ†", "supseteq" to "âŠ‡",
        "in" to "âˆˆ", "notin" to "âˆ‰", "ni" to "âˆ‹",
        // Use the long-arrow variants (U+27F6 / U+27F5) instead of the short ones (U+2192 /
        // U+2190). The short arrowhead collapses to a single pixel at subscript sizes and reads
        // as a dash; the long variants keep the arrow shape identifiable at any size.
        "to" to "âŸ¶", "rightarrow" to "âŸ¶", "leftarrow" to "âŸµ", "gets" to "âŸµ",
        "Rightarrow" to "âŸ¹", "Leftarrow" to "âŸ¸", "Leftrightarrow" to "âŸº",
        "mapsto" to "âŸ¼", "leftrightarrow" to "âŸ·",
        "implies" to "âŸ¹", "iff" to "âŸº",
    )

    private val MISC_SYMBOLS = mapOf(
        "infty" to "âˆž", "partial" to "âˆ‚", "nabla" to "âˆ‡",
        "forall" to "âˆ€", "exists" to "âˆƒ", "nexists" to "âˆ„",
        "emptyset" to "âˆ…", "varnothing" to "âˆ…",
        "hbar" to "â„", "ell" to "â„“", "Re" to "â„œ", "Im" to "â„‘", "wp" to "â„˜",
        "aleph" to "â„µ", "beth" to "â„¶",
        "neg" to "Â¬", "lnot" to "Â¬",
        "angle" to "âˆ ", "triangle" to "â–³", "square" to "â–¡",
        "top" to "âŠ¤", "bot" to "âŠ¥", "perp" to "âŠ¥", "parallel" to "âˆ¥",
        "degree" to "Â°",
        "prime" to "â€²", "dagger" to "â€ ", "ddagger" to "â€¡",
        "checkmark" to "âœ“",
        "copyright" to "Â©",
        "backslash" to "\\",
    )

    private val LARGE_OPS = mapOf(
        "sum" to "âˆ‘", "prod" to "âˆ", "coprod" to "âˆ",
        "int" to "âˆ«", "iint" to "âˆ¬", "iiint" to "âˆ­", "oint" to "âˆ®",
        "bigcup" to "â‹ƒ", "bigcap" to "â‹‚", "bigvee" to "â‹", "bigwedge" to "â‹€",
        "bigoplus" to "â¨", "bigotimes" to "â¨‚", "bigodot" to "â¨€",
    )

    /** Commands that typeset as upright function names: `\sin`, `\cos`, etc. */
    private val FUNCTION_NAMES = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "sinh", "cosh", "tanh", "coth",
        "arcsin", "arccos", "arctan",
        "log", "ln", "lg", "exp",
        "min", "max", "inf", "sup", "det", "dim", "ker", "deg",
        "gcd", "lcm", "mod", "Pr",
        "arg", "hom",
    )

    /** Function names that also always typeset their subscript as a limit (below in display). */
    private val LIMIT_FUNCTIONS = setOf("lim", "liminf", "limsup", "max", "min", "sup", "inf")

    private val SPACE_COMMANDS = mapOf(
        "," to 3f / 18f,
        ":" to 4f / 18f,
        ";" to 5f / 18f,
        "!" to -3f / 18f,
        " " to 6f / 18f,
        "quad" to 1f,
        "qquad" to 2f,
    )

    /** Commands whose output is literally one character (e.g. `\{` â†’ `{`). */
    private val LITERAL_ESCAPES = mapOf(
        "{" to "{",
        "}" to "}",
        "$" to "$",
        "%" to "%",
        "&" to "&",
        "#" to "#",
        "_" to "_",
    )

    private val DOUBLE_STRUCK_UPPER = mapOf(
        'A' to "ð”¸", 'B' to "ð”¹", 'C' to "â„‚", 'D' to "ð”»", 'E' to "ð”¼", 'F' to "ð”½",
        'G' to "ð”¾", 'H' to "â„", 'I' to "ð•€", 'J' to "ð•", 'K' to "ð•‚", 'L' to "ð•ƒ",
        'M' to "ð•„", 'N' to "â„•", 'O' to "ð•†", 'P' to "â„™", 'Q' to "â„š", 'R' to "â„",
        'S' to "ð•Š", 'T' to "ð•‹", 'U' to "ð•Œ", 'V' to "ð•", 'W' to "ð•Ž", 'X' to "ð•",
        'Y' to "ð•", 'Z' to "â„¤",
    )

    private val CALLIGRAPHIC_UPPER = mapOf(
        'A' to "ð’œ", 'B' to "â„¬", 'C' to "ð’ž", 'D' to "ð’Ÿ", 'E' to "â„°", 'F' to "â„±",
        'G' to "ð’¢", 'H' to "â„‹", 'I' to "â„", 'J' to "ð’¥", 'K' to "ð’¦", 'L' to "â„’",
        'M' to "â„³", 'N' to "ð’©", 'O' to "ð’ª", 'P' to "ð’«", 'Q' to "ð’¬", 'R' to "â„›",
        'S' to "ð’®", 'T' to "ð’¯", 'U' to "ð’°", 'V' to "ð’±", 'W' to "ð’²", 'X' to "ð’³",
        'Y' to "ð’´", 'Z' to "ð’µ",
    )

    fun lookup(command: String): MathAtom? {
        GREEK_LOWER[command]?.let { return Sym(it, SymKind.VARIABLE) }
        GREEK_UPPER[command]?.let { return Sym(it, SymKind.ORDINARY) }
        BINARY_OPS[command]?.let { return Sym(it, SymKind.BIN_OP) }
        RELATION_OPS[command]?.let { return Sym(it, SymKind.REL_OP) }
        MISC_SYMBOLS[command]?.let { return Sym(it, SymKind.ORDINARY) }
        LARGE_OPS[command]?.let { return LargeOp(it) }
        if (command in FUNCTION_NAMES) {
            return Sym(command, SymKind.FUNCTION)
        }
        if (command in LIMIT_FUNCTIONS) {
            // Matches LaTeX default: inline renders subscript beside, display renders below.
            return LargeOp(command)
        }
        SPACE_COMMANDS[command]?.let { return Space(it) }
        LITERAL_ESCAPES[command]?.let { return Sym(it, SymKind.ORDINARY) }
        return null
    }

    fun mapDoubleStruck(ch: Char): String = DOUBLE_STRUCK_UPPER[ch] ?: ch.toString()

    fun mapCalligraphic(ch: Char): String = CALLIGRAPHIC_UPPER[ch] ?: ch.toString()
}
