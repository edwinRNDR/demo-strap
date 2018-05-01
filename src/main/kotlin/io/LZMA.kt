package studio.rndr.io

import com.google.gson.Gson
import lzma.sdk.lzma.Decoder
import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream
import lzma.streams.LzmaInputStream
import java.io.InputStreamReader


fun <T> jsonFromLZMA(file: File,  class_:Class<T>):T {


    file.inputStream().use {
        val compressedIn = LzmaInputStream(
                BufferedInputStream(it),
                Decoder())
        return Gson().fromJson(InputStreamReader(compressedIn),class_)
    }
}