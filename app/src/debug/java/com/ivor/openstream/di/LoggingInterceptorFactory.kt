package com.ivor.openstream.di

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug: real verbose HTTP logging. */
fun createLoggingInterceptor(detailed: Boolean): Interceptor =
    HttpLoggingInterceptor().apply {
        level = if (detailed) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
    }
