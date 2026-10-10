package com.ivor.openstream.di

import okhttp3.Interceptor

/** Release: no-op so the logging library never ships in the release APK. */
fun createLoggingInterceptor(detailed: Boolean): Interceptor =
    Interceptor { chain -> chain.proceed(chain.request()) }
