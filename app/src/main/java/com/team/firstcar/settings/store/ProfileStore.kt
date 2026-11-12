package com.team.firstcar.settings.store

import android.content.Context
import com.team.firstcar.settings.model.ProfileSnapshot
import java.io.File

/** 앱 전용 저장소(filesDir)에 JSON 1개. 서버 동기화(F7)는 로드맵. */
class ProfileStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "profile.json")

    fun load(): ProfileSnapshot? = runCatching { if (file.exists()) ProfileJson.decode(file.readText()) else null }.getOrNull()

    fun save(p: ProfileSnapshot) {
        val tmp = File(file.parentFile, "profile.json.tmp")
        tmp.writeText(ProfileJson.encode(p))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    fun clear() { file.delete() }
}
