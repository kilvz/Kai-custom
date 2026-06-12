package com.kai.custom.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.edit_message_cancel
import kai.composeapp.generated.resources.edit_message_fork_warning
import kai.composeapp.generated.resources.edit_message_save_fork
import kai.composeapp.generated.resources.edit_message_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditMessageDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var editedContent by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_message_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.edit_message_fork_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editedContent.trim()) },
                enabled = editedContent.isNotBlank(),
            ) {
                Text(stringResource(Res.string.edit_message_save_fork))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.edit_message_cancel))
            }
        },
    )
}
