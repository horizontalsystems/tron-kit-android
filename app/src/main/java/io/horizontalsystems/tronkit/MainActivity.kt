package io.horizontalsystems.tronkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import io.horizontalsystems.tronkit.ui.theme.EmptyComposeMaterialTheme
import java.util.Date

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPagerApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmptyComposeMaterialTheme {
                val viewModel = viewModel<MainViewModel>(factory = MainViewModelFactory())

                var selectedPage by remember {
                    mutableStateOf(0)
                }
                val pagerState = rememberPagerState(initialPage = selectedPage)

                LaunchedEffect(key1 = selectedPage, block = {
                    pagerState.scrollToPage(selectedPage)
                })

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            backgroundColor = MaterialTheme.colors.background,
                            bottomBar = {
                                Column {
                                    HsBottomNavigation(
                                        backgroundColor = Color.LightGray,
                                        elevation = 10.dp
                                    ) {
                                        HsBottomNavigationItem(
                                            icon = {
                                                Text(text = "Balance", fontSize = 18.sp)
                                            },
                                            selectedContentColor = Color.Yellow,
                                            unselectedContentColor = Color.Gray,
                                            selected = selectedPage == 0,
                                            onClick = { selectedPage = 0 }
                                        )
                                        HsBottomNavigationItem(
                                            icon = {
                                                Text(text = "Transactions", fontSize = 18.sp)
                                            },
                                            selectedContentColor = Color.Yellow,
                                            unselectedContentColor = Color.Gray,
                                            selected = selectedPage == 1,
                                            onClick = { selectedPage = 1 }
                                        )
                                    }
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(it)) {
                                HorizontalPager(
                                    modifier = Modifier.weight(1f),
                                    state = pagerState,
                                    count = 2,
                                    userScrollEnabled = false,
                                    verticalAlignment = Alignment.Top
                                ) { page ->
                                    when (page) {
                                        0 -> {
                                            Balance(viewModel = viewModel)
                                        }

                                        1 -> {
                                            Transactions(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Balance(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Balance: ${viewModel.balance}", fontSize = 25.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Last Block Height: ${viewModel.lastBlockHeight}", fontSize = 25.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Sync State: ${viewModel.syncState}", fontSize = 25.sp)

        Spacer(modifier = Modifier.height(50.dp))
        Button(onClick = {
            viewModel.trc20TokenInfoTest()
        }) {
            Text(text = "TEST BUTTON")
        }
    }
}

@Composable
fun Transactions(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize()
    ) {
        LazyColumn {
            viewModel.transactions.forEach { fullTransaction ->
                val tx = fullTransaction.transaction
                val decoration = fullTransaction.decoration

                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        SelectionContainer {
                            val status = when {
                                tx.blockNumber == null -> "Pending"
                                tx.isFailed -> "Failed"
                                else -> "Success"
                            }

                            Text(
                                text = "TxID: ${tx.hash.toRawHexString()}\n" +
                                        "Status: ${status}\n" +
                                        "Contract: ${tx.contract}\n" +
                                        "Decoration: ${decoration.javaClass.simpleName}\n" +
                                        "BlockNumber: ${tx.blockNumber}\n" +
                                        "Timestamp: ${tx.timestamp}\n" +
                                        "Date: ${Date(tx.timestamp).toLocaleString()}"
                            )
                        }
                    }
                }
            }
        }
    }
}