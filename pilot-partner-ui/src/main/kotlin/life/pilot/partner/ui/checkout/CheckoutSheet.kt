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
import life.pilot.partner.sdk.model.CheckoutPatron

/**
 * Patron-details form rendered as a bottom sheet body. Caller decides how
 * to host it (ModalBottomSheet, dialog, full screen).
 *
 * Submits a [CheckoutPatron] only — payment metadata is supplied by the
 * caller and merged into the SDK's `CheckoutRequest` before sending.
 */
@Composable
fun CheckoutSheet(
    modifier: Modifier = Modifier,
    initialPatron: CheckoutPatron = CheckoutPatron(),
    isSubmitting: Boolean = false,
    error: String? = null,
    submitLabel: String = "Complete Purchase",
    onSubmit: (CheckoutPatron) -> Unit,
) {
    var firstName by remember { mutableStateOf(initialPatron.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initialPatron.lastName.orEmpty()) }
    var email by remember { mutableStateOf(initialPatron.email.orEmpty()) }
    var phone by remember { mutableStateOf(initialPatron.phone.orEmpty()) }

    val emailValid = email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val canSubmit = !isSubmitting && email.isNotBlank() && emailValid

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(CheckoutSheetTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your details",
            style = MaterialTheme.typography.titleLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it.take(45) },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(CheckoutSheetTestTags.FirstName),
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it.take(45) },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag(CheckoutSheetTestTags.LastName),
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            isError = email.isNotBlank() && !emailValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CheckoutSheetTestTags.Email),
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CheckoutSheetTestTags.Phone),
        )

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                onSubmit(
                    CheckoutPatron(
                        firstName = firstName.takeIf { it.isNotBlank() },
                        lastName = lastName.takeIf { it.isNotBlank() },
                        email = email.takeIf { it.isNotBlank() },
                        phone = phone.takeIf { it.isNotBlank() },
                    ),
                )
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CheckoutSheetTestTags.Submit),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(submitLabel)
        }
    }
}

object CheckoutSheetTestTags {
    const val Root = "CheckoutSheet.root"
    const val FirstName = "CheckoutSheet.firstName"
    const val LastName = "CheckoutSheet.lastName"
    const val Email = "CheckoutSheet.email"
    const val Phone = "CheckoutSheet.phone"
    const val Submit = "CheckoutSheet.submit"
}
