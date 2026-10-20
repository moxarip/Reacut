package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.core.CapCutDraftEngine
import com.example.core.ReaCutProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectEntity(
  val id: String,
  val name: String,
  val aspectRatioLabel: String,
  val resolutionLabel: String,
  val fps: Int,
  val durationMs: Long,
  val clipCount: Int,
  val subtitleCount: Int,
  val projectJson: String,
  val updatedAt: Long = System.currentTimeMillis(),
)

interface ProjectDao {
  fun observeAllProjects(): Flow<List<ProjectEntity>>
  suspend fun getProjectById(id: String): ProjectEntity?
  suspend fun getProjectCount(): Int
  suspend fun upsertProject(entity: ProjectEntity)
  suspend fun deleteProjectById(id: String)
}

class ReaCutDatabase private constructor(context: Context) :
  SQLiteOpenHelper(context.applicationContext, "reacut_studio.db", null, 1),
  ProjectDao {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _projectsFlow = MutableStateFlow<List<ProjectEntity>>(emptyList())

  init {
    scope.launch {
      refreshFlow()
    }
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS reacut_projects (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        aspectRatioLabel TEXT NOT NULL,
        resolutionLabel TEXT NOT NULL,
        fps INTEGER NOT NULL,
        durationMs INTEGER NOT NULL,
        clipCount INTEGER NOT NULL,
        subtitleCount INTEGER NOT NULL,
        projectJson TEXT NOT NULL,
        updatedAt INTEGER NOT NULL
      )
      """.trimIndent()
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS reacut_projects")
    onCreate(db)
  }

  fun projectDao(): ProjectDao = this

  override fun observeAllProjects(): Flow<List<ProjectEntity>> = _projectsFlow.asStateFlow()

  override suspend fun getProjectById(id: String): ProjectEntity? = withContext(Dispatchers.IO) {
    readableDatabase.rawQuery(
      "SELECT id, name, aspectRatioLabel, resolutionLabel, fps, durationMs, clipCount, subtitleCount, projectJson, updatedAt FROM reacut_projects WHERE id = ? LIMIT 1",
      arrayOf(id),
    ).use { cursor ->
      if (cursor.moveToFirst()) {
        readCursorRow(cursor)
      } else {
        null
      }
    }
  }

  override suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
    readableDatabase.rawQuery("SELECT COUNT(*) FROM reacut_projects", null).use { cursor ->
      if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
  }

  override suspend fun upsertProject(entity: ProjectEntity) = withContext(Dispatchers.IO) {
    val cv = ContentValues().apply {
      put("id", entity.id)
      put("name", entity.name)
      put("aspectRatioLabel", entity.aspectRatioLabel)
      put("resolutionLabel", entity.resolutionLabel)
      put("fps", entity.fps)
      put("durationMs", entity.durationMs)
      put("clipCount", entity.clipCount)
      put("subtitleCount", entity.subtitleCount)
      put("projectJson", entity.projectJson)
      put("updatedAt", entity.updatedAt)
    }
    writableDatabase.insertWithOnConflict(
      "reacut_projects",
      null,
      cv,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
    refreshFlow()
  }

  override suspend fun deleteProjectById(id: String) = withContext(Dispatchers.IO) {
    writableDatabase.delete("reacut_projects", "id = ?", arrayOf(id))
    refreshFlow()
  }

  private suspend fun refreshFlow() = withContext(Dispatchers.IO) {
    val list = mutableListOf<ProjectEntity>()
    readableDatabase.rawQuery(
      "SELECT id, name, aspectRatioLabel, resolutionLabel, fps, durationMs, clipCount, subtitleCount, projectJson, updatedAt FROM reacut_projects ORDER BY updatedAt DESC",
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) {
        list.add(readCursorRow(cursor))
      }
    }
    _projectsFlow.value = list
  }

  private fun readCursorRow(cursor: android.database.Cursor): ProjectEntity {
    return ProjectEntity(
      id = cursor.getString(0),
      name = cursor.getString(1),
      aspectRatioLabel = cursor.getString(2),
      resolutionLabel = cursor.getString(3),
      fps = cursor.getInt(4),
      durationMs = cursor.getLong(5),
      clipCount = cursor.getInt(6),
      subtitleCount = cursor.getInt(7),
      projectJson = cursor.getString(8),
      updatedAt = cursor.getLong(9),
    )
  }

  companion object {
    @Volatile
    private var INSTANCE: ReaCutDatabase? = null

    fun getInstance(context: Context): ReaCutDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = ReaCutDatabase(context.applicationContext)
        INSTANCE = instance
        instance
      }
    }
  }
}

class ProjectRepository(private val projectDao: ProjectDao) {

  val allProjects: Flow<List<ProjectEntity>> = projectDao.observeAllProjects()

  suspend fun ensureSeeded(): ReaCutProject {
    val count = projectDao.getProjectCount()
    val showcase = CapCutDraftEngine.createDefaultShowcaseProject()
    if (count == 0) {
      saveProject(showcase)
      val verticalReel = CapCutDraftEngine.createVerticalReelProject()
      saveProject(verticalReel)
      return showcase
    }
    val existing = projectDao.getProjectById(showcase.config.id)
    return if (existing != null) {
      CapCutDraftEngine.deserializeReaCutProject(existing.projectJson)
    } else {
      showcase
    }
  }

  suspend fun saveProject(project: ReaCutProject) {
    val totalClips = project.tracks.sumOf { it.clips.size }
    val entity = ProjectEntity(
      id = project.config.id,
      name = project.config.name,
      aspectRatioLabel = project.config.aspectRatio.label,
      resolutionLabel = project.config.resolution.label,
      fps = project.config.fps,
      durationMs = project.totalDurationMs,
      clipCount = totalClips,
      subtitleCount = project.subtitles.size,
      projectJson = CapCutDraftEngine.serializeReaCutProject(project),
      updatedAt = System.currentTimeMillis(),
    )
    projectDao.upsertProject(entity)
  }

  suspend fun loadProject(id: String): ReaCutProject? {
    val entity = projectDao.getProjectById(id) ?: return null
    return CapCutDraftEngine.deserializeReaCutProject(entity.projectJson)
  }

  suspend fun deleteProject(id: String) {
    projectDao.deleteProjectById(id)
  }
}
