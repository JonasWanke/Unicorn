package com.jonaswanke.unicorn.api

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.AppEdit
import com.google.api.services.androidpublisher.model.TrackRelease
import com.google.auth.Credentials
import com.google.auth.http.HttpCredentialsAdapter
import com.jonaswanke.unicorn.core.ProgramConfig
import net.swiftzer.semver.SemVer

object Google {
    val HTTP_TRANSPORT: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val JSON_FACTORY: JacksonFactory = JacksonFactory.getDefaultInstance()

    object Play {
        /**
         * @track Default tracks: production, beta, alpha, internal
         */
        fun getReleases(
            credentials: Credentials,
            packageName: String,
            trackId: String = "production"
        ): List<TrackRelease> {
            val publisher =
                AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, HttpCredentialsAdapter(credentials))
                    .setApplicationName("Unicorn/${ProgramConfig.VERSION}")
                    .build()
            val edit = publisher.edits().insert(packageName, AppEdit()).execute()
            val track = publisher.edits().tracks().get(packageName, edit.id, trackId).execute()
            publisher.edits().delete(packageName, edit.id)
            return track.releases
        }
    }
}

val TrackRelease.version: SemVer
    get() = SemVer.parse(name)

val List<TrackRelease>.largestVersionCode: Long?
    get() = flatMap { it.versionCodes }.max()
