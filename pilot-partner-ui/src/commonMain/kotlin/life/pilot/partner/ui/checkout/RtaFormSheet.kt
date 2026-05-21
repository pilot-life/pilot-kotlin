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
import life.pilot.partner.sdk.model.RtaCreateRequest

/**
 * Minimum-viable Request-to-Attend form rendered as a bottom sheet body.
 * Collects the four required fields from
 * [openapi.yaml's RtaCreateRequest](docs/partner-api/openapi.yaml):
 * firstName, lastName, email, phone.
 *
 * Optional fields (company, social media handles, occupation IDs, etc.)
 * aren't surfaced here — partners with richer onboarding flows should
 * compose their own form and call `client.events.requestToAttend(...)`
 * directly.
 *
 * Caller hosts the sheet in a `ModalBottomSheet`/`Dialog`/full screen
 * and supplies `onSubmit` to push the request to the SDK.
 */
@Composable
fun RtaFormSheet(
    modifier: Modifier = Modifier,
    initial: RtaCreateRequest = RtaCreateRequest(firstName = "", lastName = "", email = "", phone = ""),
    isSubmitting: Boolean = false,
    error: String? = null,
    submitLabel: String = "Submit Request",
    onSubmit: (RtaCreateRequest) -> Unit,
) {
    var firstName by remember { mutableStateOf(initial.firstName) }
    var lastName by remember { mutableStateOf(initial.lastName) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }

    val emailValid = email.isNotBlank() && EmailLooksValid(email)
    val canSubmit = !isSubmitting && firstName.isNotBlank() && lastName.isNotBlank() && emailValid && phone.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(RtaFormSheetTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Request to Attend", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it.take(45) },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(RtaFormSheetTestTags.FirstName),
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it.take(45) },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(RtaFormSheetTestTags.LastName),
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim().take(255) },
            label = { Text("Email") },
            isError = email.isNotBlank() && !emailValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag(RtaFormSheetTestTags.Email),
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(45) },
            label = { Text("Phone (E.164)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().testTag(RtaFormSheetTestTags.Phone),
        )

        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                onSubmit(
                    initial.copy(
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        phone = phone,
                    ),
                )
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().testTag(RtaFormSheetTestTags.Submit),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            }
            Text(submitLabel)
        }
    }
}

private val EmailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private fun EmailLooksValid(s: String): Boolean = EmailRegex.matches(s)

object RtaFormSheetTestTags {
    const val Root = "RtaFormSheet.root"
    const val FirstName = "RtaFormSheet.firstName"
    const val LastName = "RtaFormSheet.lastName"
    const val Email = "RtaFormSheet.email"
    const val Phone = "RtaFormSheet.phone"
    const val Submit = "RtaFormSheet.submit"
}
