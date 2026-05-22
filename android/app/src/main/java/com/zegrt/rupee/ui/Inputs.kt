package com.zegrt.rupee.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Sprint 3 BABYPROOF-INPUTS primitives. Three reusable composables that
 * replace free-text OutlinedTextField calls across the app so users can't
 * type garbage where structured data is expected.
 *
 * - [CurrencyInputField] — ₹ prefix, digit-only filter (with optional one
 *   decimal point + max 2 paise), Indian-grouping `visualTransformation`
 *   so `100000` reads as `1,00,000` while typing. Stores digits-only in
 *   state; callers parse `.toLong()` or `BigDecimal.movePointRight(2)`.
 *
 * - [DateField] — read-only OutlinedTextField that opens a Material 3
 *   `DatePickerDialog` on tap. Returns an ISO `yyyy-MM-dd` string (or
 *   empty when cleared). Replaces the EMI/card "type yyyy-MM-dd into a
 *   text box" sites that silently accepted `tomorrow`, `12 May`, etc.
 *
 * - [ProviderDropdown] — `ExposedDropdownMenuBox` over a fixed option
 *   list with a free-text fallback. Replaces onboarding bank/card
 *   provider fields. Nudges users toward providers we have parsers for
 *   without locking out custom entries.
 *
 * AccountPicker is intentionally not here yet — it requires a schema
 * change on `canonical_transactions` (manual entries don't currently
 * record which account they came from). Tracked as a follow-up.
 */

// -----------------------------------------------------------------------------
// Currency input
// -----------------------------------------------------------------------------

@Composable
fun CurrencyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowPaise: Boolean = true,
    placeholder: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(sanitizeCurrencyInput(raw, allowPaise)) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = { Text("₹") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowPaise) KeyboardType.Decimal else KeyboardType.Number,
        ),
        visualTransformation = IndianGroupingVisualTransformation(allowPaise),
    )
}

internal fun sanitizeCurrencyInput(raw: String, allowPaise: Boolean): String {
    val filtered = if (allowPaise) {
        raw.filter { it.isDigit() || it == '.' }
    } else {
        raw.filter { it.isDigit() }
    }
    if (!allowPaise) return filtered

    // Only one dot allowed, max 2 digits after. A leading dot (e.g. paste
    // from "Rs. 1,234" → ".1234") is meaningless as currency input — drop
    // the dot and return the digits alone rather than guessing whether
    // the user meant "0.1234" or "1234".
    val dotIdx = filtered.indexOf('.')
    if (dotIdx < 0) return filtered
    if (dotIdx == 0) return filtered.drop(1).filter { it.isDigit() }
    val before = filtered.substring(0, dotIdx)
    val afterRaw = filtered.substring(dotIdx + 1).filter { it.isDigit() }
    val after = afterRaw.take(2)
    return if (after.isEmpty() && filtered.endsWith('.')) "$before." else "$before.$after"
}

internal fun formatIndianGrouping(raw: String): String {
    if (raw.isEmpty()) return ""
    val dotIdx = raw.indexOf('.')
    val (intPart, decPart) = if (dotIdx >= 0) {
        raw.substring(0, dotIdx) to raw.substring(dotIdx + 1)
    } else {
        raw to null
    }
    val formattedInt = formatIntegerIndianGrouping(intPart)
    return if (decPart != null) "$formattedInt.$decPart" else formattedInt
}

private fun formatIntegerIndianGrouping(intDigits: String): String {
    if (intDigits.length <= 3) return intDigits
    val last3 = intDigits.takeLast(3)
    val rest = intDigits.dropLast(3)
    val chunks = mutableListOf<String>()
    var i = rest.length
    while (i > 0) {
        val start = (i - 2).coerceAtLeast(0)
        chunks.add(0, rest.substring(start, i))
        i -= 2
    }
    return "${chunks.joinToString(",")},$last3"
}

/**
 * Visual transformation that displays digits with Indian-grouping commas
 * while keeping the underlying state digits-only. Cursor mapping counts
 * non-digit (comma) characters before any given offset, which is the
 * simplest correct mapping for insert-at-end typing (the common case)
 * and stays close enough for mid-string edits.
 */
internal class IndianGroupingVisualTransformation(
    private val allowPaise: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = formatIndianGrouping(raw)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val safe = offset.coerceAtMost(raw.length)
                return formatIndianGrouping(raw.take(safe)).length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val safe = offset.coerceAtMost(formatted.length)
                return formatted.take(safe).count { it.isDigit() || (allowPaise && it == '.') }
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

// -----------------------------------------------------------------------------
// Date field — opens Material 3 DatePickerDialog on tap
// -----------------------------------------------------------------------------

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    isError: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed: LocalDate? = remember(value) {
        if (value.isBlank()) null else runCatching { LocalDate.parse(value, ISO) }.getOrNull()
    }
    val displayText = parsed?.format(DISPLAY).orEmpty()

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { /* read-only — the picker is the only input */ },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("Tap to pick a date") },
            readOnly = true,
            singleLine = true,
            isError = isError,
            trailingIcon = {
                if (optional && parsed != null) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear date")
                    }
                } else {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                    }
                }
            },
        )
        // Transparent overlay so tapping anywhere on the read-only field opens
        // the picker (Material 3's read-only field swallows clicks otherwise).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickableNoIndication { showPicker = true },
        )
    }

    if (showPicker) {
        val initialMillis = parsed?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(date.format(ISO))
                    }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        indication = null,
        interactionSource = source,
        onClick = onClick,
    )
}

/**
 * Returns true iff [value] parses cleanly as an ISO `yyyy-MM-dd` date.
 * Empty input returns [emptyOk]. Call-site validators use this to gate
 * Save buttons.
 */
fun isValidIsoDate(value: String, emptyOk: Boolean = false): Boolean {
    if (value.isBlank()) return emptyOk
    return try {
        LocalDate.parse(value, ISO)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}

// -----------------------------------------------------------------------------
// Provider dropdown — fixed options + free-text fallback
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                onValueChange(typed)
                // Re-open the menu whenever the user is typing so suggestions
                // stay visible — matches the Material 3 spec for editable
                // dropdowns.
                if (typed.isNotEmpty()) expanded = true
            },
            // Compose BOM 2025.04.01 still surfaces the no-arg `menuAnchor()`
            // overload. The next BOM bump will let us switch to
            // `menuAnchor(MenuAnchorType.PrimaryEditable)` and the deprecation
            // warning will drop off.
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        // The menu uses the box's filterable-anchor inside the same scope.
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Default Indian-bank provider list, matching the parsers we have today. */
val DefaultBankProviders: List<String> = listOf(
    "SBI",
    "ICICI",
    "Axis",
    "HDFC",
    "Kotak",
    "Yes Bank",
    "Federal",
    "Jupiter",
    "Fi",
    "Niyo",
    "Other",
)

/** Default card-network / issuer list. */
val DefaultCardProviders: List<String> = listOf(
    "ICICI",
    "HDFC",
    "Axis",
    "SBI Card",
    "Kotak",
    "CRED",
    "Slice",
    "OneCard",
    "Other",
)
