package com.autorally

object Config {
    /**
     * Your Firebase Realtime Database URL, e.g. "https://my-project-default-rtdb.firebaseio.com".
     * Leave empty to type it in inside the app instead (Online Multiplayer > SET SERVER).
     */
    const val DEFAULT_FIREBASE_URL = ""

    fun cleanUrl(s: String): String = s.trim().trimEnd('/')
}
