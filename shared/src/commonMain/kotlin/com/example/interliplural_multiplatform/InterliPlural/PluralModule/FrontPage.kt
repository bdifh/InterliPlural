package com.example.interliplural_multiplatform.InterliPlural.PluralModule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.interliplural_multiplatform.InterliPlural.DataModule.Member
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_BackgroundFrontIndicator
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_ButtonBackgroundColor
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_ButtonTextColor
import com.example.interliplural_multiplatform.InterliPlural.Helpers.ch_TextColor


@Composable
fun InterliFrontpage(member: List<Member> ) {
    val frontingMembers = remember { mutableStateListOf<String>() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = 30.dp)
    ) {
        // ==== Front indicator ====
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(ch_BackgroundFrontIndicator)
                .absolutePadding(left = 0.dp, top = 20.dp, right = 0.dp, bottom = 20.dp)
                .fillMaxWidth(0.9f)
        ) {

            if (frontingMembers.isEmpty()) {
                Text(
                    text = "Nobody's Fronting",
                    color = ch_TextColor
                ) }
            else {
                Row {
                    for (name in frontingMembers) {
                        Text(
                            text = " $name ",
                            color = ch_TextColor
                        )
                    }
                }
            }
        }
           // ==== Members List ====
            if (member.isNotEmpty()) {
                for (name in member) {
                    Row(
                        modifier = Modifier
                            .absolutePadding(left = 0.dp, top = 5.dp, right = 0.dp, bottom = 5.dp)
                            .fillMaxWidth(0.9f)
                            .border(
                                width = 1.dp,
                                color = ch_TextColor //later aanpassen naar ch_MemberPersonalColor
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .absolutePadding(left = 10.dp, top = 20.dp, right = 10.dp, bottom = 20.dp)
                        )
                        {
                            Text(
                                text = name.name,
                                color = ch_TextColor
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                if (frontingMembers.contains(name.name)) {
                                    frontingMembers.remove(name.name)
                                } else {
                                    frontingMembers.add(name.name)
                                }
                            },
                            colors = ButtonDefaults.buttonColors( containerColor =
                                if(frontingMembers.contains(name.name)) { ch_ButtonTextColor }
                                else { ch_ButtonBackgroundColor } ),
                            modifier = Modifier
                                .absolutePadding(left = 10.dp, top = 0.dp, right = 10.dp, bottom = 0.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            if(frontingMembers.contains(name.name)) {
                                Text(
                                    text = "▼", // alt31 ▼
                                    color = ch_ButtonBackgroundColor
                                )
                            }
                            else {
                                Text(
                                    text = "▲", // alt30 ▲
                                    color = ch_ButtonTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }


