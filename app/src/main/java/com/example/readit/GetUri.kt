package com.example.readit

import android.net.Uri
import com.canhub.cropper.CropImage

    fun CropImage.ActivityResult?.getUri(): Uri? { return this?.uriContent }
