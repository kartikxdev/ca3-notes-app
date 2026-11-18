# CA3 - Notes Application (Single Source of Truth Architecture)

An Android Jetpack Compose application built following the **Single Source of Truth (SSOT)** Repository Pattern with Room Database, custom TypeConverter, and reactive Kotlin Coroutine Flows.

---

## 📌 Problem Statement / Requirements
Build a notes application following the **Single Source of Truth (SSOT) repository pattern**.
1. The repository must interact with a Room database entity that includes a creation timestamp (`java.util.Date`).
2. Utilize a custom Room `@TypeConverter` class to serialize the date for SQLite storage.
3. Expose reactive data (`Flow` / `StateFlow`) to the ViewModel and Jetpack Compose UI.
4. Main code file: [`ca3.kt`](app/src/main/java/com/example/aclass/ca3.kt).

---

## 🏗️ Architecture Overview

```mermaid
flowchart TD
    UI["Jetpack Compose UI (NotesScreen)"] -->|Collects StateFlow| VM["ViewModel (NoteViewModel)"]
    VM -->|Flow / CRUD Operations| Repo["Repository (SSOT: NoteRepositoryImpl)"]
    Repo -->|Direct Flow Stream / Writes| Room["Room Database (NoteDao)"]
    Room -->|@TypeConverters| Converter["DateConverter (Date <-> Long)"]
    Room -->|Persists Data| SQLite[("SQLite DB")]
```

### 1. Room Entity with `java.util.Date`
```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Date // java.util.Date creation timestamp
)
```

### 2. Custom `@TypeConverter`
SQLite does not natively support `Date` objects. `DateConverter` serializes `Date` to a `Long` (Unix timestamp in milliseconds) when saving to SQLite, and deserializes it back to `Date` when queried:
```kotlin
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
```

### 3. Reactive Data Access Object (DAO)
Exposes cold Kotlin Coroutines `Flow` that automatically emits fresh lists of notes whenever the database table changes:
```kotlin
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}
```

### 4. Single Source of Truth (SSOT) Repository
The repository encapsulates the Room database as the single authoritative source of truth. The UI layers only observe data originating from Room, ensuring synchronized state across the entire app:
```kotlin
interface NoteRepository {
    val allNotes: Flow<List<NoteEntity>>
    suspend fun insertNote(title: String, content: String)
    suspend fun deleteNote(note: NoteEntity)
}
```

### 5. ViewModel Layer
Converts Room's cold `Flow` into a hot, lifecycle-aware `StateFlow` via `stateIn()` for Jetpack Compose:
```kotlin
class NoteViewModel(private val repository: NoteRepository) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

---

## 🚀 How to Run
1. Open this project in **Android Studio**.
2. Sync the project with Gradle files.
3. Select an Android Emulator or connected physical device.
4. Run the app (`Shift + F10`). The launcher activity is configured to launch `ca3` directly.
