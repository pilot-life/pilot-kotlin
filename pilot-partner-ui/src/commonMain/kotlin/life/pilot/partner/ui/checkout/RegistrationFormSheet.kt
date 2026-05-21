package life.pilot.partner.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import life.pilot.partner.sdk.model.RegistrationCreateRequest
import life.pilot.partner.sdk.model.TicketTypeRow

/**
 * Minimum-viable Registration form rendered as a bottom sheet body.
 *
 * Collects the five required fields from openapi's `RegistrationCreateRequest`
 * (ticketTypeUUID + firstName + lastName + email + phone). The
 * `ticketTypeUUID` is fixed by [ticketType] — partners present this
 * form per-ticket-type from the inventory's `registrationTicketTypes`
 * array.
 *
 * Guests list / age verification are NOT surfaced; partners with that
 * flow should compose their own form and call
 * `client.events.createRegistration(...)` directly.
 */
@Composable
fun RegistrationFormSheet(
    ticketType: TicketTypeRow,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    error: String? = null,
    submitLabel: String = "Complete Registration",
    onSubmit: (RegistrationCreateRequest) -> Unit,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val emailValid = email.isNotBlank() && EmailLooksValid(email)
    val canSubmit = !isSubmitting && firstName.isNotBlank() && lastName.isNotBlank() && emailValid && phone.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(RegistrationFormSheetTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Register for ${ticketType.name}", style = MaterialTheme.typography.titleLarge)
        Text(
            ticketType.description ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it.take(45) },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(RegistrationFormSheetTestTags.FirstName),
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it.take(45) },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(RegistrationFormSheetTestTags.LastName),
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim().take(255) },
            label = { Text("Email") },
            isError = email.isNotBlank() && !emailValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag(RegistrationFormSheetTestTags.Email),
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(45) },
            label = { Text("Phone (E.164)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().testTag(RegistrationFormSheetTestTags.Phone),
        )

        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                onSubmit(
                    RegistrationCreateRequest(
                        ticketTypeUUID = ticketType.ticketTypeUUID,
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        phone = phone,
                    ),
                )
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().testTag(RegistrationFormSheetTestTags.Submit),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text(submitLabel)
        }
    }
}

private val EmailRegexReg = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private fun EmailLooksValid(s: String): Boolean = EmailRegexReg.matches(s)

object RegistrationFormSheetTestTags {
    const val Root = "RegistrationFormSheet.root"
    const val FirstName = "RegistrationFormSheet.firstName"
    const val LastName = "RegistrationFormSheet.lastName"
    const val Email = "RegistrationFormSheet.email"
    const val Phone = "RegistrationFormSheet.phone"
    const val Submit = "RegistrationFormSheet.submit"
}
