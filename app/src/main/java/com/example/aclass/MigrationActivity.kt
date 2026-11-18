package com.example.aclass

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// UPDATED ENTITY (Version 2: Added 'email' column)
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val userId: Int = 0,
    val userName: String,
    val email: String // New field added in Version 2 schema migration
)

// DATA ACCESS OBJECT (DAO)
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // Reactive flow query: Emits updated lists automatically whenever table data changes
    @Query("SELECT * FROM users ORDER BY userName ASC")
    fun getAllUsers(): Flow<List<UserEntity>>
}

// MIGRATION STRATEGY (Version 1 -> Version 2)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Execute raw SQLite command to alter the existing table safely without data loss
        database.execSQL("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT 'unknown@mietjammu.in'")
    }
}

// DATABASE CONFIGURATION (Registering Migration)

@Database(
    entities = [UserEntity::class],
    version = 2, // Incremented version to trigger migration check
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_migration_database"
                )
                    .addMigrations(MIGRATION_1_2) // Attach our custom migration rule here
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// VIEWMODEL LAYER (Exposing Reactive Flow)
class MigrationViewModel(private val dao: UserDao) : ViewModel() {

    // Converts cold DAO Flow into hot StateFlow for Jetpack Compose UI
    val userList: StateFlow<List<UserEntity>> = dao.getAllUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addUser(name: String, email: String) {
        viewModelScope.launch {
            dao.insertUser(
                UserEntity(
                    userName = name.trim(),
                    email = email.trim()
                )
            )
        }
    }
}

class MigrationViewModelFactory(private val dao: UserDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MigrationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MigrationViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// JETPACK COMPOSE UI LAYER
class MigrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val factory = MigrationViewModelFactory(database.userDao())

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: MigrationViewModel = viewModel(factory = factory)
                    MigrationScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MigrationScreen(viewModel: MigrationViewModel) {
    val users by viewModel.userList.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Room Migrations & Reactive Flows",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            viewModel.addUser("Arsalan", "arsalan@mietjammu.in")
        }) {
            Text("Insert New User (Version 2 Schema)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.userId }) { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Name: ${user.userName}", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Email: ${user.email}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
