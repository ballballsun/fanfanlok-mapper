package com.memoryassist.fanfanlokmapper.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.memoryassist.fanfanlokmapper.utils.Constants
import com.memoryassist.fanfanlokmapper.utils.Logger
import java.io.File

/**
 * Image picker component with preview and multiple selection support
 */
@Composable
fun ImagePicker(
    onImageSelected: (Uri) -> Unit,
    onMultipleImagesSelected: ((List<Uri>) -> Unit)? = null,
    allowMultiple: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImageInfo by remember { mutableStateOf(false) }
    
    // Single image picker
    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            selectedImageUris = listOf(it)
            onImageSelected(it)
            Logger.logUserInteraction("Image selected", uri.toString())
        }
    }
    
    // Multiple images picker
    val multipleImagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris
            selectedImageUri = uris.first()
            onMultipleImagesSelected?.invoke(uris)
            Logger.logUserInteraction("Multiple images selected", "${uris.size} images")
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image picker card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Image",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (selectedImageUri != null) {
                        IconButton(onClick = { showImageInfo = !showImageInfo }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Image Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // Image preview or placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            if (allowMultiple) {
                                multipleImagesPicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                            } else {
                                singleImagePicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedImageUri)
                                .build(),
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
                        // Overlay for multiple images
                        if (selectedImageUris.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+${selectedImageUris.size - 1}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Photo",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (allowMultiple) {
                                    "Tap to select images"
                                } else {
                                    "Tap to select an image"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Supported: ${Constants.SUPPORTED_EXTENSIONS.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Browse button
                    Button(
                        onClick = {
                            if (allowMultiple) {
                                multipleImagesPicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                            } else {
                                singleImagePicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Browse",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Browse")
                    }
                    
                    // Clear button
                    if (selectedImageUri != null) {
                        OutlinedButton(
                            onClick = {
                                selectedImageUri = null
                                selectedImageUris = emptyList()
                                Logger.logUserInteraction("Image selection cleared")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Clear")
                        }
                    }
                }
                
                // Image info
                if (showImageInfo && selectedImageUri != null) {
                    ImageInfoCard(
                        uri = selectedImageUri!!,
                        additionalUris = if (selectedImageUris.size > 1) {
                            selectedImageUris.drop(1)
                        } else null
                    )
                }
            }
        }
        
        // Multiple images preview
        if (selectedImageUris.size > 1) {
            MultipleImagesPreview(
                imageUris = selectedImageUris,
                onRemove = { uri ->
                    selectedImageUris = selectedImageUris.filter { it != uri }
                    if (selectedImageUris.isEmpty()) {
                        selectedImageUri = null
                    } else if (selectedImageUri == uri) {
                        selectedImageUri = selectedImageUris.first()
                    }
                    onMultipleImagesSelected?.invoke(selectedImageUris)
                }
            )
        }
        
        // Quick actions
        QuickActionButtons(
            onCameraClick = {
                // Camera functionality would go here
                Logger.logUserInteraction("Camera button clicked (not implemented)")
            },
            onGalleryClick = {
                if (allowMultiple) {
                    multipleImagesPicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                } else {
                    singleImagePicker.launch(Constants.SUPPORTED_IMAGE_TYPES)
                }
            },
            onHistoryClick = {
                // History functionality would go here
                Logger.logUserInteraction("History button clicked (not implemented)")
            }
        )
    }
}

/**
 * Display information about selected image
 */
@Composable
private fun ImageInfoCard(
    uri: Uri,
    additionalUris: List<Uri>? = null
) {
    val context = LocalContext.current
    var fileInfo by remember(uri) { mutableStateOf<String>("Loading...") }
    
    LaunchedEffect(uri) {
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media.SIZE,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_MODIFIED
            )
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val size = cursor.getLong(0)
                    val name = cursor.getString(1)
                    val modified = cursor.getLong(2)
                    
                    val sizeStr = when {
                        size < 1024 -> "$size B"
                        size < 1024 * 1024 -> "${size / 1024} KB"
                        else -> "${size / (1024 * 1024)} MB"
                    }
                    
                    fileInfo = buildString {
                        appendLine("Name: $name")
                        appendLine("Size: $sizeStr")
                        if (additionalUris != null) {
                            appendLine("Total selected: ${additionalUris.size + 1} images")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            fileInfo = "Unable to load file info"
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = fileInfo,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Preview grid for multiple selected images
 */
@Composable
private fun MultipleImagesPreview(
    imageUris: List<Uri>,
    onRemove: (Uri) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Selected Images (${imageUris.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imageUris.take(4).forEach { uri ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        IconButton(
                            onClick = { onRemove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                
                if (imageUris.size > 4) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${imageUris.size - 4}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick action buttons for common tasks
 */
@Composable
private fun QuickActionButtons(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.Default.CameraAlt,
            label = "Camera",
            onClick = onCameraClick
        )
        
        QuickActionButton(
            icon = Icons.Default.PhotoLibrary,
            label = "Gallery",
            onClick = onGalleryClick
        )
        
        QuickActionButton(
            icon = Icons.Default.History,
            label = "Recent",
            onClick = onHistoryClick
        )
    }
}

/**
 * Individual quick action button
 */
@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}