package com.brodogfyld.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared UI entry point. Platform shells (desktop window, Android activity,
 * iOS view controller) render this composable. The real customer/kitchen/admin
 * screens are built on top of this in later slices.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Brød & Fyld",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "RestaurantOS foundation",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
