package com.example.focusflow.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.system.ProfilePhoto
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.card
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.ProfileAvatar
import com.example.focusflow.ui.components.rememberProfilePhotoPicker
import com.example.focusflow.ui.fieldColors
import com.example.focusflow.ui.theme.FFShapes
import com.example.focusflow.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val GENDERS = listOf("Female", "Male", "Non-binary", "Prefer not to say")
private val AREAS = listOf("Work", "Study", "Personal")

/** Step 2. Everything except the name is optional. Saved on this phone only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(s.userName) }
    var phone by rememberSaveable { mutableStateOf(s.phone) }
    var birthday by rememberSaveable { mutableStateOf(s.birthdayMillis) }
    var gender by rememberSaveable { mutableStateOf(s.gender) }
    var occupation by rememberSaveable { mutableStateOf(s.occupation) }
    var organization by rememberSaveable { mutableStateOf(s.organization) }
    var area by rememberSaveable { mutableStateOf(s.focusArea) }
    var photoV by remember { mutableIntStateOf(s.photoVersion) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    val pickPhoto = rememberProfilePhotoPicker { ok ->
        if (ok) { s.photoVersion = s.photoVersion + 1; photoV = s.photoVersion; photoError = null }
        else photoError = "That picture couldn't be used. Try another one."
    }

    fun save() {
        if (name.isBlank()) { nameError = true; return }
        s.userName = name; s.phone = phone; s.birthdayMillis = birthday; s.gender = gender
        s.occupation = occupation; s.organization = organization; s.focusArea = area
        onDone()
    }

    Column(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Set up your profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text("Only your name is required. It stays on this phone.", fontSize = 14.sp, color = cs.onSurfaceVariant)

        Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(72.dp, name, photoV)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Profile picture (optional)", fontSize = 16.sp)
                Row {
                    TextButton({ pickPhoto() }) {
                        Text(if (photoV > 0) "Change" else "Choose photo")
                    }
                    if (photoV > 0) TextButton({ ProfilePhoto.remove(ctx); s.photoVersion = 0; photoV = 0 }) {
                        Text("Remove", color = cs.error)
                    }
                }
            }
        }
        photoError?.let { Text(it, fontSize = 13.sp, color = cs.error) }

        ProfileField(name, { name = it; nameError = false }, "Full name", isError = nameError, capitalize = true)
        if (nameError) Text("Please enter your name.", fontSize = 13.sp, color = cs.error)
        ProfileField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }.take(18) }, "Phone number (optional)", keyboard = KeyboardType.Phone)

        Text("Birthday (optional)", fontSize = 14.sp, color = cs.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (birthday >= 0) Instant.ofEpochMilli(birthday).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) else "Not set",
                Modifier.weight(1f), fontSize = 16.sp, color = if (birthday >= 0) cs.onSurface else cs.onSurfaceVariant
            )
            TextButton({ pickDate = true }) { Text(if (birthday >= 0) "Change" else "Choose date") }
        }

        Text("Gender (optional)", fontSize = 14.sp, color = cs.onSurfaceVariant)
        ChipRow(GENDERS, gender) { gender = if (gender == it) "" else it }

        Text("Professional details (optional)", fontSize = 14.sp, color = cs.onSurfaceVariant)
        ProfileField(occupation, { occupation = it }, "Occupation or role", capitalize = true)
        ProfileField(organization, { organization = it }, "Company or school", capitalize = true)

        Text("I mainly use AMU for (optional)", fontSize = 14.sp, color = cs.onSurfaceVariant)
        ChipRow(AREAS, area) { area = if (area == it) "" else it }

        PrimaryButton("Continue", onClick = ::save, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (birthday >= 0) birthday else null,
            yearRange = (today.year - 110)..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { birthday = it }; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ProfileField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    isError: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, capitalize: Boolean = false
) {
    TextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        isError = isError, shape = FFShapes.small, colors = fieldColors(),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard, imeAction = ImeAction.Next,
            capitalization = if (capitalize) androidx.compose.ui.text.input.KeyboardCapitalization.Words
            else androidx.compose.ui.text.input.KeyboardCapitalization.None
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { Chip(it, selected = selected == it) { onPick(it) } }
    }
}
