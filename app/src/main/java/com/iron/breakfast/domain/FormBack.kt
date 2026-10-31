package com.iron.breakfast.domain

/** What Back or the arrow does on a form. */
internal enum class FormBack { IGNORE, ASK, CLOSE }

/** While a save runs, Back waits for it; a changed form asks before discarding; an unchanged one closes. */
internal fun formBack(saving: Boolean, dirty: Boolean): FormBack = when {
    saving -> FormBack.IGNORE
    dirty -> FormBack.ASK
    else -> FormBack.CLOSE
}
