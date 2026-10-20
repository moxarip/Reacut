package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ProjectRepository
import com.example.data.ReaCutDatabase
import com.example.editor.EditorViewModel
import com.example.ui.ReaCutStudioScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = ReaCutDatabase.getInstance(applicationContext)
    val repository = ProjectRepository(database.projectDao())

    setContent {
      MyApplicationTheme {
        val editorViewModel: EditorViewModel = viewModel(
          factory = EditorViewModel.Factory(repository),
        )
        ReaCutStudioScreen(viewModel = editorViewModel)
      }
    }
  }
}
