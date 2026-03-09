package org.ethereumphone.andyclaw.onboarding

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.example.dgenlibrary.DgenLoadingMatrix
import com.example.dgenlibrary.SystemColorManager
import com.example.dgenlibrary.ui.theme.dgenOcean
import androidx.compose.material3.MaterialTheme
import com.example.dgenlibrary.showDgenToast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dgenlibrary.ui.theme.body1_fontSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ethereumphone.andyclaw.BuildConfig
import org.ethereumphone.andyclaw.NodeApp
import androidx.compose.ui.tooling.preview.Preview
import com.example.dgenlibrary.DgenPrimaryButton
import org.ethereumphone.andyclaw.ui.components.ChadBackground
import org.ethereumphone.andyclaw.ui.components.GlowStyle
import org.ethereumphone.andyclaw.ui.theme.AndyClawTheme
import org.ethereumphone.walletsdk.WalletSDK
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/**
 * Standalone screen shown to existing ethOS users who have already completed
 * onboarding but haven't signed in with their wallet yet.
 */
@Composable
fun WalletSignScreen(
    onComplete: () -> Unit,
    viewModel: WalletSignViewModel = viewModel(),
) {
    val walletAddress by viewModel.walletAddress.collectAsState()
    val isSigning by viewModel.isSigning.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(error) {
        error?.let {
            showDgenToast(context, it)
            viewModel.clearError()
        }
    }

    // Once signed, persist and navigate
    LaunchedEffect(walletAddress) {
        if (walletAddress.isNotBlank()) {
            viewModel.persist()
            onComplete()
        }
    }

    WalletSignScreenContent(
        isSigning = isSigning,
        onSign = { viewModel.signWithWallet() },
    )
}

@Composable
private fun WalletSignScreenContent(
    isSigning: Boolean,
    onSign: () -> Unit,
) {
    val context = LocalContext.current
    val primaryColor = SystemColorManager.primaryColor

    LaunchedEffect(Unit) {
        SystemColorManager.refresh(context)
    }

    ChadBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SIGN IN WITH YOUR WALLET",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = body1_fontSize,
                    shadow = GlowStyle.title(primaryColor),
                ),
                color = primaryColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "AndyClaw now requires a wallet signature to verify your identity for billing. Please sign once to continue.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = GlowStyle.body(primaryColor.copy(alpha = 0.7f)),
                ),
                color = primaryColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(32.dp))

            if (isSigning) {
                DgenLoadingMatrix(
                    size = 20.dp,
                    LEDSize = 5.dp,
                    activeLEDColor = primaryColor,
                    unactiveLEDColor = dgenOcean,
                )
            } else {
                DgenPrimaryButton(
                    text = "Sign",
                    onClick = onSign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        }
}

class WalletSignViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NodeApp

    private val _walletAddress = MutableStateFlow("")
    val walletAddress: StateFlow<String> = _walletAddress.asStateFlow()

    private val _walletSignature = MutableStateFlow("")

    private val _isSigning = MutableStateFlow(false)
    val isSigning: StateFlow<Boolean> = _isSigning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signWithWallet() {
        if (_isSigning.value) return
        _isSigning.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val rpc = "https://base-mainnet.g.alchemy.com/v2/${BuildConfig.ALCHEMY_API}"
                val sdk = WalletSDK(
                    context = app,
                    web3jInstance = Web3j.build(HttpService(rpc)),
                    bundlerRPCUrl = "https://api.pimlico.io/v2/8453/rpc?apikey=${BuildConfig.BUNDLER_API}",
                )
                val address = withContext(Dispatchers.IO) { sdk.getAddress() }
                val sig = sdk.signMessage("Signing into AndyClaw", chainId = 8453)
                if (!sig.startsWith("0x")) {
                    _error.value = "Wallet signing was declined or returned an invalid result. Please try again."
                    return@launch
                }
                _walletAddress.value = address
                _walletSignature.value = sig
            } catch (e: Exception) {
                Log.e("WalletSignVM", "Wallet signing failed", e)
                _error.value = "Wallet signing failed: ${e.message}"
            } finally {
                _isSigning.value = false
            }
        }
    }

    fun persist() {
        app.securePrefs.setWalletAuth(_walletAddress.value, _walletSignature.value)
    }

    fun clearError() {
        _error.value = null
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewWalletSignScreenIdle() {
    AndyClawTheme {
        WalletSignScreenContent(
            isSigning = false,
            onSign = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewWalletSignScreenSigning() {
    AndyClawTheme {
        WalletSignScreenContent(
            isSigning = true,
            onSign = {},
        )
    }
}
