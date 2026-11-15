package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.ads.LevelPlayManager
import com.example.data.local.MediaStorePhotoLoader
import com.example.data.local.PhotoDatabase
import com.example.data.repository.PhotoRepository
import com.example.ui.LuminaMainApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PhotosViewModel
import com.example.ui.viewmodel.PhotosViewModelFactory

class MainActivity : ComponentActivity() {
  private val database by lazy { PhotoDatabase.getDatabase(this) }
  private val mediaStoreLoader by lazy { MediaStorePhotoLoader(applicationContext) }
  private val repository by lazy { PhotoRepository(database.photoDao(), mediaStoreLoader) }
  private val factory by lazy { PhotosViewModelFactory(application, repository) }
  private val viewModel: PhotosViewModel by viewModels { factory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        LuminaMainApp(viewModel = viewModel)
      }
    }
  }

  override fun onDestroy() {
    LevelPlayManager.destroyAll()
    super.onDestroy()
  }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
  androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
