package com.example.interliplural_multiplatform.InterliPlural.Navigating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ScrimDefaults.color
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.interliplural_multiplatform.InterliPlural.DataModule.Member
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_BackgroundIndicator
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_ButtonBackgroundColor
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_ButtonTextColor
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_TextColor
import com.example.interliplural_multiplatform.InterliPlural.PluralModule.InterliFrontpage



@Composable
fun SideMenu() {
    var addNewMemberPopup by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf("") }
    val member = remember { mutableStateListOf<Member>() }

    Row() {
        Column(modifier = Modifier.absolutePadding(left = 10.dp, top = 22.dp, right = 5.dp, bottom = 1.dp)) {
            Row() {
                Box(modifier = Modifier.clickable(onClick = { addNewMemberPopup = true })) {
                    Text(
                        text = "+ Member",
                        color = ch_TextColor
                    )
                }
                if (addNewMemberPopup) {
                    ShowPopupAddMember(
                        onSave = {
                            savedName = it
                            member.add(
                                Member(
                                    id = it,
                                    name = it,
                                    color = " "
                                )
                    ) },
                        onClose = { addNewMemberPopup = false }
                    )
                }
            }

            Box() {
                Text(
                    text = "Front page",
                    color = ch_TextColor
                )
            }
        }
        Box() {
            InterliFrontpage( member )
        }
    }
}



// POPUPS
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowPopupAddMember( onClose: () -> Unit,
                        onSave: (String) -> Unit ) {
    BasicAlertDialog(
        onDismissRequest = {
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(ch_BackgroundIndicator)
        ) {
            Column(modifier = Modifier.absolutePadding(left = 10.dp, top = 10.dp, right = 10.dp, bottom = 10.dp)) {
                var name by remember { mutableStateOf("") }
                Box() {
                    Column {
                        Text(
                            text = "Name:",
                            color = ch_TextColor
                        )
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            colors = TextFieldDefaults.colors(
                                ch_TextColor,
                                unfocusedContainerColor = Color.White.copy(alpha = 0.5f).compositeOver(ch_BackgroundIndicator),
                                focusedContainerColor = Color.White.copy(alpha = 0.5f).compositeOver(ch_BackgroundIndicator)
                            ),
                            modifier = Modifier
                                .fillMaxWidth(1f)
                                .height(50.dp)
                        )
                    }
                }
                Row {
                    Box(
                        modifier = Modifier
                            .clickable( onClick = { onClose() })
                            .absolutePadding(left = 0.dp, top = 10.dp, right = 0.dp, bottom = 0.dp)
                            .background(ch_ButtonBackgroundColor)
                            .absolutePadding(left = 10.dp, top = 5.dp, right = 10.dp, bottom = 5.dp)
                    ) {
                        Text(
                            text = "cancel",
                            color = ch_ButtonTextColor
                        )
                    }
                    Spacer( modifier = Modifier.weight(weight = 1f))

                    Box(
                        modifier = Modifier
                            .clickable(onClick = {
                                onSave(name)
                                onClose()
                            })
                            .absolutePadding(left = 0.dp, top = 10.dp, right = 0.dp, bottom = 0.dp)
                            .background(ch_ButtonBackgroundColor)
                            .absolutePadding(left = 10.dp, top = 5.dp, right = 10.dp, bottom = 5.dp)
                    ) {
                        Text(
                            text = "save",
                            color = ch_ButtonTextColor
                        )
                    }
                }
            }
        }
    }
}

