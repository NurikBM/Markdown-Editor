package com.markdown.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.markdown.editor.di.EditorViewModelFactory
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorViewModel
import com.markdown.editor.presentation.ui.editor.EditorScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewModelFactory: EditorViewModelFactory

    private val viewModel: EditorViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()

                    LaunchedEffect(Unit) {
                        viewModel.processIntent(EditorIntent.LoadDocument("default-document"))
                    }

                    EditorScreen(
                        state = state,
                        effects = viewModel.uiEffect,
                        onIntent = viewModel::processIntent
                    )
                }
            }
        }
    }
}

